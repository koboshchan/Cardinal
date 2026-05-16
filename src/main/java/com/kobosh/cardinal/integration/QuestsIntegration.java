package com.kobosh.cardinal.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;

public class QuestsIntegration {

    private final Plugin plugin;
    private Plugin questsPlugin;

    public QuestsIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detect and validate Quests plugin
     * 
     * @return true if Quests is loaded and valid
     */
    public boolean detectAndValidate() {
        questsPlugin = Bukkit.getPluginManager().getPlugin("Quests");

        if (questsPlugin == null) {
            plugin.getLogger().severe("Quests plugin not found! Cardinal requires Quests to be installed.");
            return false;
        }

        if (!questsPlugin.isEnabled()) {
            plugin.getLogger().severe("Quests plugin is disabled!");
            return false;
        }

        File questsFolder = questsPlugin.getDataFolder();
        if (!questsFolder.exists()) {
            plugin.getLogger().warning("Quests plugin data folder does not exist, creating it...");
            questsFolder.mkdirs();
        }

        File questsDir = new File(questsFolder, "quests");
        if (!questsDir.exists()) {
            plugin.getLogger().info("Creating quests directory...");
            questsDir.mkdirs();
        }

        plugin.getLogger().info("Quests plugin detected and validated");
        return true;
    }

    /**
     * Get the Quests plugin data folder
     * 
     * @return Quests plugin data folder, or null if not detected
     */
    public File getQuestsFolder() {
        if (questsPlugin == null) {
            return null;
        }
        return questsPlugin.getDataFolder();
    }

    /**
     * Reload quests in the Quests plugin
     */
    public void reloadQuests() {
        if (questsPlugin == null) {
            plugin.getLogger().warning("Cannot reload quests: Quests plugin not detected");
            return;
        }

        Runnable reloadAction = () -> {
            try {
                boolean executed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "quests admin reload");
                if (executed) {
                    plugin.getLogger().info("Triggered Quests reload");
                } else {
                    plugin.getLogger().warning("Failed to trigger Quests reload command");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload quests: " + e.getMessage());
            }
        };

        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass()
                    .getMethod("execute", Plugin.class, Runnable.class)
                    .invoke(globalScheduler, plugin, reloadAction);
        } catch (Throwable ignored) {
            Bukkit.getScheduler().runTask(plugin, reloadAction);
        }
    }

    public boolean isQuestsLoaded() {
        return questsPlugin != null && questsPlugin.isEnabled();
    }
}
