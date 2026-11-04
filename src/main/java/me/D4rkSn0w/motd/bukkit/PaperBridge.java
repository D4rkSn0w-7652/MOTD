package me.D4rkSn0w.motd.bukkit;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import me.D4rkSn0w.motd.adventure.AdventureBuilder;
import me.D4rkSn0w.motd.core.Section;
import me.D4rkSn0w.motd.core.ServerPlatform;
import org.bukkit.Bukkit;
import org.bukkit.event.server.ServerListPingEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PaperBridge {

    private static final char SECTION = (char) 0xA7;
    private static final int NAME_LIMIT = 16;

    private static volatile boolean motdReported;
    private static volatile boolean countReported;
    private static volatile boolean versionReported;
    private static volatile boolean sampleReported;
    private static volatile boolean shortReported;

    private PaperBridge() {
    }

    static void reporter(ServerPlatform platform) {
        AdventureBuilder.reporter(platform);
    }

    static int protocol(ServerListPingEvent event) {
        if (event instanceof PaperServerListPingEvent) {
            return ((PaperServerListPingEvent) event).getClient().getProtocolVersion();
        }
        return -1;
    }

    static void setPlayerCount(ServerListPingEvent event, int online, ServerPlatform platform) {
        if (!(event instanceof PaperServerListPingEvent)) {
            return;
        }
        try {
            ((PaperServerListPingEvent) event).setNumPlayers(online);
        } catch (Throwable error) {
            if (!countReported) {
                countReported = true;
                platform.warn("Die Spielerzahl konnte nicht gesetzt werden, deshalb bleibt die "
                        + "echte Zahl stehen. Der Rest der MOTD ist davon nicht betroffen.", error);
            }
        }
    }

    static void versionDisplay(ServerListPingEvent event, String text, int protocol,
            ServerPlatform platform) {
        if (!(event instanceof PaperServerListPingEvent)) {
            return;
        }
        try {
            PaperServerListPingEvent paper = (PaperServerListPingEvent) event;
            paper.setVersion(AdventureBuilder.legacyToString(text));
            paper.setProtocolVersion(protocol);
        } catch (Throwable error) {
            if (!versionReported) {
                versionReported = true;
                platform.warn("Die Versionsanzeige konnte nicht gesetzt werden. Der Rest der "
                        + "MOTD ist davon nicht betroffen.", error);
            }
        }
    }

    static boolean sendJson(Object receiver, String json) {
        try {
            net.kyori.adventure.text.Component built = AdventureBuilder.fromJson(json);
            if (built == null || !(receiver instanceof net.kyori.adventure.audience.Audience)) {
                return false;
            }
            ((net.kyori.adventure.audience.Audience) receiver).sendMessage(built);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    static void sample(ServerListPingEvent event, List<String> lines, ServerPlatform platform) {
        if (!(event instanceof PaperServerListPingEvent)) {
            return;
        }
        PaperServerListPingEvent paper = (PaperServerListPingEvent) event;
        List<String> texts = new ArrayList<>(lines.size());
        for (String line : lines) {
            texts.add(AdventureBuilder.legacyToString(line));
        }
        try {
            if (writeListed(paper, texts)) {
                return;
            }
            writeProfiles(paper, texts, platform);
        } catch (Throwable error) {
            if (!sampleReported) {
                sampleReported = true;
                platform.warn("Die Hover-Liste konnte nicht gesetzt werden und bleibt deshalb "
                        + "leer. Der Rest der MOTD ist davon nicht betroffen.", error);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean writeListed(PaperServerListPingEvent paper, List<String> texts) {
        if (Listed.GETTER == null || Listed.INFO == null) {
            return false;
        }
        try {
            Object raw = Listed.GETTER.invoke(paper);
            if (!(raw instanceof List)) {
                return false;
            }
            List<Object> target = (List<Object>) raw;
            target.clear();
            for (String text : texts) {
                target.add(Listed.INFO.newInstance(text, UUID.randomUUID()));
            }
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void writeProfiles(PaperServerListPingEvent paper, List<String> texts,
            ServerPlatform platform) {
        List<PlayerProfile> profiles = paper.getPlayerSample();
        profiles.clear();
        for (String text : texts) {
            String name = shorten(text);
            if (name.length() < text.length() && !shortReported) {
                shortReported = true;
                platform.warn("Diese Server-Version erlaubt in der Hover-Liste nur " + NAME_LIMIT
                        + " Zeichen pro Zeile, deshalb werden längere Zeilen gekürzt. "
                        + "Ab Minecraft 1.20.6 gilt diese Grenze nicht mehr.", null);
            }
            profiles.add(Bukkit.createProfileExact(UUID.randomUUID(), name));
        }
    }

    private static String shorten(String text) {
        if (text.length() <= NAME_LIMIT) {
            return text;
        }
        String cut = text.substring(0, NAME_LIMIT);
        return cut.charAt(NAME_LIMIT - 1) == SECTION ? cut.substring(0, NAME_LIMIT - 1) : cut;
    }

    static boolean description(ServerListPingEvent event, List<Section> parts, ServerPlatform platform) {
        try {
            event.motd(AdventureBuilder.build(parts));
            return true;
        } catch (Throwable error) {
            if (!motdReported) {
                motdReported = true;
                platform.warn("Die MOTD konnte nicht als Komponente gesetzt werden, deshalb wird "
                        + "auf einfachen Farbtext ausgewichen - Sprites gehen dabei verloren. "
                        + "Ereignisklasse: " + event.getClass().getName(), error);
            }
            return false;
        }
    }

    private static final class Listed {
        static final Method GETTER;
        static final Constructor<?> INFO;

        static {
            Method getter = null;
            Constructor<?> info = null;
            try {
                getter = PaperServerListPingEvent.class.getMethod("getListedPlayers");
                info = Class.forName("com.destroystokyo.paper.event.server."
                        + "PaperServerListPingEvent$ListedPlayerInfo")
                        .getConstructor(String.class, UUID.class);
            } catch (Throwable error) {
                getter = null;
                info = null;
            }
            GETTER = getter;
            INFO = info;
        }
    }
}
