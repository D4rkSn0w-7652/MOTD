package me.D4rkSn0w.motd.core;

public final class Sprite {

    private final String name;
    private final String atlas;
    private final String image;
    private final String color;
    private final String fallback;

    public Sprite(String name, String atlas, String image, String color, String fallback) {
        this.name = name;
        this.atlas = atlas;
        this.image = image;
        this.color = color;
        this.fallback = fallback;
    }

    public String name() {
        return name;
    }

    public String atlas() {
        return atlas;
    }

    public String image() {
        return image;
    }

    public String color() {
        return color;
    }

    public String fallback() {
        return fallback;
    }

    public String describe() {
        return "Atlas '" + atlas + "', Sprite '" + image + "', Farbe '" + color
                + "', Fallback '" + fallback + "'";
    }

    public String toJson() {
        StringBuilder out = new StringBuilder("{\"atlas\":\"")
                .append(escape(atlas))
                .append("\",\"sprite\":\"")
                .append(escape(image))
                .append('"');
        if (color != null && !color.isEmpty()) {
            out.append(",\"color\":\"").append(escape(color)).append('"');
        }
        return out.append('}').toString();
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
