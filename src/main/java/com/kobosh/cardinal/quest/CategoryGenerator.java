package com.kobosh.cardinal.quest;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class CategoryGenerator {

    private final Plugin plugin;
    private final File questsFolder;
    private final File categoriesFile;

    public CategoryGenerator(Plugin plugin, File questsFolder) {
        this.plugin = plugin;
        this.questsFolder = questsFolder;
        this.categoriesFile = new File(questsFolder, "categories.yml");
    }

    /**
     * Create or update a category with metadata
     * 
     * @param categoryId the category ID/name
     * @param title      the display title (from LLM metadata)
     * @param item       the GUI item type (from LLM metadata)
     * @param lore       the description/story (from LLM metadata)
     */
    public void createOrUpdateCategory(String categoryId, String title, String item, String lore) {
        try {
            FileConfiguration config;
            if (categoriesFile.exists()) {
                config = YamlConfiguration.loadConfiguration(categoriesFile);
            } else {
                File parent = categoriesFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                config = new YamlConfiguration();
            }

            String safeCategoryId = normalizeCategoryId(categoryId);
            String basePath = "categories." + safeCategoryId;
            String safeTitle = normalizeText(title);
            String safeItem = normalizeText(item).toUpperCase();

            if (!config.contains("categories")) {
                config.createSection("categories");
            }
            if (!config.contains(basePath)) {
                config.createSection(basePath);
            }

            config.set(basePath + ".gui-name", safeTitle);
            config.set(basePath + ".display.name", safeTitle);
            config.set(basePath + ".display.type", safeItem);

            List<String> displayLore = new ArrayList<>();
            List<String> wrappedLines = wrapTextBy10Words(lore);
            if (wrappedLines.isEmpty()) {
                displayLore.add("&7Generated story quests.");
            } else {
                for (String line : wrappedLines) {
                    displayLore.add("&7" + normalizeText(line));
                }
            }
            config.set(basePath + ".display.lore", displayLore);

            config.set(basePath + ".permission-required", false);
            config.set(basePath + ".hidden", false);

            config.save(categoriesFile);
            plugin.getLogger().info("Created/updated category: " + safeCategoryId);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save category: " + e.getMessage());
        }
    }

    /**
     * Create or update a category (legacy method for backward compatibility)
     * 
     * @param categoryId the category ID/name
     * @param title      the display title
     * @param lore       the description/story
     */
    public void createOrUpdateCategory(String categoryId, String title, String lore) {
        createOrUpdateCategory(categoryId, title, "BOOK", lore);
    }

    private String normalizeCategoryId(String input) {
        String normalized = normalizeText(input).toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return normalized.isEmpty() ? "story_arc" : normalized;
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
}
