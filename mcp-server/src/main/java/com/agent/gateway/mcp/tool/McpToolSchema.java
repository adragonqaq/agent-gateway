package com.agent.gateway.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 JSON Schema 构造器，避免各工具手拼 Map。
 */
public final class McpToolSchema {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    private McpToolSchema() {
    }

    public static McpToolSchema object() {
        return new McpToolSchema();
    }

    public static Property string(String name, String description) {
        return new Property(name, "string", description);
    }

    public McpToolSchema required(Property property) {
        required.add(property.name);
        return put(property);
    }

    public McpToolSchema optional(Property property) {
        return put(property);
    }

    public JsonSchema build() {
        return new JsonSchema(
                "object",
                Collections.unmodifiableMap(new LinkedHashMap<>(properties)),
                List.copyOf(required),
                false,
                null,
                null);
    }

    private McpToolSchema put(Property property) {
        if (properties.put(property.name, property.toMap()) != null) {
            throw new IllegalStateException("duplicate MCP arg: " + property.name);
        }
        return this;
    }

    public static final class Property {
        private final String name;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Property(String name, String type, String description) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("arg name required");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("arg description required: " + name);
            }
            this.name = name;
            attributes.put("type", type);
            attributes.put("description", description);
        }

        public Property defaultTo(Object defaultValue) {
            attributes.put("default", defaultValue);
            return this;
        }

        private Map<String, Object> toMap() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }
}
