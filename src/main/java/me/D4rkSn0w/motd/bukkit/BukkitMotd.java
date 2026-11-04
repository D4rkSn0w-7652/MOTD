package me.D4rkSn0w.motd.bukkit;

import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.PingData;
import me.D4rkSn0w.motd.core.ServerPlatform;
import me.D4rkSn0w.motd.core.PluginInfo;
import me.D4rkSn0w.motd.metrics.Metrics;
import me.D4rkSn0w.motd.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class BukkitMotd extends JavaPlugin implements Listener, CommandExecutor, ServerPlatform {

    private MotdCore core;
    private Metrics metrics;
    private UpdateChecker updateChecker;
    private boolean paper;
    private boolean folia;
    private String software = "Bukkit";
    private CustomCommand extra;
    private final List<String> online = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() {
        folia = classPresent("io.papermc.paper.threadedregions.RegionizedServer");
        paper = classPresent("com.destroystokyo.paper.event.server.PaperServerListPingEvent");
        software = detectSoftware();

        if (paper) {
            PaperBridge.reporter(this);
        }

        core = new MotdCore(this);
        core.load();

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("motd");
        if (command != null) {
            command.setExecutor(this);
        }
        registerExtraCommand();

        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getName());
        }

        metrics = Metrics.start(this, core);
        updateChecker = UpdateChecker.start(this, core);

        if (!paper) {
            getLogger().info("Kein Paper erkannt: Sprites, Spielerzahl und Versionsanzeige "
                    + "sind über die Bukkit-API nicht möglich.");
        }
        getLogger().info("MOTD aktiviert (" + software + ").");
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
    public void onPing(ServerListPingEvent event) {
        int protocol = paper ? PaperBridge.protocol(event) : -1;
        PingData data = core.ping(event.getNumPlayers(), event.getMaxPlayers(), protocol);

        event.setMaxPlayers(data.max());

        boolean done = false;
        if (paper) {
            PaperBridge.setPlayerCount(event, data.online(), this);
            if (data.hasDescription()) {
                done = PaperBridge.description(event, data.description(), this);
            }
            if (data.versionEnabled()) {
                PaperBridge.versionDisplay(event, data.versionText(), data.versionProtocol(), this);
            }
            if (data.hasSample()) {
                PaperBridge.sample(event, data.sample(), this);
            }
        }
        if (!done && data.hasDescription()) {
            event.setMotd(LegacyBuilder.build(data.description()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        if (!online.contains(name)) {
            online.add(name);
        }
        if (updateChecker == null || !updateChecker.updateAvailable()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission(UpdateChecker.PERMISSION)) {
            return;
        }
        sendUpdate(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        online.remove(event.getPlayer().getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        execute(sender, label, args);
        return true;
    }

    private void execute(CommandSender sender, String label, String[] args) {
        boolean reload = "motdreload".equalsIgnoreCase(label) || core.isReload(label, args);
        if (!reload) {
            for (String line : PluginInfo.lines(pluginVersion(), core.commandName())) {
                sender.sendMessage(LegacyBuilder.colors(line));
            }
            if (showUpdate(sender)) {
                sendUpdate(sender);
            }
            return;
        }
        if (!sender.hasPermission(core.commandPermission())) {
            sender.sendMessage(LegacyBuilder.colors(
                    core.message("no-permission", "&cDazu hast du keine Rechte.")));
            return;
        }
        core.load();
        registerExtraCommand();
        sender.sendMessage(LegacyBuilder.colors(
                core.message("reloaded", "&aConfig neu geladen.")));
    }

    private void sendUpdate(CommandSender target) {
        String json = core.updateMessageJson(updateChecker.latestVersion(), pluginVersion());
        if (json != null && paper && PaperBridge.sendJson(target, json)) {
            return;
        }
        target.sendMessage(LegacyBuilder.colors(
                core.updateMessage(updateChecker.latestVersion(), pluginVersion())));
    }

    private boolean showUpdate(CommandSender sender) {
        return updateChecker != null && updateChecker.updateAvailable()
                && (!(sender instanceof Player) || ((Player) sender).isOp()
                || sender.hasPermission(UpdateChecker.PERMISSION));
    }

    private void registerExtraCommand() {
        String name = core.commandName();
        String alias = core.commandAlias();
        boolean wanted = !name.equals("motd") || !alias.equals("motdreload");
        try {
            CommandMap map = commandMap();
            if (map == null) {
                if (wanted) {
                    getLogger().info("/" + name + " konnte nicht angelegt werden - /motd bleibt nutzbar.");
                }
                return;
            }
            removeExtraCommand(map);
            if (!wanted) {
                return;
            }
            extra = new CustomCommand(name, alias);
            map.register(getName().toLowerCase(Locale.ROOT), extra);
        } catch (Throwable error) {
            getLogger().info("/" + name + " konnte nicht angelegt werden - /motd bleibt nutzbar.");
        }
    }

    private CommandMap commandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    private void removeExtraCommand(CommandMap map) {
        if (extra == null) {
            return;
        }
        try {
            Method known = map.getClass().getMethod("getKnownCommands");
            Object value = known.invoke(map);
            if (value instanceof Map) {
                ((Map<?, ?>) value).values().removeIf(entry -> entry == extra);
            }
        } catch (ReflectiveOperationException ignored) {
            getLogger().info("Der alte Befehl /" + extra.getName()
                    + " bleibt bis zum Neustart bestehen.");
        }
        extra.unregister(map);
        extra = null;
    }

    private final class CustomCommand extends Command {
        private CustomCommand(String name, String alias) {
            super(name);
            setAliases(Collections.singletonList(alias));
            setDescription("Lädt die MOTD-Config neu.");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            BukkitMotd.this.execute(sender, label, args);
            return true;
        }
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static String detectSoftware() {
        if (classPresent("io.papermc.paper.threadedregions.RegionizedServer")) {
            return "Folia";
        }
        if (classPresent("org.purpurmc.purpur.PurpurConfig")) {
            return "Purpur";
        }
        if (classPresent("io.papermc.paper.configuration.Configuration")
                || classPresent("com.destroystokyo.paper.PaperConfig")) {
            return "Paper";
        }
        if (classPresent("org.spigotmc.SpigotConfig")) {
            return "Spigot";
        }
        return Bukkit.getName();
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
        return software;
    }

    @Override
    public String softwareVersion() {
        return Bukkit.getVersion();
    }

    @Override
    public String minecraftVersion() {
        String version = Bukkit.getBukkitVersion();
        int strich = version.indexOf('-');
        return strich > 0 ? version.substring(0, strich) : version;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public String bstatsPlatform() {
        return "bukkit";
    }

    @Override
    public int playerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public List<String> playerNames() {
        return new ArrayList<>(online);
    }

    @Override
    public boolean onlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public String pluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public void runTask(Runnable task) {
        if (folia) {
            FoliaBridge.run(this, task);
            return;
        }
        Bukkit.getScheduler().runTask(this, task);
    }
}
