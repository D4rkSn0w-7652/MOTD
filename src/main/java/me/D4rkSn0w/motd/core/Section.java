package me.D4rkSn0w.motd.core;

public final class Section {

    private final String text;
    private final String json;
    private final Sprite sprite;

    private Section(String text, String json, Sprite sprite) {
        this.text = text;
        this.json = json;
        this.sprite = sprite;
    }

    public static Section text(String text) {
        return new Section(text, null, null);
    }

    public static Section sprite(Sprite sprite) {
        return new Section(null, null, sprite);
    }

    public static Section formatted(String json, String legacy) {
        return new Section(legacy, json, null);
    }

    public boolean isSprite() {
        return sprite != null;
    }

    public boolean isJson() {
        return json != null;
    }

    public String text() {
        return text;
    }

    public String json() {
        return json;
    }

    public Sprite sprite() {
        return sprite;
    }
}
