package me.D4rkSn0w.motd.sponge;

import com.google.inject.Inject;
import me.D4rkSn0w.motd.adventure.AdventureBuilder;
import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.PingData;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.PluginInfo;
import me.D4rkSn0w.motd.metrics.Metrics;
import me.D4rkSn0w.motd.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.lifecycle.ConstructPluginEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Plugin("motd")
public final class SpongeMotd implements ServerPlatform {

    private final PluginContainer container;
    private final Logger logger;
    private final Path dataFolder;
    private final MotdCore core;

    private Metrics metrics;
    private UpdateChecker updateChecker;
    private volatile boolean sampleReported;
    private final List<String> online = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Inject
    public SpongeMotd(PluginContainer container, Logger logger,
                      @ConfigDir(sharedRoot = false) Path dataFolder) {
        this.container = container;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.core = new MotdCore(this);
    }

    @Listener
    public void onConstruct(ConstructPluginEvent event) {
        AdventureBuilder.reporter(this);
        core.load();
    }

    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Parameterized> event) {
        Parameter.Value<String> aktion = Parameter.string().key("aktion").optional().build();

        Command.Parameterized haupt = Command.builder()
                .shortDescription(AdventureBuilder.legacy("&7Zeigt Infos zum Plugin."))
                .addParameter(aktion)
                .executor(context -> {
                    String value = context.one(aktion).orElse("");
                    if (!value.equalsIgnoreCase("reload")) {
                        for (String line : PluginInfo.lines(pluginVersion(), core.commandName())) {
                            context.sendMessage(AdventureBuilder.legacy(line));
                        }
                        if (updateChecker != null && updateChecker.updateAvailable()
                                && context.cause().hasPermission(UpdateChecker.PERMISSION)) {
                            context.sendMessage(updateComponent());
                        }
                        return CommandResult.success();
                    }
                    return reload(context);
                })
                .build();
        event.register(container, haupt, core.commandName());

        String alias = core.commandAlias();
        if (alias != null && !alias.isEmpty() && !alias.equalsIgnoreCase(core.commandName())) {
            Command.Parameterized shortText = Command.builder()
                    .shortDescription(AdventureBuilder.legacy("&7Lädt die MOTD-Config neu."))
                    .executor(this::reload)
                    .build();
            event.register(container, shortText, alias);
        }
    }

    private Component updateComponent() {
        String json = core.updateMessageJson(updateChecker.latestVersion(), pluginVersion());
        Component built = json == null ? null : AdventureBuilder.fromJson(json);
        return built != null ? built : AdventureBuilder.legacy(
                core.updateMessage(updateChecker.latestVersion(), pluginVersion()));
    }

    private CommandResult reload(CommandContext context) {
        String oldName = core.commandName();
        String oldAlias = core.commandAlias();
        if (!context.cause().hasPermission(core.commandPermission())) {
            context.sendMessage(AdventureBuilder.legacy(
                    core.message("no-permission", "&cDazu hast du keine Rechte.")));
            return CommandResult.success();
        }
        core.load();
        if (!oldName.equals(core.commandName()) || !oldAlias.equals(core.commandAlias())) {
            logger.warn("Befehlsname oder Alias wurden geändert. Sponge registriert Befehle nur "
                    + "beim Start - bis zum Neustart gilt weiter /{} und /{}.", oldName, oldAlias);
        }
        context.sendMessage(AdventureBuilder.legacy(
                core.message("reloaded", "&aConfig neu geladen.")));
        return CommandResult.success();
    }

    @Listener
    public void onStart(StartedEngineEvent<Server> event) {
        metrics = Metrics.start(this, core);
        updateChecker = UpdateChecker.start(this, core);
        logger.info("MOTD aktiviert ({}).", softwareName());
    }

    @Listener
    public void onShutdown(StoppingEngineEvent<Server> event) {
        if (metrics != null) {
            metrics.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
    }

    @Listener
    public void onJoin(ServerSideConnectionEvent.Join event) {
        String name = event.player().name();
        if (!online.contains(name)) {
            online.add(name);
        }
        if (updateChecker == null || !updateChecker.updateAvailable()) {
            return;
        }
        ServerPlayer player = event.player();
        if (!player.hasPermission(UpdateChecker.PERMISSION)) {
            return;
        }
        player.sendMessage(updateComponent());
    }

    @Listener
    public void onDisconnect(ServerSideConnectionEvent.Disconnect event) {
        event.profile().ifPresent(profile -> profile.name().ifPresent(online::remove));
    }

    @Listener(order = Order.LAST)
    public void onPing(ClientPingServerEvent event) {
        ClientPingServerEvent.Response response = event.response();
        Optional<ClientPingServerEvent.Response.Players> player = response.players();

        int realOnline = player.map(ClientPingServerEvent.Response.Players::online).orElse(0);
        int realMax = player.map(ClientPingServerEvent.Response.Players::max).orElse(0);
        int protocol = event.client().version().protocolVersion();

        PingData data = core.ping(realOnline, realMax, protocol);

        player.ifPresent(values -> {
            values.setOnline(data.online());
            values.setMax(data.max());
        });
        if (data.hasDescription()) {
            response.setDescription(AdventureBuilder.build(data.description()));
        }

        if (data.hasSample()) {
            player.ifPresent(values -> writeSample(values, data));
        }

        if (data.versionEnabled()) {
            ClientPingServerEvent.Response.Version version = response.version();
            version.setName(AdventureBuilder.legacyToString(data.versionText()));
            version.setProtocolVersion(data.versionProtocol());
        }
    }

    private void writeSample(ClientPingServerEvent.Response.Players values, PingData data) {
        try {
            values.profiles().clear();
            for (String line : data.sample()) {
                values.profiles().add(GameProfile.of(UUID.randomUUID(),
                        AdventureBuilder.legacyToString(line)));
            }
        } catch (Throwable error) {
            if (!sampleReported) {
                sampleReported = true;
                warn("Die Hover-Liste konnte nicht gesetzt werden und bleibt deshalb leer. "
                        + "Der Rest der MOTD ist davon nicht betroffen.", error);
            }
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
        try {
            return Sponge.platform().container(Platform.Component.IMPLEMENTATION)
                    .metadata().name().orElse("Sponge");
        } catch (Throwable error) {
            return "Sponge";
        }
    }

    @Override
    public String softwareVersion() {
        try {
            return String.valueOf(Sponge.platform().container(Platform.Component.IMPLEMENTATION)
                    .metadata().version());
        } catch (Throwable error) {
            return "unbekannt";
        }
    }

    @Override
    public String minecraftVersion() {
        try {
            return Sponge.platform().minecraftVersion().name();
        } catch (Throwable error) {
            return "unbekannt";
        }
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public String bstatsPlatform() {
        return "sponge";
    }

    @Override
    public int playerCount() {
        return Sponge.isServerAvailable() ? Sponge.server().onlinePlayers().size() : 0;
    }

    @Override
    public List<String> playerNames() {
        return new ArrayList<>(online);
    }

    @Override
    public boolean onlineMode() {
        return Sponge.isServerAvailable() && Sponge.server().isOnlineModeEnabled();
    }

    @Override
    public boolean metricsAllowed() {
        try {
            return Sponge.metricsConfigManager().effectiveCollectionState(container).asBoolean();
        } catch (Throwable error) {
            return false;
        }
    }

    @Override
    public String pluginVersion() {
        return String.valueOf(container.metadata().version());
    }

    @Override
    public void runTask(Runnable task) {
        Sponge.asyncScheduler().submit(Task.builder()
                .plugin(container)
                .execute(task)
                .build());
    }
}
