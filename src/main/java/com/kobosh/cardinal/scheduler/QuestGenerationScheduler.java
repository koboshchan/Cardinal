package com.kobosh.cardinal.scheduler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kobosh.cardinal.config.ConfigManager;
import com.kobosh.cardinal.integration.QuestsIntegration;
import com.kobosh.cardinal.llm.OpenAiClient;
import com.kobosh.cardinal.quest.CategoryGenerator;
import com.kobosh.cardinal.quest.QuestYamlGenerator;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class QuestGenerationScheduler {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final OpenAiClient llmClient;
    private final QuestsIntegration questsIntegration;
    private final QuestYamlGenerator questGenerator;
    private final CategoryGenerator categoryGenerator;

    private ScheduledTask foliaGenerationTask;
    private org.bukkit.scheduler.BukkitTask bukkitGenerationTask;
    private List<String> generatedQuestIds;
    private String currentCategoryName;
    private long lastGenerationTime;
    private boolean storyMetadataGenerated;
    private String storyTitle;
    private String storyItem;
    private String storyLore;

    public QuestGenerationScheduler(Plugin plugin, ConfigManager configManager, OpenAiClient llmClient,
            QuestsIntegration questsIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.llmClient = llmClient;
        this.questsIntegration = questsIntegration;
        this.generatedQuestIds = new ArrayList<>();
        this.storyMetadataGenerated = false;

        File questsFolder = questsIntegration.getQuestsFolder();
        this.questGenerator = new QuestYamlGenerator(plugin, questsFolder, configManager.getOutputFolder());
        this.categoryGenerator = new CategoryGenerator(plugin, questsFolder);
        this.lastGenerationTime = System.currentTimeMillis();
        restoreGeneratedQuestState();
    }

    /**
     * Start the scheduler
     */
    public void start() {
        stop();

        if (currentCategoryName == null || currentCategoryName.isEmpty()) {
            currentCategoryName = resolveDefaultCategoryName();
        }

        long daysPerGen = configManager.getDaysPerGeneration();
        long ticks = daysPerGen * 20 * 60 * 20; // MC day = 20 min = 24000 ticks

        try {
            foliaGenerationTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    task -> generateNextBatch(),
                    ticks,
                    ticks);
        } catch (UnsupportedOperationException e) {
            // Fallback for non-Folia runtimes.
            bukkitGenerationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::generateNextBatch, ticks, ticks);
        }

        plugin.getLogger().info("Quest generation scheduler started (interval: " + daysPerGen + " MC day(s))");
    }

    /**
     * Stop the scheduler
     */
    public void stop() {
        boolean stopped = false;

        if (foliaGenerationTask != null) {
            foliaGenerationTask.cancel();
            foliaGenerationTask = null;
            stopped = true;
        }

        if (bukkitGenerationTask != null) {
            bukkitGenerationTask.cancel();
            bukkitGenerationTask = null;
            stopped = true;
        }

        if (stopped) {
            plugin.getLogger().info("Quest generation scheduler stopped");
        }
    }

    /**
     * Initialize with story context and generate first batch
     * 
     * @param storyContext the initial story prompt
     * @param categoryName the category name for generated quests
     */
    public void initializeAndGenerate(String storyContext, String categoryName) {
        this.currentCategoryName = (categoryName == null || categoryName.isBlank())
                ? resolveDefaultCategoryName()
                : categoryName;
        configManager.setStoryContext(storyContext);
        generateNextBatch();
    }

    /**
     * Generate a specific number of quests
     * 
     * @param storyContext the story prompt
     * @param categoryName the category name for generated quests
     * @param questCount   number of quests to generate
     */
    public void generateQuestsWithCount(String storyContext, String categoryName, int questCount) {
        this.currentCategoryName = (categoryName == null || categoryName.isBlank())
                ? resolveDefaultCategoryName()
                : categoryName;
        configManager.setStoryContext(storyContext);
        generateQuestBatch(questCount);
    }

    /**
     * Generate next batch of quests
     */
    private void generateNextBatch() {
        generateQuestBatch(3);
    }

    /**
     * Generate a quest batch with a specific count
     */
    private void generateQuestBatch(int questCount) {
        if (!questsIntegration.isQuestsLoaded()) {
            plugin.getLogger().warning("Quests plugin not loaded, skipping generation");
            return;
        }

        int maxQuests = configManager.getMaxTotalQuests();
        if (maxQuests > 0 && generatedQuestIds.size() >= maxQuests) {
            plugin.getLogger().info("Maximum quest limit reached (" + maxQuests + "), stopping generation");
            stop();
            return;
        }

        if (configManager.isVerboseLogging()) {
            plugin.getLogger().info("Generating new quest batch...");
        }

        String storyContext = configManager.getStoryContext();
        if (storyContext == null || storyContext.isEmpty()) {
            plugin.getLogger().warning("No story context set, use /cardinal init first");
            return;
        }

        if (currentCategoryName == null || currentCategoryName.isEmpty()) {
            currentCategoryName = resolveDefaultCategoryName();
        }

        // Generate story metadata on first run
        if (!storyMetadataGenerated) {
            if (configManager.isVerboseLogging()) {
                plugin.getLogger().info("Generating story metadata...");
            }
            JsonObject metadata = llmClient.generateStoryMetadata(storyContext);
            if (metadata != null && metadata.has("title") && metadata.has("item") && metadata.has("lore")) {
                storyTitle = metadata.get("title").getAsString();
                storyItem = metadata.get("item").getAsString();
                storyLore = metadata.get("lore").getAsString();
            } else {
                storyTitle = currentCategoryName;
                storyItem = "BOOK";
                storyLore = storyContext;
            }
            storyMetadataGenerated = true;
            plugin.getLogger().info("Story metadata: " + storyTitle);
        }

        // Ensure Quests categories.yml contains our story category with metadata.
        categoryGenerator.createOrUpdateCategory(
                currentCategoryName,
                storyTitle,
                storyItem,
                storyLore);

        // Generate previous quests summary
        String previousSummary = generatePreviousSummary();

        // Call LLM
        JsonArray questBatch = llmClient.generateQuestBatch(storyContext, previousSummary, questCount);
        if (questBatch == null) {
            plugin.getLogger().warning("Failed to generate quest batch");
            return;
        }

        // Generate quest files
        String lastQuestId = generatedQuestIds.isEmpty() ? null : generatedQuestIds.get(generatedQuestIds.size() - 1);
        List<String> newQuestIds = questGenerator.generateQuests(questBatch, currentCategoryName, lastQuestId);

        if (newQuestIds.isEmpty()) {
            plugin.getLogger().warning("No quests were generated from LLM response");
            return;
        }

        generatedQuestIds.addAll(newQuestIds);

        // Reload quests plugin
        questsIntegration.reloadQuests();
        lastGenerationTime = System.currentTimeMillis();

        if (configManager.isVerboseLogging()) {
            plugin.getLogger().info("Generated " + newQuestIds.size() + " new quests");
            plugin.getLogger().info("Total generated quests: " + generatedQuestIds.size());
        }
    }

    private String generatePreviousSummary() {
        return questGenerator.buildExistingQuestSummary(60);
    }

    public List<String> getGeneratedQuestIds() {
        return new ArrayList<>(generatedQuestIds);
    }

    public long getLastGenerationTime() {
        return lastGenerationTime;
    }

    private String resolveDefaultCategoryName() {
        String prefix = configManager.getCategoryPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "story";
        }
        return prefix + "_arc";
    }

    private void restoreGeneratedQuestState() {
        List<String> existingQuestIds = questGenerator.loadExistingQuestIdsSorted();
        if (existingQuestIds.isEmpty()) {
            return;
        }

        generatedQuestIds.clear();
        generatedQuestIds.addAll(existingQuestIds);
        plugin.getLogger().info("Restored " + generatedQuestIds.size() + " generated quest(s) from disk");
    }
}
