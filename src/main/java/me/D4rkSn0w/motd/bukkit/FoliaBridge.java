package me.D4rkSn0w.motd.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

final class FoliaBridge {

    private FoliaBridge() {
    }

    static void run(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }
}
