package com.kobosh.cardinal;

import com.kobosh.cardinal.command.CardinalInitCommand;
import com.kobosh.cardinal.config.ConfigManager;
import com.kobosh.cardinal.integration.QuestsIntegration;
import com.kobosh.cardinal.llm.OpenAiClient;
import com.kobosh.cardinal.scheduler.QuestGenerationScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class Cardinal extends JavaPlugin {

    private ConfigManager configManager;
    private QuestsIntegration questsIntegration;
    private OpenAiClient llmClient;
    private QuestGenerationScheduler generationScheduler;

    @Override
    public void onEnable() {
        // Load config
        configManager = new ConfigManager(this);

        // Validate config before proceeding
        if (!configManager.isConfigValid()) {
            getLogger().severe("Config validation failed! Please check your configuration.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!configManager.isEnabled()) {
            getLogger().info("Cardinal is disabled in config.yml");
            return;
        }

        // Detect Quests plugin
        questsIntegration = new QuestsIntegration(this);
        if (!questsIntegration.detectAndValidate()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize LLM client
        llmClient = new OpenAiClient(
                this,
                configManager,
                configManager.getLlmBaseUrl(),
                configManager.getLlmApiKey(),
                configManager.getLlmModel(),
                configManager.getLlmTemperature(),
                configManager.getLlmMaxTokens(),
                configManager.getLlmTimeoutSeconds());

        // Initialize scheduler
        generationScheduler = new QuestGenerationScheduler(
                this,
                configManager,
                llmClient,
                questsIntegration);

        // Register command and tab completer
        CardinalInitCommand cardinalCommand = new CardinalInitCommand(this, generationScheduler);
        getCommand("cardinal").setExecutor(cardinalCommand);
        getCommand("cardinal").setTabCompleter(cardinalCommand);

        // Start scheduler if story context exists
        String storyContext = configManager.getStoryContext();
        if (storyContext != null && !storyContext.isEmpty()) {
            generationScheduler.start();
            getLogger().info("Resumed quest generation with existing story context");
        } else {
            getLogger().info("No story context found. Use /cardinal init <prompt> to start the system.");
        }

        getLogger().info("Cardinal quest system enabled!");
    }

    @Override
    public void onDisable() {
        if (generationScheduler != null) {
            generationScheduler.stop();
        }
        getLogger().info("Cardinal quest system disabled");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public QuestsIntegration getQuestsIntegration() {
        return questsIntegration;
    }

    public OpenAiClient getLlmClient() {
        return llmClient;
    }

    public QuestGenerationScheduler getGenerationScheduler() {
        return generationScheduler;
    }
}
