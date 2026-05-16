package com.kobosh.cardinal.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

public class ConfigManager {

    private final Plugin plugin;
    private FileConfiguration config;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void reloadConfig() {
        loadConfig();
    }

    // LLM settings
    public String getLlmBaseUrl() {
        return config.getString("llm.base_url", "https://api.openai.com/v1");
    }

    public String getLlmApiKey() {
        return config.getString("llm.api_key", "");
    }

    public String getLlmModel() {
        return config.getString("llm.model", "gpt-3.5-turbo");
    }

    public double getLlmTemperature() {
        return config.getDouble("llm.temperature", 0.7);
    }

    public int getLlmMaxTokens() {
        return config.getInt("llm.max_tokens", 2048);
    }

    public int getLlmTimeoutSeconds() {
        return config.getInt("llm.timeout_seconds", 30);
    }

    // Generation settings
    public int getDaysPerGeneration() {
        return config.getInt("generation.days_per_generation", 1);
    }

    public int getQuestsPerBatch() {
        return config.getInt("generation.quests_per_batch", 3);
    }

    public int getMaxTotalQuests() {
        return config.getInt("generation.max_total_quests", 50);
    }

    public String getOutputFolder() {
        return config.getString("generation.output_folder", "quests/generated");
    }

    public boolean isVerboseLogging() {
        return config.getBoolean("generation.verbose_logging", true);
    }

    // Story settings
    public String getStoryContext() {
        return config.getString("story.context", "");
    }

    public void setStoryContext(String context) {
        config.set("story.context", context);
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save story context to config: " + e.getMessage());
        }
    }

    public String getCategoryPrefix() {
        return config.getString("story.category_prefix", "story");
    }

    // Token tracking settings
    public long getTotalInputTokens() {
        return config.getLong("tokens.total_input", 0);
    }

    public void setTotalInputTokens(long tokens) {
        config.set("tokens.total_input", tokens);
        saveConfig();
    }

    public long getTotalOutputTokens() {
        return config.getLong("tokens.total_output", 0);
    }

    public void setTotalOutputTokens(long tokens) {
        config.set("tokens.total_output", tokens);
        saveConfig();
    }

    public boolean shouldCalculateSpendings() {
        return config.getBoolean("tokens.calculate_spendings", false);
    }

    public double getInputPricePerMillionTokens() {
        return config.getDouble("tokens.price_per_million_input", 0.0);
    }

    public double getOutputPricePerMillionTokens() {
        return config.getDouble("tokens.price_per_million_output", 0.0);
    }

    private void saveConfig() {
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    // Validation
    public boolean isConfigValid() {
        String apiKey = getLlmApiKey();
        String baseUrl = getLlmBaseUrl();
        int daysPerGen = getDaysPerGeneration();
        int questsPerBatch = getQuestsPerBatch();
        double inputPricePerMillion = getInputPricePerMillionTokens();
        double outputPricePerMillion = getOutputPricePerMillionTokens();

        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            plugin.getLogger().warning("LLM API key not configured!");
            return false;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            plugin.getLogger().warning("LLM base URL not configured!");
            return false;
        }
        if (daysPerGen <= 0) {
            plugin.getLogger().warning("days_per_generation must be > 0");
            return false;
        }
        if (questsPerBatch <= 0) {
            plugin.getLogger().warning("quests_per_batch must be > 0");
            return false;
        }
        if (inputPricePerMillion < 0 || outputPricePerMillion < 0) {
            plugin.getLogger().warning("Token prices per million must be >= 0");
            return false;
        }
        return true;
    }
}
