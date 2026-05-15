package com.kobosh.cardinal.command;

import com.kobosh.cardinal.Cardinal;
import com.kobosh.cardinal.scheduler.QuestGenerationScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CardinalInitCommand implements CommandExecutor, TabCompleter {

    private final Cardinal plugin;
    private final QuestGenerationScheduler scheduler;

    public CardinalInitCommand(Cardinal plugin, QuestGenerationScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for admin permission
        if (!(sender instanceof Player)) {
            // Console can always run
        } else if (!sender.hasPermission("cardinal.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /cardinal <init|generate|reload|status>");
            sender.sendMessage("§7  /cardinal init <story_prompt> - Initialize with story");
            sender.sendMessage("§7  /cardinal generate <amount> - Generate quests (requires existing story)");
            sender.sendMessage("§7  /cardinal reload - Reload configuration");
            sender.sendMessage("§7  /cardinal status - Display token usage statistics");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            long inputTokens = plugin.getLlmClient().getTotalInputTokens();
            long outputTokens = plugin.getLlmClient().getTotalOutputTokens();
            long totalTokens = plugin.getLlmClient().getTotalTokens();

            sender.sendMessage("§6=== Cardinal Token Usage Statistics ===");
            sender.sendMessage("§7Input tokens:  §f" + inputTokens);
            sender.sendMessage("§7Output tokens: §f" + outputTokens);
            sender.sendMessage("§7Total tokens:  §f" + totalTokens);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reloadConfig();
            sender.sendMessage("§aCardinal configuration reloaded successfully!");
            return true;
        }

        if (args[0].equalsIgnoreCase("generate")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /cardinal generate <amount>");
                sender.sendMessage("§7Example: /cardinal generate 5");
                return true;
            }

            int questCount;
            try {
                questCount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid number: " + args[1]);
                return true;
            }

            if (questCount < 1 || questCount > 20) {
                sender.sendMessage("§cQuest count must be between 1 and 20");
                return true;
            }

            String storyContext = plugin.getConfigManager().getStoryContext();
            if (storyContext == null || storyContext.isEmpty()) {
                sender.sendMessage("§cNo story context found. Use /cardinal init <story_prompt> first.");
                return true;
            }

            sender.sendMessage("§aGenerating " + questCount + " quests from existing story...");
            String categoryPrefix = plugin.getConfigManager().getCategoryPrefix();
            String categoryName = (categoryPrefix == null || categoryPrefix.isBlank()) ? "story_arc"
                    : categoryPrefix + "_arc";
            scheduler.generateQuestsWithCount(storyContext, categoryName, questCount);
            sender.sendMessage("§aQuest batch generated! Check the Quests menu in-game.");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("init")) {
            sender.sendMessage("§cUsage: /cardinal init <story_prompt>");
            return true;
        }

        // Reconstruct prompt from all args after 'init'
        StringBuilder promptBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1)
                promptBuilder.append(" ");
            promptBuilder.append(args[i]);
        }
        String prompt = promptBuilder.toString();

        if (prompt.length() < 10) {
            sender.sendMessage("§cStory prompt must be at least 10 characters");
            return true;
        }

        sender.sendMessage("§aInitializing Cardinal quest system with your story...");
        sender.sendMessage("§7Prompt: " + prompt);

        // Generate with category from config prefix.
        String categoryPrefix = plugin.getConfigManager().getCategoryPrefix();
        String categoryName = (categoryPrefix == null || categoryPrefix.isBlank()) ? "story_arc"
                : categoryPrefix + "_arc";
        scheduler.initializeAndGenerate(prompt, categoryName);

        sender.sendMessage("§aInitial quest batch generated! Check the Quests menu in-game.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("cardinal.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByPrefix(Arrays.asList("init", "generate", "reload", "status"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            List<String> suggestions = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                suggestions.add(String.valueOf(i));
            }
            return filterByPrefix(suggestions, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> options, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                results.add(option);
            }
        }
        return results;
    }
}
