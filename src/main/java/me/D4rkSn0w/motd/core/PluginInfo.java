package me.D4rkSn0w.motd.core;

import java.util.ArrayList;
import java.util.List;

public final class PluginInfo {

    public static final String VERSION = "1.3-hotfix";
    public static final String AUTOR = "D4rkSn0w";
    public static final String WEBSITE = "https://kaktuscrew.net";
    public static final String DISCORD = "https://discord.gg/AccQuwNYfT";
    public static final String SPIGOT =
            "https://www.spigotmc.org/resources/custommotd-custom-textures-proxy-folia-support.139865/";
    public static final String MODRINTH = "https://modrinth.com/project/spritemotd";
    public static final String HANGAR = "https://hangar.papermc.io/D4rkSn0w/MOTD";

    private static final String LINE = "&8&m                                                        ";

    private PluginInfo() {
    }

    public static List<String> lines(String version, String command) {
        List<String> out = new ArrayList<>();
        out.add(LINE);
        out.add(" &a&lMOTD &7v" + version + " &8- &7von &a" + AUTOR);
        out.add("");
        out.add(" &7Webseite &8» &b" + WEBSITE);
        out.add(" &7Discord &8» &b" + DISCORD);
        out.add("");
        out.add(" &7Download");
        out.add(" &8» &b" + SPIGOT);
        out.add(" &8» &b" + MODRINTH);
        out.add(" &8» &b" + HANGAR);
        out.add("");
        out.add(" &7Befehl &8» &a/" + command + " reload &8- &7lädt die Config neu");
        out.add(LINE);
        return out;
    }
}
