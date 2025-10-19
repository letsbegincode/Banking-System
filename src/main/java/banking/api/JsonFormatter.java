package banking.api;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

final class JsonFormatter {
    private JsonFormatter() {
    }

    static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        append(builder, value);
        return builder.toString();
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            builder.append('"').append(escape(string)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            appendMap(builder, map);
        } else if (value instanceof Collection<?> collection) {
            appendCollection(builder, collection.iterator());
        } else if (value.getClass().isArray()) {
            appendArray(builder, value);
        } else {
            builder.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static void appendMap(StringBuilder builder, Map<?, ?> map) {
        builder.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
            append(builder, entry.getValue());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append('}');
    }

    private static void appendCollection(StringBuilder builder, Iterator<?> iterator) {
        builder.append('[');
        while (iterator.hasNext()) {
            append(builder, iterator.next());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
        builder.append(']');
    }

    private static void appendArray(StringBuilder builder, Object array) {
        builder.append('[');
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            append(builder, java.lang.reflect.Array.get(array, i));
            if (i + 1 < length) {
                builder.append(',');
            }
        }
        builder.append(']');
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
