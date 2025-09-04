package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Schema definition for a tool with input validation.
 *
 * @param <T> The type of input schema this tool accepts
 */
public class ToolSchema<T> {
    private final String name;
    private final String title;
    private final String description;
    private final T inputSchema;
    private final ToolType type;

    private ToolSchema(Builder<T> builder) {
        this.name = builder.name;
        this.title = builder.title;
        this.description = builder.description;
        this.inputSchema = builder.inputSchema;
        this.type = builder.type;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public T getInputSchema() {
        return inputSchema;
    }

    public ToolType getType() {
        return type;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private String name;
        private String title;
        private String description;
        private T inputSchema;
        private ToolType type;

        public Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T> title(String title) {
            this.title = title;
            return this;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<T> inputSchema(T inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder<T> type(ToolType type) {
            this.type = type;
            return this;
        }

        public ToolSchema<T> build() {
            if (name == null || title == null || description == null || type == null) {
                throw new IllegalStateException("name, title, description, and type are required");
            }
            return new ToolSchema<>(this);
        }
    }

    public String describeSchema() {
        if (null == inputSchema) {
            return "{\"type\":\"object\",\"properties\":{}}"; // No input schema defined
        }
        return JSON.toJSONString(toJsonSchemaObject(inputSchema.getClass()), JSONWriter.Feature.PrettyFormat);
    }

    private static Map<String, Object> toJsonSchemaObject(Class<?> clazz) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            Type type = field.getGenericType();
            prop.putAll(parseTypeToSchema(type));
            if (field.isAnnotationPresent(FieldDescription.class)) {
                prop.put("description", field.getAnnotation(FieldDescription.class).value());
            }
            properties.put(field.getName(), prop);
        }
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> parseTypeToSchema(Type type) {
        Map<String, Object> prop = new LinkedHashMap<>();
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz == String.class) {
                prop.put("type", "string");
            } else if (clazz == Integer.class || clazz == int.class) {
                prop.put("type", "integer");
            } else if (clazz == Boolean.class || clazz == boolean.class) {
                prop.put("type", "boolean");
            } else if (clazz == Double.class || clazz == double.class || clazz == Float.class || clazz == float.class) {
                prop.put("type", "number");
            } else if (clazz.isEnum()) {
                prop.put("type", "string");
            } else {
                // 自定义类递归
                prop.putAll(toJsonSchemaObject(clazz));
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> rawClass = (Class<?>) pt.getRawType();
            if (rawClass == List.class || rawClass == ArrayList.class) {
                prop.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                prop.put("items", parseTypeToSchema(elementType));
            } else if (rawClass == Map.class || rawClass == HashMap.class) {
                prop.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                prop.put("additionalProperties", parseTypeToSchema(valueType));
            }
        }
        return prop;
    }

    // for testing
    public static void main(String[] args) throws Exception {
        Object descriptions = toJsonSchemaObject(UserDTO.class);
        System.out.println(JSON.toJSONString(descriptions, JSONWriter.Feature.PrettyFormat));
    }
}
