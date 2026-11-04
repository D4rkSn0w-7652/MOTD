package me.D4rkSn0w.motd.bukkit;

import me.D4rkSn0w.motd.core.Section;
import org.bukkit.ChatColor;

import java.util.List;

final class LegacyBuilder {

    private LegacyBuilder() {
    }

    static String colors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    static String build(List<Section> parts) {
        StringBuilder text = new StringBuilder();
        for (Section part : parts) {
            text.append(part.isSprite() ? part.sprite().fallback() : part.text());
        }
        return colors(text.toString());
    }
}
