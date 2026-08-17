package com.CapaPresentacion.Utilidades;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    // --- SERIALIZATION ---

    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof LocalDate) {
            return "\"" + ((LocalDate) obj).format(DATE_FORMATTER) + "\"";
        }
        if (obj instanceof LocalDateTime) {
            return "\"" + obj + "\"";
        }
        if (obj instanceof Enum) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }
        if (obj instanceof Collection) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Object item : (Collection<?>) obj) {
                joiner.add(toJson(item));
            }
            return joiner.toString();
        }
        if (obj instanceof Map) {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                joiner.add("\"" + escapeJson(entry.getKey().toString()) + "\":" + toJson(entry.getValue()));
            }
            return joiner.toString();
        }
        if (obj instanceof Optional) {
            Optional<?> opt = (Optional<?>) obj;
            return opt.isPresent() ? toJson(opt.get()) : "null";
        }

        // Reflection for custom objects and records
        try {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            Class<?> clazz = obj.getClass();
            
            // Check if it's a Record
            if (clazz.isRecord()) {
                RecordComponent[] components = clazz.getRecordComponents();
                for (RecordComponent comp : components) {
                    comp.getAccessor().setAccessible(true);
                    Object val = comp.getAccessor().invoke(obj);
                    joiner.add("\"" + comp.getName() + "\":" + toJson(val));
                }
            } else {
                // Regular class
                Class<?> current = clazz;
                while (current != null && current != Object.class) {
                    for (Field field : current.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                            continue;
                        }
                        field.setAccessible(true);
                        Object val = field.get(obj);
                        joiner.add("\"" + field.getName() + "\":" + toJson(val));
                    }
                    current = current.getSuperclass();
                }
            }
            return joiner.toString();
        } catch (Exception e) {
            return "{\"error\":\"Serialization failed: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    // --- DESERIALIZATION ---

    public static Object parse(String json) {
        if (json == null) return null;
        json = json.trim();
        if (json.isEmpty()) return null;
        return new Parser(json).parse();
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> clazz) {
        Object parsed = parse(json);
        if (parsed instanceof Map) {
            return convertMapToType((Map<String, Object>) parsed, clazz);
        }
        throw new IllegalArgumentException("Expected JSON object for class " + clazz.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        Object parsed = parse(json);
        if (parsed instanceof List) {
            List<T> result = new ArrayList<>();
            for (Object item : (List<?>) parsed) {
                if (item instanceof Map) {
                    result.add(convertMapToType((Map<String, Object>) item, clazz));
                } else {
                    result.add((T) coerce(item, clazz));
                }
            }
            return result;
        }
        throw new IllegalArgumentException("Expected JSON array for list of class " + clazz.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertMapToType(Map<String, Object> map, Class<T> clazz) {
        try {
            if (clazz.isRecord()) {
                RecordComponent[] components = clazz.getRecordComponents();
                Object[] args = new Object[components.length];
                Class<?>[] paramTypes = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    RecordComponent comp = components[i];
                    paramTypes[i] = comp.getType();
                    Object rawVal = map.get(comp.getName());
                    args[i] = coerce(rawVal, comp.getGenericType());
                }
                Constructor<T> canonicalConstructor = clazz.getDeclaredConstructor(paramTypes);
                canonicalConstructor.setAccessible(true);
                return canonicalConstructor.newInstance(args);
            } else {
                Constructor<T> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                T instance = constructor.newInstance();
                Class<?> current = clazz;
                while (current != null && current != Object.class) {
                    for (Field field : current.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                            continue;
                        }
                        if (map.containsKey(field.getName())) {
                            field.setAccessible(true);
                            Object rawVal = map.get(field.getName());
                            field.set(instance, coerce(rawVal, field.getType()));
                        }
                    }
                    current = current.getSuperclass();
                }
                return instance;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Map to " + clazz.getName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(Object val, Class<?> targetType) {
        if (val == null) {
            if (targetType.isPrimitive()) {
                if (targetType == int.class) return 0;
                if (targetType == double.class) return 0.0;
                if (targetType == boolean.class) return false;
                if (targetType == long.class) return 0L;
            }
            return null;
        }
        if (targetType.isAssignableFrom(val.getClass())) {
            return val;
        }
        if (targetType == String.class) {
            return val.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return ((Number) val).intValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((Number) val).doubleValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((Number) val).longValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (val instanceof Boolean) return val;
            return Boolean.parseBoolean(val.toString());
        }
        if (targetType == BigDecimal.class) {
            if (val instanceof Number) {
                return BigDecimal.valueOf(((Number) val).doubleValue());
            }
            return new BigDecimal(val.toString());
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(val.toString(), DATE_FORMATTER);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, val.toString().toUpperCase());
        }
        return val;
    }

    private static Object coerce(Object val, Type targetType) {
        if (targetType instanceof Class<?>) {
            Class<?> targetClass = (Class<?>) targetType;
            if (val instanceof Map && !Map.class.isAssignableFrom(targetClass)) {
                return convertMapToType((Map<String, Object>) val, targetClass);
            }
            return coerce(val, targetClass);
        }

        if (targetType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) targetType;
            Type rawType = parameterizedType.getRawType();
            if (rawType == List.class && val instanceof List<?>) {
                Type elementType = parameterizedType.getActualTypeArguments()[0];
                List<Object> resultado = new ArrayList<>();
                for (Object elemento : (List<?>) val) {
                    resultado.add(coerce(elemento, elementType));
                }
                return resultado;
            }
        }
        return val;
    }

    // --- RECURSIVE DESCENT JSON PARSER ---

    private static class Parser {
        private final String src;
        private int pos = 0;

        public Parser(String src) {
            this.src = src;
        }

        public Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Extra characters at end of JSON input at pos " + pos);
            }
            return value;
        }

        private Object parseValue() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (Character.isDigit(c) || c == '-') {
                return parseNumber();
            } else if (src.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return false;
            } else if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' at pos " + pos);
        }

        private Map<String, Object> parseObject() {
            pos++; // consume '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key in object at pos " + pos);
                }
                String key = parseString();
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' after key in object at pos " + pos);
                }
                pos++; // consume ':'
                skipWhitespace();
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Unterminated object");
                }
                char c = src.charAt(pos);
                if (c == '}') {
                    pos++;
                    break;
                } else if (c == ',') {
                    pos++;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in object at pos " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            pos++; // consume '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("Unterminated array");
                }
                char c = src.charAt(pos);
                if (c == ']') {
                    pos++;
                    break;
                } else if (c == ',') {
                    pos++;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in array at pos " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            pos++; // consume '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                } else if (c == '\\') {
                    pos++;
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("Unfinished escape sequence");
                    }
                    char esc = src.charAt(pos);
                    pos++;
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape sequence");
                            }
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown escape character: " + esc);
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') {
                pos++;
            }
            boolean isDouble = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isDouble = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            if (isDouble) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
