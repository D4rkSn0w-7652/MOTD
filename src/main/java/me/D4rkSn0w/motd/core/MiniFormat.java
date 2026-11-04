package me.D4rkSn0w.motd.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MiniFormat {

    public static final class Run {

        private final String text;
        private final String color;
        private final boolean bold;
        private final boolean italic;
        private final boolean underlined;
        private final boolean strikethrough;
        private final boolean obfuscated;
        private final String clickUrl;

        Run(String text, Style style) {
            this.text = text;
            this.color = style.color;
            this.bold = style.bold;
            this.italic = style.italic;
            this.underlined = style.underlined;
            this.strikethrough = style.strikethrough;
            this.obfuscated = style.obfuscated;
            this.clickUrl = style.clickUrl;
        }

        public String text() {
            return text;
        }

        public String color() {
            return color;
        }
    }

    static final class Style {

        String color;
        boolean bold;
        boolean italic;
        boolean underlined;
        boolean strikethrough;
        boolean obfuscated;
        String clickUrl;

        Style copy() {
            Style latest = new Style();
            latest.color = color;
            latest.bold = bold;
            latest.italic = italic;
            latest.underlined = underlined;
            latest.strikethrough = strikethrough;
            latest.obfuscated = obfuscated;
            latest.clickUrl = clickUrl;
            return latest;
        }

        void reset() {
            color = null;
            bold = false;
            italic = false;
            underlined = false;
            strikethrough = false;
            obfuscated = false;
            clickUrl = null;
        }
    }

    private static final Map<String, String> COLOR_NAMES = new LinkedHashMap<>();
    private static final Map<Character, String> COLOR_CODES = new LinkedHashMap<>();

    static {
        COLOR_NAMES.put("black", "#000000");
        COLOR_NAMES.put("dark_blue", "#0000AA");
        COLOR_NAMES.put("dark_green", "#00AA00");
        COLOR_NAMES.put("dark_aqua", "#00AAAA");
        COLOR_NAMES.put("dark_red", "#AA0000");
        COLOR_NAMES.put("dark_purple", "#AA00AA");
        COLOR_NAMES.put("gold", "#FFAA00");
        COLOR_NAMES.put("gray", "#AAAAAA");
        COLOR_NAMES.put("grey", "#AAAAAA");
        COLOR_NAMES.put("dark_gray", "#555555");
        COLOR_NAMES.put("dark_grey", "#555555");
        COLOR_NAMES.put("blue", "#5555FF");
        COLOR_NAMES.put("green", "#55FF55");
        COLOR_NAMES.put("aqua", "#55FFFF");
        COLOR_NAMES.put("red", "#FF5555");
        COLOR_NAMES.put("light_purple", "#FF55FF");
        COLOR_NAMES.put("yellow", "#FFFF55");
        COLOR_NAMES.put("white", "#FFFFFF");

        COLOR_CODES.put('0', "#000000");
        COLOR_CODES.put('1', "#0000AA");
        COLOR_CODES.put('2', "#00AA00");
        COLOR_CODES.put('3', "#00AAAA");
        COLOR_CODES.put('4', "#AA0000");
        COLOR_CODES.put('5', "#AA00AA");
        COLOR_CODES.put('6', "#FFAA00");
        COLOR_CODES.put('7', "#AAAAAA");
        COLOR_CODES.put('8', "#555555");
        COLOR_CODES.put('9', "#5555FF");
        COLOR_CODES.put('a', "#55FF55");
        COLOR_CODES.put('b', "#55FFFF");
        COLOR_CODES.put('c', "#FF5555");
        COLOR_CODES.put('d', "#FF55FF");
        COLOR_CODES.put('e', "#FFFF55");
        COLOR_CODES.put('f', "#FFFFFF");
    }

    private MiniFormat() {
    }

    public static boolean hasTags(String text) {
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '<') {
                int ende = text.indexOf('>', i);
                if (ende > i + 1 && isKnown(text.substring(i + 1, ende))) {
                    return true;
                }
            }
            i++;
        }
        return false;
    }

    public static List<Run> split(String text) {
        List<Run> target = new ArrayList<>();
        parse(text, new Style(), target);
        return target;
    }

    private static void parse(String text, Style start, List<Run> target) {
        Style style = start.copy();
        Deque<Style> stack = new ArrayDeque<>();
        StringBuilder buffer = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            char character = text.charAt(i);

            if (character == '&' && i + 1 < text.length()) {
                int consumed = legacy(text, i, style, buffer, target);
                if (consumed > 0) {
                    i += consumed;
                    continue;
                }
            }

            if (character == '<') {
                int ende = text.indexOf('>', i);
                if (ende > i) {
                    String tag = text.substring(i + 1, ende);
                    String lower = tag.toLowerCase(Locale.ROOT);

                    if (lower.equals("gradient") || lower.startsWith("gradient:")
                            || lower.equals("rainbow") || lower.startsWith("rainbow:")) {
                        String name = lower.startsWith("rainbow") ? "rainbow" : "gradient";
                        int close = findClose(text, ende + 1, name);
                        String inner = text.substring(ende + 1, close);
                        write(buffer, style, target);
                        gradient(inner, style, target, name.equals("rainbow") ? null : stops(lower));
                        int afterClose = text.indexOf('>', close);
                        i = afterClose < 0 ? text.length() : afterClose + 1;
                        continue;
                    }

                    if (isKnown(tag)) {
                        write(buffer, style, target);
                        if (lower.startsWith("/")) {
                            if (!stack.isEmpty()) {
                                style = stack.pop();
                            }
                        } else if (lower.equals("reset") || lower.equals("r")) {
                            stack.clear();
                            style.reset();
                        } else if (lower.startsWith("click:")) {
                            stack.push(style.copy());
                            style.clickUrl = clickTarget(tag);
                        } else {
                            stack.push(style.copy());
                            apply(lower, style);
                        }
                        i = ende + 1;
                        continue;
                    }
                }
            }

            buffer.append(character);
            i++;
        }
        write(buffer, style, target);
    }

    private static int legacy(String text, int i, Style style, StringBuilder buffer, List<Run> target) {
        char code = Character.toLowerCase(text.charAt(i + 1));

        if (text.charAt(i + 1) == '#' && i + 7 < text.length()) {
            StringBuilder direct = new StringBuilder("#");
            for (int k = 0; k < 6; k++) {
                char h = text.charAt(i + 2 + k);
                if (!isHex(h)) {
                    return 0;
                }
                direct.append(h);
            }
            write(buffer, style, target);
            style.color = direct.toString().toUpperCase(Locale.ROOT);
            return 8;
        }

        if (code == 'x' && i + 13 < text.length()) {
            StringBuilder hex = new StringBuilder("#");
            for (int k = 0; k < 6; k++) {
                int pos = i + 2 + k * 2;
                if (text.charAt(pos) != '&' || !isHex(text.charAt(pos + 1))) {
                    return 0;
                }
                hex.append(text.charAt(pos + 1));
            }
            write(buffer, style, target);
            style.color = hex.toString().toUpperCase(Locale.ROOT);
            return 14;
        }

        if (COLOR_CODES.containsKey(code)) {
            write(buffer, style, target);
            style.reset();
            style.color = COLOR_CODES.get(code);
            return 2;
        }

        switch (code) {
            case 'l':
                write(buffer, style, target);
                style.bold = true;
                return 2;
            case 'o':
                write(buffer, style, target);
                style.italic = true;
                return 2;
            case 'n':
                write(buffer, style, target);
                style.underlined = true;
                return 2;
            case 'm':
                write(buffer, style, target);
                style.strikethrough = true;
                return 2;
            case 'k':
                write(buffer, style, target);
                style.obfuscated = true;
                return 2;
            case 'r':
                write(buffer, style, target);
                style.reset();
                return 2;
            default:
                return 0;
        }
    }

    private static void gradient(String inner, Style style, List<Run> target, List<String> stops) {
        List<Run> parts = new ArrayList<>();
        parse(inner, style, parts);

        int total = 0;
        for (Run run : parts) {
            total += run.text.length();
        }
        if (total == 0) {
            return;
        }

        int position = 0;
        for (Run run : parts) {
            for (int k = 0; k < run.text.length(); k++) {
                double t = total == 1 ? 0.0 : (double) position / (total - 1);
                Style single = new Style();
                single.color = stops == null ? rainbow(t) : mix(stops, t);
                single.bold = run.bold;
                single.italic = run.italic;
                single.underlined = run.underlined;
                single.strikethrough = run.strikethrough;
                single.obfuscated = run.obfuscated;
                single.clickUrl = run.clickUrl;
                target.add(new Run(String.valueOf(run.text.charAt(k)), single));
                position++;
            }
        }
    }

    private static int findClose(String text, int from, String name) {
        String up = "<" + name;
        String zu = "</" + name;
        int depth = 1;
        int i = from;
        while (i < text.length()) {
            String rest = text.substring(i).toLowerCase(Locale.ROOT);
            if (rest.startsWith(zu)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (rest.startsWith(up)) {
                depth++;
            }
            i++;
        }
        return text.length();
    }

    private static List<String> stops(String tag) {
        List<String> colors = new ArrayList<>();
        int doppel = tag.indexOf(':');
        if (doppel >= 0) {
            for (String part : tag.substring(doppel + 1).split(":")) {
                String color = colorValue(part.trim());
                if (color != null) {
                    colors.add(color);
                }
            }
        }
        if (colors.size() < 2) {
            colors.clear();
            colors.add("#FFFFFF");
            colors.add("#000000");
        }
        return colors;
    }

    private static String mix(List<String> stops, double t) {
        int sections = stops.size() - 1;
        double scaled = t * sections;
        int index = (int) Math.floor(scaled);
        if (index >= sections) {
            index = sections - 1;
        }
        double local = scaled - index;
        int from = Integer.parseInt(stops.get(index).substring(1), 16);
        int to = Integer.parseInt(stops.get(index + 1).substring(1), 16);
        int r = blend((from >> 16) & 0xFF, (to >> 16) & 0xFF, local);
        int g = blend((from >> 8) & 0xFF, (to >> 8) & 0xFF, local);
        int b = blend(from & 0xFF, to & 0xFF, local);
        return hex(r, g, b);
    }

    private static String rainbow(double t) {
        double h = t * 6.0;
        int segment = (int) Math.floor(h) % 6;
        double f = h - Math.floor(h);
        int full = 255;
        int up = (int) Math.round(f * 255);
        int down = 255 - up;
        switch (segment) {
            case 0: return hex(full, up, 0);
            case 1: return hex(down, full, 0);
            case 2: return hex(0, full, up);
            case 3: return hex(0, down, full);
            case 4: return hex(up, 0, full);
            default: return hex(full, 0, down);
        }
    }

    private static int blend(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static String hex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private static void apply(String tag, Style style) {
        String color = colorValue(tag);
        if (color != null) {
            style.color = color;
            return;
        }
        switch (tag) {
            case "b":
            case "bold":
                style.bold = true;
                break;
            case "i":
            case "em":
            case "italic":
                style.italic = true;
                break;
            case "u":
            case "underlined":
                style.underlined = true;
                break;
            case "st":
            case "strikethrough":
                style.strikethrough = true;
                break;
            case "obf":
            case "obfuscated":
                style.obfuscated = true;
                break;
            default:
                break;
        }
    }

    private static String colorValue(String tag) {
        String value = tag;
        if (value.startsWith("color:") || value.startsWith("colour:")) {
            value = value.substring(value.indexOf(':') + 1);
        } else if (value.startsWith("c:")) {
            value = value.substring(2);
        }
        if (value.startsWith("#") && value.length() == 7) {
            for (int i = 1; i < 7; i++) {
                if (!isHex(value.charAt(i))) {
                    return null;
                }
            }
            return value.toUpperCase(Locale.ROOT);
        }
        return COLOR_NAMES.get(value.toLowerCase(Locale.ROOT));
    }

    private static String clickTarget(String tag) {
        int first = tag.indexOf(':');
        if (first < 0) {
            return null;
        }
        String rest = tag.substring(first + 1);
        int second = rest.indexOf(':');
        if (second < 0) {
            return null;
        }
        String value = rest.substring(second + 1).trim();
        if (value.length() > 1
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    private static boolean isKnown(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) {
            lower = lower.substring(1);
        }
        if (lower.isEmpty()) {
            return false;
        }
        if (lower.equals("reset") || lower.equals("r")) {
            return true;
        }
        if (lower.startsWith("gradient") || lower.startsWith("rainbow") || lower.startsWith("click")) {
            return true;
        }
        if (colorValue(lower) != null) {
            return true;
        }
        switch (lower) {
            case "b":
            case "bold":
            case "i":
            case "em":
            case "italic":
            case "u":
            case "underlined":
            case "st":
            case "strikethrough":
            case "obf":
            case "obfuscated":
                return true;
            default:
                return false;
        }
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static void write(StringBuilder buffer, Style style, List<Run> target) {
        if (buffer.length() == 0) {
            return;
        }
        target.add(new Run(buffer.toString(), style));
        buffer.setLength(0);
    }

    public static String toJson(List<Run> runs) {
        if (runs.isEmpty()) {
            return "{\"text\":\"\"}";
        }
        StringBuilder out = new StringBuilder("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < runs.size(); i++) {
            Run run = runs.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"text\":\"").append(escape(run.text)).append('"');
            if (run.color != null) {
                out.append(",\"color\":\"").append(run.color.toLowerCase(Locale.ROOT)).append('"');
            }
            flag(out, "bold", run.bold);
            flag(out, "italic", run.italic);
            flag(out, "underlined", run.underlined);
            flag(out, "strikethrough", run.strikethrough);
            flag(out, "obfuscated", run.obfuscated);
            if (run.clickUrl != null) {
                out.append(",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"")
                        .append(escape(run.clickUrl)).append("\"}");
            }
            out.append('}');
        }
        return out.append("]}").toString();
    }

    public static String toLegacy(List<Run> runs) {
        StringBuilder out = new StringBuilder();
        for (Run lauf : runs) {
            out.append("&r");
            if (lauf.color != null) {
                out.append("&x");
                for (char c : lauf.color.substring(1).toCharArray()) {
                    out.append('&').append(c);
                }
            }
            if (lauf.bold) {
                out.append("&l");
            }
            if (lauf.italic) {
                out.append("&o");
            }
            if (lauf.underlined) {
                out.append("&n");
            }
            if (lauf.strikethrough) {
                out.append("&m");
            }
            if (lauf.obfuscated) {
                out.append("&k");
            }
            out.append(lauf.text);
        }
        return out.toString();
    }

    private static void flag(StringBuilder out, String name, boolean an) {
        if (an) {
            out.append(",\"").append(name).append("\":true");
        }
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }
}
