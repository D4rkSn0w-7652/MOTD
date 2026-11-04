package me.D4rkSn0w.motd.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import me.D4rkSn0w.motd.adventure.AdventureBuilder;
import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.PingData;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.PluginInfo;
import me.D4rkSn0w.motd.metrics.Metrics;
import me.D4rkSn0w.motd.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.UUID;

@Plugin(
        id = "motd",
        name = "MOTD",
        version = PluginInfo.VERSION,
        description = "MOTD für Paper, Purpur, Folia, Spigot, Sponge, Velocity, BungeeCord und Waterfall",
        authors = {"D4rkSn0w"}
)
public class VelocityMotd implements ServerPlatform {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;
    private final MotdCore core;

    private Metrics metrics;
    private UpdateChecker updateChecker;
    private volatile boolean sampleReported;
    private String commandName;
    private String commandAlias;

    @Inject
    public VelocityMotd(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.core = new MotdCore(this);
    }

    @Subscribe
    public void onStart(ProxyInitializeEvent event) {
        core.load();
        AdventureBuilder.reporter(this);
        registerCommand();
        metrics = Metrics.start(this, core);
        updateChecker = UpdateChecker.start(this, core);
        logger.info("MOTD aktiviert ({}).", softwareName());
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (metrics != null) {
            metrics.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
    }

    @Subscribe
    public void onJoin(PostLoginEvent event) {
        if (updateChecker == null || !updateChecker.updateAvailable()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(UpdateChecker.PERMISSION)) {
            return;
        }
        sendUpdate(player);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPing(ProxyPingEvent event) {
        ServerPing ping = event.getPing();
        int realOnline = ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
        int realMax = ping.getPlayers().map(ServerPing.Players::getMax).orElse(0);
        int protocol = event.getConnection().getProtocolVersion().getProtocol();

        PingData data = core.ping(realOnline, realMax, protocol);

        ServerPing.Builder builder = ping.asBuilder()
                .onlinePlayers(data.online())
                .maximumPlayers(data.max());

        if (data.hasDescription()) {
            builder.description(AdventureBuilder.build(data.description()));
        }

        if (data.hasSample()) {
            try {
                List<ServerPing.SamplePlayer> sample = new ArrayList<>();
                for (String line : data.sample()) {
                    sample.add(new ServerPing.SamplePlayer(
                            AdventureBuilder.legacyToString(line), UUID.randomUUID()));
                }
                builder.clearSamplePlayers()
                        .samplePlayers(sample.toArray(new ServerPing.SamplePlayer[0]));
            } catch (Throwable error) {
                if (!sampleReported) {
                    sampleReported = true;
                    warn("Die Hover-Liste konnte nicht gesetzt werden und bleibt deshalb leer. "
                            + "Der Rest der MOTD ist davon nicht betroffen.", error);
                }
            }
        }

        if (data.versionEnabled()) {
            builder.version(new ServerPing.Version(
                    data.versionProtocol(),
                    AdventureBuilder.legacyToString(data.versionText())));
        }

        event.setPing(builder.build());
    }

    private void sendUpdate(com.velocitypowered.api.command.CommandSource target) {
        String json = core.updateMessageJson(updateChecker.latestVersion(), pluginVersion());
        Component built = json == null ? null : AdventureBuilder.fromJson(json);
        target.sendMessage(built != null ? built : AdventureBuilder.legacy(
                core.updateMessage(updateChecker.latestVersion(), pluginVersion())));
    }

    private void registerCommand() {
        CommandManager verwaltung = proxy.getCommandManager();
        String newName = core.commandName();
        String newAlias = core.commandAlias();

        if (newName.equals(commandName) && newAlias.equals(commandAlias)) {
            return;
        }
        if (commandName != null) {
            verwaltung.unregister(commandName);
        }
        if (commandAlias != null) {
            verwaltung.unregister(commandAlias);
        }

        commandName = newName;
        commandAlias = newAlias;

        CommandMeta description = verwaltung.metaBuilder(commandName)
                .aliases(commandAlias)
                .plugin(this)
                .build();
        verwaltung.register(description, new MotdCommand());
    }

    private class MotdCommand implements SimpleCommand {
        @Override
        public void execute(Invocation aufruf) {
            CommandSource sender = aufruf.source();
            if (!core.isReload(aufruf.alias(), aufruf.arguments())) {
                for (String line : PluginInfo.lines(pluginVersion(), core.commandName())) {
                    sender.sendMessage(AdventureBuilder.legacy(line));
                }
                if (updateChecker != null && updateChecker.updateAvailable()
                        && sender.hasPermission(UpdateChecker.PERMISSION)) {
                    sendUpdate(sender);
                }
                return;
            }
            if (!sender.hasPermission(core.commandPermission())) {
                sender.sendMessage(AdventureBuilder.legacy(
                        core.message("no-permission", "&cDazu hast du keine Rechte.")));
                return;
            }
            core.load();
            registerCommand();
            sender.sendMessage(AdventureBuilder.legacy(
                    core.message("reloaded", "&aConfig neu geladen.")));
        }
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public InputStream bundledResource(String name) {
        return getClass().getResourceAsStream("/" + name);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        if (error == null) {
            logger.warn(message);
        } else {
            logger.warn(message, error);
        }
    }

    @Override
    public String softwareName() {
        return proxy.getVersion().getName();
    }

    @Override
    public String softwareVersion() {
        return proxy.getVersion().getVersion();
    }

    @Override
    public String softwareVendor() {
        return proxy.getVersion().getVendor();
    }

    @Override
    public String minecraftVersion() {
        try {
            return ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion();
        } catch (Throwable error) {
            return "unbekannt";
        }
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public String bstatsPlatform() {
        return "velocity";
    }

    @Override
    public int playerCount() {
        return proxy.getPlayerCount();
    }

    @Override
    public List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : proxy.getAllPlayers()) {
            names.add(player.getUsername());
        }
        return names;
    }

    @Override
    public int managedServers() {
        return proxy.getAllServers().size();
    }

    @Override
    public boolean onlineMode() {
        return proxy.getConfiguration().isOnlineMode();
    }

    @Override
    public String pluginVersion() {
        return proxy.getPluginManager().fromInstance(this)
                .map(container -> container.getDescription().getVersion().orElse(PluginInfo.VERSION))
                .orElse(PluginInfo.VERSION);
    }

    @Override
    public void runTask(Runnable task) {
        proxy.getScheduler().buildTask(this, task).schedule();
    }
}
