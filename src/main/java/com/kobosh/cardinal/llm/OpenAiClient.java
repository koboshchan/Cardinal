package com.kobosh.cardinal.llm;

import com.kobosh.cardinal.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OpenAiClient {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private long totalInputTokens;
    private long totalOutputTokens;

    public OpenAiClient(Plugin plugin, ConfigManager configManager, String baseUrl, String apiKey, String model,
            double temperature, int maxTokens, int timeoutSeconds) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.gson = new Gson();
        // Load previously saved token counts
        this.totalInputTokens = configManager.getTotalInputTokens();
        this.totalOutputTokens = configManager.getTotalOutputTokens();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Get total input tokens used
     */
    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    /**
     * Get total output tokens used
     */
    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    /**
     * Get total tokens used
     */
    public long getTotalTokens() {
        return totalInputTokens + totalOutputTokens;
    }

    /**
     * Reset token counters
     */
    public void resetTokenCounters() {
        totalInputTokens = 0;
        totalOutputTokens = 0;
        configManager.setTotalInputTokens(0);
        configManager.setTotalOutputTokens(0);
    }

    /**
     * Track tokens from API response
     */
    private void trackTokens(JsonObject responseJson) {
        try {
            if (responseJson.has("usage")) {
                JsonObject usage = responseJson.getAsJsonObject("usage");
                if (usage.has("prompt_tokens")) {
                    totalInputTokens += usage.get("prompt_tokens").getAsLong();
                }
                if (usage.has("completion_tokens")) {
                    totalOutputTokens += usage.get("completion_tokens").getAsLong();
                }
                // Save updated counts to config
                configManager.setTotalInputTokens(totalInputTokens);
                configManager.setTotalOutputTokens(totalOutputTokens);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to track tokens: " + e.getMessage());
        }
    }

    /**
     * Generate base story metadata from LLM
     * 
     * @param storyContext the initial story prompt
     * @return JSON object with title, item, and lore, or null if error
     */
    public JsonObject generateStoryMetadata(String storyContext) {
        try {
            String userPrompt = "Based on this story concept, provide metadata for a quest series:\n\n" +
                    "Story: " + storyContext + "\n\n" +
                    "Respond ONLY with a JSON object in this exact format:\n" +
                    "{\n" +
                    "  \"title\": \"Name of the quest series (2-5 words)\",\n" +
                    "  \"item\": \"A Minecraft material name representing this series\",\n" +
                    "  \"lore\": \"A 40-50 word description of the quest series\"\n" +
                    "}\n\n" +
                    "Examples of items: BOOK, ENCHANTED_BOOK, GOLDEN_APPLE, DIAMOND, NETHER_STAR, BEACON";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", 300);

            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", "You are a helpful assistant that generates metadata for quest series.");
            messages.add(systemMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userPrompt);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            String jsonBody = gson.toJson(requestBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("LLM API error: " + response.code() + " " + response.message());
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                trackTokens(responseJson);
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices == null || choices.size() == 0) {
                    plugin.getLogger().warning("No choices in LLM response");
                    return null;
                }

                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content")
                        .getAsString();

                return JsonParser.parseString(content).getAsJsonObject();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("LLM API request failed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse LLM metadata response: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate quest batch from LLM
     * 
     * @param storyContext          the initial story prompt
     * @param previousQuestsSummary summary of previously generated quests
     * @param questCount            number of quests to generate
     * @return JSON array of quest objects, or null if error
     */
    public JsonArray generateQuestBatch(String storyContext, String previousQuestsSummary, int questCount) {
        try {
            String userPrompt = buildPrompt(storyContext, previousQuestsSummary, questCount);
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);

            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", SYSTEM_PROMPT);
            messages.add(systemMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userPrompt);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            String jsonBody = gson.toJson(requestBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("LLM API error: " + response.code() + " " + response.message());
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
                trackTokens(responseJson);
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices == null || choices.size() == 0) {
                    plugin.getLogger().warning("No choices in LLM response");
                    return null;
                }

                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content")
                        .getAsString();

                return JsonParser.parseString(content).getAsJsonArray();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("LLM API request failed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse LLM response: " + e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String storyContext, String previousQuestsSummary, int questCount) {
        return "You are a quest generator for a Minecraft server running the Quests plugin. " +
                "Generate " + questCount + " sequential quests based on the following story context.\n\n" +
                "Story Context: " + storyContext + "\n\n" +
                (previousQuestsSummary.isEmpty() ? ""
                : "Previously Generated Quests (treat as already used and DO NOT duplicate):\n"
                    + previousQuestsSummary + "\n\n")
                +
                "Requirements:\n" +
                "- Each quest should advance the story and be completable in ~20 minutes of gameplay\n" +
                "- Format response as a JSON array of quest objects\n" +
                "- Each quest must have: title, description (~50 words), instructions (what player does), " +
                "finish (completion message), itemrewards (array of {name, amount}), moneyrewards (int), " +
                "requirements (array of task objects with type-specific params)\n" +
                "- Supported task types: walking, position, smelting, mobkilling, milking, itemmending, farming, " +
                "crafting, consume, brewing, shearing, blockplace, blockbreak\n" +
                "- Smelting alias: smeltingcertain is accepted and normalized to smelting\n" +
                "- Smelting item semantics: when using smelting.item, specify the cooked/result item (e.g. STEAK), not the raw/input item (e.g. BEEF)\n"
                +
                "- Type-specific required params:\n" +
                "  * walking: distance\n" +
                "  * position: x + y + z + world\n" +
                "  * smelting: amount\n" +
                "  * mobkilling: amount\n" +
                "  * milking: amount\n" +
                "  * itemmending: amount\n" +
                "  * farming: amount\n" +
                "  * crafting: amount + item\n" +
                "  * consume: amount + item\n" +
                "  * brewing: amount\n" +
                "  * shearing: amount\n" +
                "  * blockplace: amount\n" +
                "  * blockbreak: amount\n" +
                "- Use Quests field names for optional params such as block/blocks, mob/mobs, ingredient, mode, worlds, exact-match, reverse-if-broken, reverse-if-placed\n"
                +
                "- Cash Rewards (moneyrewards field):\n" +
                "  * Easy quests (simple tasks, low amounts): 10,000 - 50,000\n" +
                "  * Medium quests (moderate tasks, medium amounts): 100,000 - 500,000\n" +
                "  * Hard quests (complex tasks, high amounts, multiple tasks): 500,000 - 1,500,000\n" +
                "  * Base reward on task difficulty: more difficult tasks = higher rewards\n" +
                "- Each quest unlocks the next; chain them with story progression\n" +
                "- Hard constraint: do not create duplicate quests from Previously Generated Quests\n" +
                "- Avoid near-duplicates too: no reused quest titles, same objective combinations, or same task targets with only tiny number changes\n" +
                "- Make it engaging and narrative-driven\n\n" +
                "JSON Schema (must be followed exactly):\n" +
                QUEST_BATCH_JSON_SCHEMA + "\n\n" +
                "Respond ONLY with valid JSON array, no other text.";
    }

    private static final String SYSTEM_PROMPT = "You are a creative quest designer. Generate quests that are engaging, logical, "
            +
            "and progress a narrative storyline. Always respond with valid JSON only.";

    private static final String QUEST_BATCH_JSON_SCHEMA = """
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "array",
                "minItems": 1,
                "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": [
                        "title",
                        "description",
                        "instructions",
                        "finish",
                        "itemrewards",
                        "moneyrewards",
                        "requirements"
                    ],
                    "properties": {
                        "title": { "type": "string", "minLength": 1 },
                        "description": { "type": "string", "minLength": 1 },
                        "instructions": { "type": "string", "minLength": 1 },
                        "finish": { "type": "string", "minLength": 1 },
                        "itemrewards": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "additionalProperties": false,
                                "required": ["name", "amount"],
                                "properties": {
                                    "name": { "type": "string", "minLength": 1 },
                                    "amount": { "type": "integer", "minimum": 1 }
                                }
                            }
                        },
                        "moneyrewards": { "type": "integer", "minimum": 10000, "maximum": 1500000 },
                        "requirements": {
                            "type": "array",
                            "minItems": 1,
                            "items": {
                                "type": "object",
                                "oneOf": [
                                    {
                                        "required": ["type", "distance"],
                                        "properties": {
                                            "type": { "const": "walking" },
                                            "distance": { "type": "integer", "minimum": 1 },
                                            "mode": { "type": "string" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "x", "y", "z", "world"],
                                        "properties": {
                                            "type": { "const": "position" },
                                            "x": { "type": "integer" },
                                            "y": { "type": "integer" },
                                            "z": { "type": "integer" },
                                            "world": { "type": "string", "minLength": 1 },
                                            "distance-padding": { "type": "integer", "minimum": 0 }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "smelting" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "item": {},
                                            "exact-match": { "type": "boolean" },
                                            "mode": { "type": "string" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "smeltingcertain" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "item": {},
                                            "exact-match": { "type": "boolean" },
                                            "mode": { "type": "string" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "mobkilling" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "mob": {},
                                            "mobs": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "hostile": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "milking" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "mob": {},
                                            "mobs": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "itemmending" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "item": {},
                                            "exact-match": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "farming" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "block": {},
                                            "blocks": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "mode": { "type": "string" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount", "item"],
                                        "properties": {
                                            "type": { "const": "crafting" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "item": {},
                                            "exact-match": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount", "item"],
                                        "properties": {
                                            "type": { "const": "consume" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "item": {},
                                            "exact-match": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "brewing" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "ingredient": {},
                                            "exact-match": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "shearing" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "color": { "type": "string" },
                                            "colors": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "mob": {},
                                            "mobs": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "blockplace" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "block": {},
                                            "blocks": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "reverse-if-broken": { "type": "boolean" },
                                            "allow-negative-progress": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    },
                                    {
                                        "required": ["type", "amount"],
                                        "properties": {
                                            "type": { "const": "blockbreak" },
                                            "amount": { "type": "integer", "minimum": 1 },
                                            "block": {},
                                            "blocks": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            },
                                            "reverse-if-placed": { "type": "boolean" },
                                            "allow-negative-progress": { "type": "boolean" },
                                            "allow-silk-touch": { "type": "boolean" },
                                            "worlds": {
                                                "type": "array",
                                                "items": { "type": "string" }
                                            }
                                        },
                                        "additionalProperties": true
                                    }
                                ]
                            }
                        }
                    }
                }
            }
            """;
}
