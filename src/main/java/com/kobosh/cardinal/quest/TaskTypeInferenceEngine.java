package com.kobosh.cardinal.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class TaskTypeInferenceEngine {

    private final Plugin plugin;
    private final Map<String, TaskTypeValidator> validators;

    public TaskTypeInferenceEngine(Plugin plugin) {
        this.plugin = plugin;
        this.validators = new HashMap<>();
        registerValidators();
    }

    private void registerValidators() {
        validators.put("mobkilling", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("blockbreak", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("blockplace", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("walking", (obj) -> {
            if (!obj.has("distance"))
                return "Missing 'distance'";
            return null;
        });
        validators.put("smelting", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("milking", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("itemmending", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("farming", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
        validators.put("crafting", (obj) -> {
            if (!obj.has("amount") || !obj.has("item"))
                return "Missing 'amount' or 'item'";
            return null;
        });
        validators.put("consume", (obj) -> {
            if (!obj.has("amount") || !obj.has("item"))
                return "Missing 'amount' or 'item'";
            return null;
        });
        validators.put("brewing", (obj) -> {
            if (!obj.has("amount"))
                return "Missing 'amount'";
            return null;
        });
    }

    /**
     * Validate and convert requirements array to list of task configs
     * 
     * @param requirements JSON array of requirement objects with explicit 'type'
     *                     field
     * @return List of validated requirement configs, or empty list with error
     *         logging
     */
    public List<RequirementConfig> validateAndConvert(JsonArray requirements) {
        List<RequirementConfig> result = new ArrayList<>();

        if (requirements == null) {
            return result;
        }

        for (int i = 0; i < requirements.size(); i++) {
            try {
                JsonObject req = requirements.get(i).getAsJsonObject();
                String type = req.has("type") ? req.get("type").getAsString() : null;

                if (type == null || type.isEmpty()) {
                    plugin.getLogger().warning("Requirement " + i + " missing 'type' field");
                    continue;
                }

                TaskTypeValidator validator = validators.get(type);
                if (validator == null) {
                    plugin.getLogger().warning("Unknown task type: " + type);
                    continue;
                }

                String error = validator.validate(req);
                if (error != null) {
                    plugin.getLogger().warning("Requirement " + i + " validation failed: " + error);
                    continue;
                }

                result.add(new RequirementConfig(type, req));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse requirement " + i + ": " + e.getMessage());
            }
        }

        return result;
    }

    public static class RequirementConfig {
        public final String type;
        public final JsonObject config;

        public RequirementConfig(String type, JsonObject config) {
            this.type = type;
            this.config = config;
        }
    }

    @FunctionalInterface
    interface TaskTypeValidator {
        String validate(JsonObject config); // returns null if valid, error message if invalid
    }
}
