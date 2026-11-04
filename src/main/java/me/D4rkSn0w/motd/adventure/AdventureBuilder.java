package me.D4rkSn0w.motd.adventure;

import me.D4rkSn0w.motd.core.Section;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.Sprite;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public final class AdventureBuilder {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static volatile ServerPlatform reporter;
    private static volatile boolean spriteReported;
    private static volatile boolean tagsReported;

    private AdventureBuilder() {
    }

    public static void reporter(ServerPlatform platform) {
        reporter = platform;
    }

    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    public static String legacyToString(String text) {
        return LegacyComponentSerializer.legacySection().serialize(LEGACY.deserialize(text));
    }

    public static Component fromJson(String json) {
        try {
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (Throwable error) {
            return null;
        }
    }

    public static Component build(List<Section> parts) {
        if (parts.isEmpty()) {
            return Component.empty();
        }
        TextComponent.Builder builder = Component.text();
        for (Section part : parts) {
            builder.append(bau(part));
        }
        return builder.build();
    }

    private static Component bau(Section part) {
        if (part.isSprite()) {
            return sprite(part.sprite());
        }
        if (part.isJson()) {
            try {
                return GsonComponentSerializer.gson().deserialize(part.json());
            } catch (Throwable error) {
                if (!tagsReported) {
                    tagsReported = true;
                    warne("Ein formatierter MOTD-Abschnitt konnte nicht gebaut werden, es wird auf "
                            + "einfache Farbcodes ausgewichen. JSON: " + part.json(), error);
                }
                return legacy(part.text());
            }
        }
        return legacy(part.text());
    }

    private static Component sprite(Sprite sprite) {
        try {
            Component made = Component.object(
                    ObjectContents.sprite(Key.key(sprite.atlas()), Key.key(sprite.image())));
            TextColor color = color(sprite.color());
            return color == null ? made : made.color(color);
        } catch (Throwable error) {
            if (!spriteReported) {
                spriteReported = true;
                warne("Sprite '" + sprite.name() + "' konnte nicht gebaut werden, stattdessen "
                        + "wird der Fallback-Text angezeigt. Diese Serverversion kennt den "
                        + "Sprite-Typ vermutlich noch nicht (nötig ist MC 1.21.9 aufwärts). "
                        + "Atlas '" + sprite.atlas() + "', Sprite '" + sprite.image() + "'",
                        error);
            }
            return legacy(sprite.fallback());
        }
    }

    private static TextColor color(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String clean = name.trim();
        if (clean.charAt(0) == '#') {
            return TextColor.fromHexString(clean);
        }
        return NamedTextColor.NAMES.value(clean.toLowerCase(java.util.Locale.ROOT));
    }

    private static void warne(String text, Throwable error) {
        ServerPlatform platform = reporter;
        if (platform != null) {
            platform.warn(text, error);
        }
    }
}
