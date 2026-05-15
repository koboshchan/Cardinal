package com.kobosh.cardinal.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

public class QuestYamlGenerator {

    private final Plugin plugin;
    private final TaskTypeInferenceEngine inferenceEngine;
    private final File questsOutputFolder;

    public QuestYamlGenerator(Plugin plugin, File questsFolder, String outputSubfolder) {
        this.plugin = plugin;
        this.inferenceEngine = new TaskTypeInferenceEngine(plugin);
        this.questsOutputFolder = new File(questsFolder, outputSubfolder);
        if (!this.questsOutputFolder.exists()) {
            this.questsOutputFolder.mkdirs();
        }
    }

    /**
     * Generate quest YAML files from LLM response
     * 
     * @param questsJson      JSON array from LLM
     * @param categoryName    category name for all quests
     * @param previousQuestId the quest ID to chain from (null for first batch)
     * @return list of generated quest IDs on success, empty list on failure
     */
    public List<String> generateQuests(JsonArray questsJson, String categoryName, String previousQuestId) {
        List<String> generatedIds = new ArrayList<>();

        if (questsJson == null || questsJson.size() == 0) {
            plugin.getLogger().warning("No quests to generate");
            return generatedIds;
        }

        String prevId = previousQuestId;
        int sortOrder = countExistingQuestsInCategory(categoryName) + 1;
        for (int i = 0; i < questsJson.size(); i++) {
            try {
                JsonObject questObj = questsJson.get(i).getAsJsonObject();
                String questId = generateQuest(questObj, categoryName, prevId, sortOrder);
                if (questId != null) {
                    generatedIds.add(questId);
                    prevId = questId;
                    sortOrder++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to generate quest " + i + ": " + e.getMessage());
            }
        }

        return generatedIds;
    }

    /**
     * Count existing quests in a category by checking files in the output folder
     */
    private int countExistingQuestsInCategory(String categoryName) {
        String safeCategoryName = normalizeText(categoryName == null ? "story_arc" : categoryName);
        int count = 0;
        if (questsOutputFolder.exists()) {
            File[] files = questsOutputFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".yml")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Generate a task-specific description for progress placeholders
     * Examples: "20 wheat gathered", "3 cows milked", "10 blocks broken"
     */
    private String generateTaskDescription(String taskType, JsonObject config) {
        String amount = config.has("amount") ? config.get("amount").getAsString() : "?";

        switch (taskType.toLowerCase(Locale.ROOT)) {
            case "farming":
                String farmTarget = extractSpecificTargets(config, "block", "blocks");
                if (farmTarget != null) {
                    return amount + " " + farmTarget + " gathered";
                }
                return amount + " crops gathered";
            case "milking":
                String milkTarget = extractSpecificTargets(config, "mob", "mobs");
                if (milkTarget != null) {
                    return amount + " " + milkTarget + " milked";
                }
                return amount + " mobs milked";
            case "mobkilling":
            case "mobkill":
                String killTarget = extractSpecificTargets(config, "mob", "mobs");
                if (killTarget != null) {
                    return amount + " " + killTarget + " defeated";
                }
                return amount + " hostile mobs defeated";
            case "blockbreak":
                String breakTarget = extractSpecificTargets(config, "block", "blocks");
                if (breakTarget != null) {
                    return amount + " " + breakTarget + " broken";
                }
                return amount + " blocks broken";
            case "blockplace":
                String placeTarget = extractSpecificTargets(config, "block", "blocks");
                if (placeTarget != null) {
                    return amount + " " + placeTarget + " placed";
                }
                return amount + " blocks placed";
            case "crafting":
                String craftTarget = extractSpecificTargets(config, "item", "items");
                if (craftTarget != null) {
                    return amount + " " + craftTarget + " crafted";
                }
                return amount + " items crafted";
            case "smelting":
                String smeltTarget = extractSpecificTargets(config, "item", "items");
                if (smeltTarget != null) {
                    return amount + " " + smeltTarget + " smelted";
                }
                return amount + " items smelted";
            case "consume":
                String consumeTarget = extractSpecificTargets(config, "item", "items");
                if (consumeTarget != null) {
                    return amount + " " + consumeTarget + " consumed";
                }
                return amount + " items consumed";
            case "brewing":
                String ingredientTarget = extractSpecificTargets(config, "ingredient", "ingredients");
                if (ingredientTarget != null) {
                    return amount + " potions brewed with " + ingredientTarget;
                }
                return amount + " potions brewed";
            case "itemmending":
                String mendTarget = extractSpecificTargets(config, "item", "items");
                if (mendTarget != null) {
                    return amount + " " + mendTarget + " mended";
                }
                return amount + " items mended";
            case "walking":
                String distance = config.has("distance") ? config.get("distance").getAsString() : "?";
                return distance + " blocks walked";
            default:
                return "Task in progress";
        }
    }

    /**
     * Extract readable name from resource names like WHEAT, IRON_INGOT, etc.
     */
    private String extractResourceName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            return "item";
        }
        // Convert IRON_INGOT -> Iron Ingot
        return Arrays.stream(resourceName.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String extractSpecificTargets(JsonObject config, String singularKey, String pluralKey) {
        List<String> targets = new ArrayList<>();

        if (config.has(singularKey) && config.get(singularKey).isJsonPrimitive()) {
            String value = config.get(singularKey).getAsString();
            if (!value.isBlank()) {
                targets.add(extractResourceName(value).toLowerCase(Locale.ROOT));
            }
        }

        if (config.has(pluralKey) && config.get(pluralKey).isJsonArray()) {
            JsonArray values = config.getAsJsonArray(pluralKey);
            for (JsonElement value : values) {
                if (value != null && value.isJsonPrimitive()) {
                    String raw = value.getAsString();
                    if (!raw.isBlank()) {
                        targets.add(extractResourceName(raw).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        // Preserve order but remove duplicates to keep placeholder text concise.
        LinkedHashSet<String> unique = new LinkedHashSet<>(targets);
        String joined = String.join(", ", unique);
        return "\"" + joined + "\"";
    }

    /**
     * Build a compact summary of existing generated quests so the LLM can avoid
     * duplicate quests across restarts and manual generations.
     */
    public String buildExistingQuestSummary(int maxEntries) {
        if (maxEntries <= 0) {
            return "";
        }

        List<QuestSummary> summaries = collectQuestSummaries();

        if (summaries.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(maxEntries, summaries.size());
        for (int i = 0; i < limit; i++) {
            QuestSummary summary = summaries.get(i);
            builder.append("- ")
                    .append(summary.questId)
                    .append(" | ")
                    .append(summary.title)
                    .append(" | ")
                    .append(summary.taskSummary)
                    .append("\n");
        }

        return builder.toString();
    }

    /**
     * Restore existing quest IDs from generated quest files in sort order.
     */
    public List<String> loadExistingQuestIdsSorted() {
        List<QuestSummary> summaries = collectQuestSummaries();
        List<String> ids = new ArrayList<>();
        for (QuestSummary summary : summaries) {
            ids.add(summary.questId);
        }
        return ids;
    }

    private List<QuestSummary> collectQuestSummaries() {
        if (!questsOutputFolder.exists()) {
            return new ArrayList<>();
        }

        File[] files = questsOutputFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }

        List<QuestSummary> summaries = new ArrayList<>();
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String questId = file.getName().substring(0, file.getName().length() - 4);
                int sortOrder = yaml.getInt("options.sort-order", Integer.MAX_VALUE);
                String title = normalizeText(yaml.getString("display.name", questId));
                String taskSummary = summarizeTasks(yaml.getConfigurationSection("tasks"));
                summaries.add(new QuestSummary(sortOrder, questId, title, taskSummary));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to summarize quest file '" + file.getName() + "': " + e.getMessage());
            }
        }

        summaries.sort(Comparator
                .comparingInt((QuestSummary s) -> s.sortOrder)
                .thenComparing(s -> s.questId));
        return summaries;
    }

    private String summarizeTasks(ConfigurationSection tasksSection) {
        if (tasksSection == null) {
            return "no tasks";
        }

        List<String> summaries = new ArrayList<>();
        for (String taskId : tasksSection.getKeys(false)) {
            ConfigurationSection taskSection = tasksSection.getConfigurationSection(taskId);
            if (taskSection == null) {
                continue;
            }

            String type = normalizeText(taskSection.getString("type", "task")).toLowerCase(Locale.ROOT);
            String amount = taskSection.contains("amount")
                    ? String.valueOf(taskSection.getInt("amount"))
                    : (taskSection.contains("distance") ? String.valueOf(taskSection.getInt("distance")) : "?");

            String target = extractTaskTarget(taskSection);
            if (target == null || target.isBlank()) {
                summaries.add(type + " x" + amount);
            } else {
                summaries.add(type + "(" + target + ") x" + amount);
            }
        }

        if (summaries.isEmpty()) {
            return "no tasks";
        }
        return String.join("; ", summaries);
    }

    private String extractTaskTarget(ConfigurationSection taskSection) {
        List<String> keysToCheck = Arrays.asList("item", "ingredient", "block", "mob");
        for (String key : keysToCheck) {
            Object value = taskSection.get(key);
            if (value instanceof String) {
                String normalized = ((String) value).trim();
                if (!normalized.isBlank()) {
                    return normalized;
                }
            }
        }

        List<String> listKeysToCheck = Arrays.asList("items", "ingredients", "blocks", "mobs");
        for (String key : listKeysToCheck) {
            List<String> values = taskSection.getStringList(key);
            if (!values.isEmpty()) {
                return String.join(",", values);
            }
        }

        return null;
    }

    private static class QuestSummary {
        private final int sortOrder;
        private final String questId;
        private final String title;
        private final String taskSummary;

        private QuestSummary(int sortOrder, String questId, String title, String taskSummary) {
            this.sortOrder = sortOrder;
            this.questId = questId;
            this.title = title;
            this.taskSummary = taskSummary;
        }
    }

    private String generateQuest(JsonObject questObj, String categoryName, String previousQuestId, int sortOrder)
            throws IOException {
        String title = normalizeText(questObj.has("title") ? questObj.get("title").getAsString() : "Quest");
        String description = normalizeText(
                questObj.has("description") ? questObj.get("description").getAsString() : "");
        String instructions = normalizeText(
                questObj.has("instructions") ? questObj.get("instructions").getAsString() : "");
        String finish = normalizeText(questObj.has("finish") ? questObj.get("finish").getAsString() : "");
        String safeCategoryName = normalizeText(categoryName == null ? "story_arc" : categoryName);
        int moneyRewards = questObj.has("moneyrewards") ? questObj.get("moneyrewards").getAsInt() : 0;

        // Generate quest ID from title (must be strictly alphanumeric for Quests).
        String questId = generateQuestId(title);

        // Validate and convert requirements
        JsonArray requirementsArray = questObj.has("requirements")
                ? questObj.getAsJsonArray("requirements")
                : new JsonArray();
        List<TaskTypeInferenceEngine.RequirementConfig> requirements = inferenceEngine
                .validateAndConvert(requirementsArray);

        if (requirements.isEmpty()) {
            plugin.getLogger().warning("Quest '" + title + "' has no valid task types");
            return null;
        }

        // Build YAML
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Generated quest: ").append(title).append("\n");
        yaml.append("# Part of category: ").append(safeCategoryName).append("\n\n");

        // Tasks
        yaml.append("tasks:\n");
        int taskIndex = 0;
        List<String> taskIds = new ArrayList<>();
        Map<String, String> taskDescriptions = new HashMap<>();
        for (TaskTypeInferenceEngine.RequirementConfig req : requirements) {
            String taskId = "task" + taskIndex;
            taskIds.add(taskId);
            yaml.append("  ").append(taskId).append(":\n");
            yaml.append("    type: \"").append(escapeYamlString(req.type)).append("\"\n");

            // Add all config fields
            for (String key : req.config.keySet()) {
                if (key.equals("type"))
                    continue;
                JsonElement normalizedValue = normalizeTaskFieldValue(key, req.config.get(key));
                appendYamlField(yaml, 4, key, normalizedValue);
            }

            // Generate task description for progress placeholder
            taskDescriptions.put(taskId, generateTaskDescription(req.type, req.config));
            taskIndex++;
        }

        // Display
        yaml.append("\ndisplay:\n");
        yaml.append("  name: \"").append(escapeYamlString(title)).append("\"\n");
        yaml.append("  lore-normal:\n");
        List<String> descLines = wrapTextBy10Words(description);
        if (descLines.isEmpty()) {
            yaml.append("    - \"&7Complete the objectives to advance the story.\"\n");
        } else {
            for (String line : descLines) {
                yaml.append("    - \"&7").append(escapeYamlString(line)).append("\"\n");
            }
        }
        yaml.append("  lore-started:\n");
        yaml.append("    - \"\"\n");
        yaml.append("    - \"&7Status: &fIn Progress\"\n");
        for (String taskId : taskIds) {
            String progressDesc = taskDescriptions.getOrDefault(taskId, "Task in progress");
            List<String> progressLines = wrapTextBy10Words(progressDesc);
            if (progressLines.isEmpty()) {
                yaml.append("    - \"&7 - &f{").append(taskId).append(":progress}\"\n");
            } else {
                yaml.append("    - \"&7 - &f{").append(taskId).append(":progress} ")
                        .append(escapeYamlString(progressLines.get(0))).append("\"\n");
                for (int i = 1; i < progressLines.size(); i++) {
                    yaml.append("    - \"&7   ").append(escapeYamlString(progressLines.get(i))).append("\"\n");
                }
            }
        }
        yaml.append("  type: \"PAPER\"\n");

        // Rewards
        yaml.append("\nrewards:\n");
        if (questObj.has("itemrewards")) {
            JsonArray itemRewards = questObj.getAsJsonArray("itemrewards");
            for (int j = 0; j < itemRewards.size(); j++) {
                JsonObject reward = itemRewards.get(j).getAsJsonObject();
                String itemName = normalizeText(reward.has("name") ? reward.get("name").getAsString() : "diamond");
                int amount = reward.has("amount") ? reward.get("amount").getAsInt() : 1;
                yaml.append("  - \"give {player} ").append(escapeYamlString(itemName)).append(" ").append(amount)
                        .append("\"\n");
            }
        }
        if (moneyRewards > 0) {
            yaml.append("\nvaultreward: ").append(moneyRewards).append("\n");
        }

        // Lifecycle command hooks
        yaml.append("\nstartcommands: []\n");
        yaml.append("cancelcommands: []\n");
        yaml.append("expirycommands: []\n");

        // Strings
        yaml.append("\nstartstring:\n");
        yaml.append("  - \"&8[&9Quest&8] &f").append(escapeYamlString(title)).append(" &7started\"\n");
        yaml.append("  - \"&7").append(escapeYamlString(instructions)).append("\"\n");

        yaml.append("\nrewardstring:\n");
        yaml.append("  - \"&8[&2Quest Complete&8] &f").append(escapeYamlString(title)).append("\"\n");
        yaml.append("  - \"&7").append(escapeYamlString(finish)).append("\"\n");

        yaml.append("\ncancelstring:\n");
        yaml.append("  - \"&8[&cQuest Cancelled&8] &f").append(escapeYamlString(title)).append("\"\n");

        yaml.append("\nexpirystring:\n");
        yaml.append("  - \"&8[&cQuest Expired&8] &f").append(escapeYamlString(title)).append("\"\n");

        yaml.append("\nplaceholders:\n");
        yaml.append("  description: \"").append(escapeYamlString(description)).append("\"\n");
        if (!taskIds.isEmpty()) {
            yaml.append("  progress: \"{").append(taskIds.get(0)).append(":progress}\"\n");
        }

        yaml.append("\nprogress-placeholders:\n");
        for (String taskId : taskIds) {
            String progressDesc = taskDescriptions.getOrDefault(taskId, "Task in progress");
            yaml.append("  ").append(taskId).append(": \"&f{").append(taskId).append(":progress} ")
                    .append(escapeYamlString(progressDesc)).append("\"\n");
        }
        yaml.append("  '*': \"&fQuest in progress\"\n");

        // Options
        yaml.append("\noptions:\n");
        yaml.append("  category: \"").append(escapeYamlString(safeCategoryName)).append("\"\n");
        yaml.append("  sort-order: ").append(sortOrder).append("\n");
        if (previousQuestId != null) {
            yaml.append("  requires:\n");
            yaml.append("    - \"").append(escapeYamlString(previousQuestId)).append("\"\n");
        }
        yaml.append("  permission-required: false\n");
        yaml.append("  repeatable: false\n");
        yaml.append("  cancellable: true\n");
        yaml.append("  counts-towards-limit: true\n");
        yaml.append("  counts-towards-completed: true\n");
        yaml.append("  hidden: false\n");
        yaml.append("  autostart: false\n");

        // Write file
        File questFile = new File(questsOutputFolder, questId + ".yml");
        try (FileWriter writer = new FileWriter(questFile)) {
            writer.write(yaml.toString());
        }

        plugin.getLogger().info("Generated quest: " + questId);
        return questId;
    }

    private String generateQuestId(String title) {
        String base = normalizeText(title).toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "quest";
        }

        String candidate = base;
        int suffix = 2;
        while (new File(questsOutputFolder, candidate + ".yml").exists()) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private JsonElement normalizeTaskFieldValue(String key, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return value;
        }

        String normalizedKey = normalizeYamlKey(key).toLowerCase(Locale.ROOT);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String raw = value.getAsString();
            if (shouldUppercaseTokenField(normalizedKey)) {
                return new JsonPrimitive(toUpperSnakeToken(raw));
            }
            return new JsonPrimitive(normalizeText(raw));
        }

        if (value.isJsonArray() && shouldUppercaseTokenField(normalizedKey)) {
            JsonArray src = value.getAsJsonArray();
            JsonArray out = new JsonArray();
            for (JsonElement item : src) {
                if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    out.add(toUpperSnakeToken(item.getAsString()));
                } else {
                    out.add(item);
                }
            }
            return out;
        }

        return value;
    }

    private boolean shouldUppercaseTokenField(String normalizedKey) {
        return normalizedKey.equals("item")
                || normalizedKey.equals("ingredient")
                || normalizedKey.equals("block")
                || normalizedKey.equals("blocks")
                || normalizedKey.equals("mob")
                || normalizedKey.equals("mobs")
                || normalizedKey.equals("spawn_reason")
                || normalizedKey.equals("spawn_reasons");
    }

    private String toUpperSnakeToken(String input) {
        return normalizeText(input)
                .replaceAll("[\\s-]+", "_")
                .replaceAll("[^A-Za-z0-9_]", "")
                .toUpperCase(Locale.ROOT);
    }

    private void appendYamlField(StringBuilder yaml, int indent, String key, JsonElement value) {
        String pad = " ".repeat(indent);
        String safeKey = normalizeYamlKey(key);

        if (value == null || value.isJsonNull()) {
            yaml.append(pad).append(safeKey).append(": null\n");
            return;
        }

        if (value.isJsonPrimitive()) {
            yaml.append(pad).append(safeKey).append(": ").append(formatPrimitive(value.getAsJsonPrimitive()))
                    .append("\n");
            return;
        }

        if (value.isJsonArray()) {
            yaml.append(pad).append(safeKey).append(":\n");
            JsonArray array = value.getAsJsonArray();
            for (JsonElement item : array) {
                if (item == null || item.isJsonNull()) {
                    yaml.append(pad).append("  - null\n");
                } else if (item.isJsonPrimitive()) {
                    yaml.append(pad).append("  - ").append(formatPrimitive(item.getAsJsonPrimitive())).append("\n");
                } else if (item.isJsonObject()) {
                    yaml.append(pad).append("  -\n");
                    JsonObject object = item.getAsJsonObject();
                    for (String childKey : object.keySet()) {
                        appendYamlField(yaml, indent + 4, childKey, object.get(childKey));
                    }
                }
            }
            return;
        }

        if (value.isJsonObject()) {
            yaml.append(pad).append(safeKey).append(":\n");
            JsonObject object = value.getAsJsonObject();
            for (String childKey : object.keySet()) {
                appendYamlField(yaml, indent + 2, childKey, object.get(childKey));
            }
        }
    }

    private String formatPrimitive(JsonPrimitive primitive) {
        if (primitive.isBoolean() || primitive.isNumber()) {
            return primitive.toString();
        }
        return "\"" + escapeYamlString(primitive.getAsString()) + "\"";
    }

    private String normalizeYamlKey(String key) {
        String normalized = normalizeText(key);
        return normalized.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('\u00A0', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return normalized.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }

    private String escapeYamlString(String input) {
        return normalizeText(input)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * Wrap text to a new line every 10 words
     * 
     * @param text the text to wrap
     * @return list of wrapped lines with max 10 words each
     */
    private List<String> wrapTextBy10Words(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (wordCount == 10) {
                lines.add(currentLine.toString().trim());
                currentLine = new StringBuilder();
                wordCount = 0;
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
            wordCount++;
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }

        return lines;
    }

    /**
     * Format category name for display (convert underscores to spaces and title
     * case)
     * Example: "story_arc" -> "Story Arc"
     */
    public static String formatCategoryName(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return "Quest Series";
        }
        return Arrays.stream(categoryId.split("_"))
                .map(word -> word.isEmpty() ? "" : word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
