package me.D4rkSn0w.motd.core;

import java.io.InputStream;
import java.nio.file.Path;

public interface ServerPlatform {

    Path dataFolder();

    InputStream bundledResource(String name);

    void info(String message);

    void warn(String message, Throwable error);

    String softwareName();

    String softwareVersion();

    default String softwareVendor() {
        return "";
    }

    String minecraftVersion();

    boolean isProxy();

    String bstatsPlatform();

    int playerCount();

    default java.util.List<String> playerNames() {
        return java.util.Collections.emptyList();
    }

    default int managedServers() {
        return 0;
    }

    boolean onlineMode();

    default boolean metricsAllowed() {
        return true;
    }

    String pluginVersion();

    void runTask(Runnable task);
}
