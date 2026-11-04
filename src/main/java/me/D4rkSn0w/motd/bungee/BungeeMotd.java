package me.D4rkSn0w.motd.bungee;

import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.PingData;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.PluginInfo;
import me.D4rkSn0w.motd.metrics.Metrics;
import me.D4rkSn0w.motd.update.UpdateChecker;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

public class BungeeMotd extends Plugin implements Listener, ServerPlatform {

    private MotdCore core;
    private Metrics metrics;
    private UpdateChecker updateChecker;
    private volatile boolean sampleReported;
    private Command command;
    private Command aliasCommand;

    @Override
    public void onEnable() {
        core = new MotdCore(this);
        core.load();
        BungeeBuilder.reporter(this);
        getProxy().getPluginManager().registerListener(this, this);
        registerCommand();
        metrics = Metrics.start(this, core);
        updateChecker = UpdateChecker.start(this, core);
        getLogger().info("MOTD aktiviert (" + softwareName() + ").");
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ProxyPingEvent event) {
        ServerPing ping = event.getResponse();
        if (ping == null) {
            return;
        }
        ServerPing.Players player = ping.getPlayers();
        int realOnline = player == null ? 0 : player.getOnline();
        int realMax = player == null ? 0 : player.getMax();
        int protocol = event.getConnection().getVersion();

        PingData data = core.ping(realOnline, realMax, protocol);

        if (player != null) {
            player.setOnline(data.online());
            player.setMax(data.max());
        }
        if (data.hasDescription()) {
            ping.setDescriptionComponent(BungeeBuilder.build(data.description()));
        }

        if (data.hasSample() && player != null) {
            try {
                ServerPing.PlayerInfo[] sample = new ServerPing.PlayerInfo[data.sample().size()];
                for (int i = 0; i < sample.length; i++) {
                    sample[i] = new ServerPing.PlayerInfo(
                            BungeeBuilder.colors(data.sample().get(i)), UUID.randomUUID());
                }
                player.setSample(sample);
            } catch (Throwable error) {
                if (!sampleReported) {
                    sampleReported = true;
                    warn("Die Hover-Liste konnte nicht gesetzt werden und bleibt deshalb leer. "
                            + "Der Rest der MOTD ist davon nicht betroffen.", error);
                }
            }
        }

        ServerPing.Protocol version = ping.getVersion();
        if (data.versionEnabled() && version != null) {
            version.setName(BungeeBuilder.colors(data.versionText()));
            version.setProtocol(data.versionProtocol());
        }

        event.setResponse(ping);
    }

    @EventHandler
    public void onJoin(PostLoginEvent event) {
        if (updateChecker == null || !updateChecker.updateAvailable()) {
            return;
        }
        ProxiedPlayer player = event.getPlayer();
        if (!player.hasPermission(UpdateChecker.PERMISSION)) {
            return;
        }
        sendUpdate(player);
    }

    private void registerCommand() {
        if (command != null) {
            getProxy().getPluginManager().unregisterCommand(command);
        }
        if (aliasCommand != null) {
            getProxy().getPluginManager().unregisterCommand(aliasCommand);
        }
        command = new MotdCommand(core.commandName(), false);
        getProxy().getPluginManager().registerCommand(this, command);

        String alias = core.commandAlias();
        if (alias != null && !alias.isEmpty() && !alias.equalsIgnoreCase(core.commandName())) {
            aliasCommand = new MotdCommand(alias, true);
            getProxy().getPluginManager().registerCommand(this, aliasCommand);
        } else {
            aliasCommand = null;
        }
    }

    private class MotdCommand extends Command {
        private final boolean alwaysReload;

        MotdCommand(String name, boolean alwaysReload) {
            super(name);
            this.alwaysReload = alwaysReload;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!alwaysReload && !core.isReload(null, args)) {
                for (String line : PluginInfo.lines(pluginVersion(), core.commandName())) {
                    send(sender, line);
                }
                if (updateChecker != null && updateChecker.updateAvailable()
                        && sender.hasPermission(UpdateChecker.PERMISSION)) {
                    sendUpdate(sender);
                }
                return;
            }
            if (!sender.hasPermission(core.commandPermission())) {
                send(sender, core.message("no-permission", "&cDazu hast du keine Rechte."));
                return;
            }
            core.load();
            registerCommand();
            send(sender, core.message("reloaded", "&aConfig neu geladen."));
        }
    }

    private void sendUpdate(CommandSender target) {
        String json = core.updateMessageJson(updateChecker.latestVersion(), pluginVersion());
        if (json != null) {
            try {
                target.sendMessage(ComponentSerializer.parse(json));
                return;
            } catch (Throwable ignored) {
            }
        }
        send(target, core.updateMessage(updateChecker.latestVersion(), pluginVersion()));
    }

    private void send(CommandSender receiver, String text) {
        receiver.sendMessage(new TextComponent(TextComponent.fromLegacyText(BungeeBuilder.colors(text))));
    }

    @Override
    public Path dataFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public InputStream bundledResource(String name) {
        return getClass().getResourceAsStream("/" + name);
    }

    @Override
    public void info(String message) {
        getLogger().info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        if (error == null) {
            getLogger().warning(message);
        } else {
            getLogger().log(Level.WARNING, message, error);
        }
    }

    @Override
    public String softwareName() {
        return getProxy().getName();
    }

    @Override
    public String softwareVersion() {
        return getProxy().getVersion();
    }

    @Override
    public String minecraftVersion() {
        String versionen = getProxy().getGameVersion();
        if (versionen == null || versionen.isEmpty()) {
            return "unbekannt";
        }
        String[] parts = versionen.split(",");
        return parts[parts.length - 1].trim();
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public String bstatsPlatform() {
        return "bungeecord";
    }

    @Override
    public int playerCount() {
        return getProxy().getPlayers().size();
    }

    @Override
    public List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override
    public int managedServers() {
        return getProxy().getServers().size();
    }

    @Override
    public boolean onlineMode() {
        return getProxy().getConfig().isOnlineMode();
    }

    @Override
    public String pluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public void runTask(Runnable task) {
        getProxy().getScheduler().runAsync(this, task);
    }
}
