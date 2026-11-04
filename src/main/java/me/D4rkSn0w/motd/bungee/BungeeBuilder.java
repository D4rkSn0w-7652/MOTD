package me.D4rkSn0w.motd.bungee;

import me.D4rkSn0w.motd.core.Section;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.Sprite;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BungeeBuilder {

    private static volatile ServerPlatform reporter;
    private static volatile boolean spriteReported;
    private static volatile boolean tagsReported;

    private BungeeBuilder() {
    }

    public static void reporter(ServerPlatform platform) {
        reporter = platform;
    }

    public static String colors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static BaseComponent build(List<Section> parts) {
        List<BaseComponent> done = new ArrayList<>();
        for (Section part : parts) {
            if (part.isSprite()) {
                done.add(sprite(part.sprite()));
            } else if (part.isJson()) {
                Collections.addAll(done, fromJson(part));
            } else {
                Collections.addAll(done, TextComponent.fromLegacyText(colors(part.text())));
            }
        }
        return new TextComponent(done.toArray(new BaseComponent[0]));
    }

    private static BaseComponent[] fromJson(Section part) {
        try {
            return ComponentSerializer.parse(part.json());
        } catch (Throwable error) {
            if (!tagsReported) {
                tagsReported = true;
                warne("Ein formatierter MOTD-Abschnitt konnte nicht gebaut werden, es wird auf "
                        + "einfache Farbcodes ausgewichen. JSON: " + part.json(), error);
            }
            return TextComponent.fromLegacyText(colors(part.text()));
        }
    }

    private static BaseComponent sprite(Sprite sprite) {
        try {
            BaseComponent[] parsed = ComponentSerializer.parse(sprite.toJson());
            return parsed.length == 1 ? parsed[0] : new TextComponent(parsed);
        } catch (Throwable error) {
            if (!spriteReported) {
                spriteReported = true;
                warne("Sprite '" + sprite.name() + "' konnte nicht gebaut werden, stattdessen wird "
                        + "der Fallback-Text angezeigt. Diese BungeeCord-Version kennt den Sprite-Typ "
                        + "vermutlich noch nicht. JSON: " + sprite.toJson(), error);
            }
            return new TextComponent(TextComponent.fromLegacyText(colors(sprite.fallback())));
        }
    }

    private static void warne(String text, Throwable error) {
        ServerPlatform platform = reporter;
        if (platform != null) {
            platform.warn(text, error);
        }
    }
}
