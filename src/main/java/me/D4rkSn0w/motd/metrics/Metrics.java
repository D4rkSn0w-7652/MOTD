package me.D4rkSn0w.motd.metrics;

import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.ServerPlatform;
import org.bstats.MetricsBase;
import org.bstats.charts.SimplePie;
import org.bstats.json.JsonObjectBuilder;

public final class Metrics {

    private static final int BUKKIT = 33960;
    private static final int BUNGEECORD = 33963;
    private static final int VELOCITY = 33964;
    private static final int SPONGE = 33965;

    private final MetricsBase basis;

    private Metrics(MetricsBase basis) {
        this.basis = basis;
    }

    public static Metrics start(ServerPlatform platform, MotdCore core) {
        int serviceId = serviceId(platform.bstatsPlatform());
        try {
            BstatsConfig file = BstatsConfig.load(platform);

            if (!file.enabled) {
                platform.info("bStats ist in " + file.path + " abgeschaltet - es werden keine Daten gesendet.");
                return null;
            }

            MetricsBase basis = new MetricsBase(
                    platform.bstatsPlatform(),
                    file.serverUuid,
                    serviceId,
                    true,
                    builder -> platformData(platform, builder),
                    builder -> builder.appendField("pluginVersion", platform.pluginVersion()),
                    platform::runTask,
                    platform::metricsAllowed,
                    platform::warn,
                    platform::info,
                    file.logErrors,
                    file.logSentData,
                    file.logResponseStatus,
                    false);

            basis.addCustomChart(new SimplePie("server_software", platform::softwareName));
            basis.addCustomChart(new SimplePie("platform_type", () -> platform.isProxy() ? "Proxy" : "Backend"));
            basis.addCustomChart(new SimplePie("minecraft_version", platform::minecraftVersion));
            basis.addCustomChart(new SimplePie("sprites", () -> core.spritesEnabled() ? "an" : "aus"));

            if (platform.metricsAllowed()) {
                platform.info("bStats aktiv (Plattform " + platform.bstatsPlatform() + ", Plugin-ID "
                        + serviceId + ") - die erste Übertragung folgt in 3 bis 6 Minuten.");
            } else {
                platform.info("bStats ist über die Sponge-Einstellungen für dieses Plugin nicht freigegeben.");
            }
            return new Metrics(basis);
        } catch (Throwable error) {
            platform.warn("bStats konnte nicht gestartet werden:", error);
            return null;
        }
    }

    public void stop() {
        basis.shutdown();
    }

    private static int serviceId(String platform) {
        switch (platform) {
            case "bungeecord":
                return BUNGEECORD;
            case "velocity":
                return VELOCITY;
            case "sponge":
                return SPONGE;
            default:
                return BUKKIT;
        }
    }

    private static void platformData(ServerPlatform platform, JsonObjectBuilder builder) {
        builder.appendField("playerAmount", platform.playerCount());
        builder.appendField("onlineMode", platform.onlineMode() ? 1 : 0);

        switch (platform.bstatsPlatform()) {
            case "velocity":
                builder.appendField("managedServers", platform.managedServers());
                builder.appendField("velocityVersionVersion", platform.softwareVersion());
                builder.appendField("velocityVersionName", platform.softwareName());
                builder.appendField("velocityVersionVendor", platform.softwareVendor());
                break;
            case "bungeecord":
                builder.appendField("managedServers", platform.managedServers());
                builder.appendField("bungeecordVersion", platform.softwareVersion());
                builder.appendField("bungeecordName", platform.softwareName());
                break;
            case "sponge":
                builder.appendField("minecraftVersion", platform.minecraftVersion());
                builder.appendField("spongeImplementation", platform.softwareName());
                break;
            default:
                builder.appendField("bukkitVersion", platform.softwareVersion());
                builder.appendField("bukkitName", platform.softwareName());
                break;
        }

        builder.appendField("javaVersion", System.getProperty("java.version"));
        builder.appendField("osName", System.getProperty("os.name"));
        builder.appendField("osArch", System.getProperty("os.arch"));
        builder.appendField("osVersion", System.getProperty("os.version"));
        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
    }
}
