package me.D4rkSn0w.motd.core;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MotdCore {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");
    private static final int WIDTH = 45;
    private static final long WARN_PAUSE = 60000;
    private static final int VERSION_RIGHT_EDGE = 288;
    private static final int SPACE_WIDTH = 4;
    private static final int MAX_PAD = 250;
    private static final char SECTION = (char) 0xA7;
    private static final String[] ATLASES = {
            "minecraft:blocks", "minecraft:items", "minecraft:gui", "minecraft:particles",
            "minecraft:paintings", "minecraft:armor_trims", "minecraft:chests",
            "minecraft:decorated_pot", "minecraft:shield_patterns", "minecraft:banner_patterns",
            "minecraft:beds", "minecraft:shulker_boxes", "minecraft:signs",
            "minecraft:map_decorations", "minecraft:celestials"};

    private static final String FILE = "settings.yml";

    private static final class View {

        private final Config settings;
        private final String motd;
        private final List<String> motds;
        private final boolean sprites;
        private final int minProtocol;
        private final boolean debug;
        private final Map<String, Sprite> spriteMap;

        private View(Config settings, String motd, List<String> motds,
                     boolean sprites, int minProtocol, boolean debug) {
            this.settings = settings;
            this.motd = motd;
            this.motds = Collections.unmodifiableList(new ArrayList<>(motds));
            this.sprites = sprites;
            this.minProtocol = minProtocol;
            this.debug = debug;

            Map<String, Sprite> made = new LinkedHashMap<>();
            for (String name : settings.keys("sprites")) {
                made.put(name, build(settings, name));
            }
            this.spriteMap = Collections.unmodifiableMap(made);
        }

        private static Sprite build(Config settings, String name) {
            return new Sprite(
                    name,
                    atlasOf(settings, name),
                    settings.text("sprites." + name + ".sprite", ""),
                    settings.text("sprites." + name + ".farbe", "white"),
                    settings.text("sprites." + name + ".fallback", ""));
        }
    }

    private final ServerPlatform platform;
    private final Language language;
    private final Set<Integer> reported = ConcurrentHashMap.newKeySet();
    private final long startedAt = System.currentTimeMillis();

    private volatile View view =
            new View(Config.empty(), "", Collections.emptyList(), true, 773, false);
    private volatile long lastWarning;

    public MotdCore(ServerPlatform platform) {
        this.platform = platform;
        this.language = new Language(platform);
    }

    public Config settings() {
        return view.settings;
    }

    public Language language() {
        return language;
    }

    public void load() {
        View previous = view;
        boolean taken = false;
        try {
            Path folder = platform.dataFolder();
            Files.createDirectories(folder);
            Path file = folder.resolve(FILE);

            if (!Files.exists(file)) {
                try (InputStream in = platform.bundledResource(FILE)) {
                    if (in == null) {
                        platform.warn(FILE + " liegt nicht in der Jar - die MOTD bleibt unverändert.", null);
                        return;
                    }
                    Files.copy(in, file);
                    platform.info(FILE + " wurde angelegt: " + file);
                }
            }

            Config settings;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings = Config.load(reader);
            }

            String motd = settings.text("motd", "");
            View fresh = new View(
                    settings,
                    motd,
                    buildMotdList(settings, motd),
                    settings.flag("sprites-aktiviert", true),
                    settings.number("sprite-min-protokoll", 773),
                    settings.flag("debug", false));
            String wanted = settings.text("sprache", Language.FALLBACK_CODE);

            view = fresh;
            reported.clear();
            taken = true;

            language.load(wanted);
            check(fresh, file);
            checkVersion();
        } catch (Throwable error) {
            if (taken) {
                platform.warn("Die " + FILE + " ist übernommen, aber danach ist etwas schiefgegangen "
                        + "(Sprachdatei oder Prüfung). Die MOTD arbeitet mit den neuen Werten. "
                        + "Grund: " + shortText(error), error);
                return;
            }
            platform.warn(FILE + " konnte nicht gelesen werden - "
                    + (previous.motd.isEmpty()
                    ? "es wird KEINE MOTD gesetzt, der Server zeigt seine eigene."
                    : "die zuletzt funktionierende Konfiguration bleibt vollständig aktiv.")
                    + " Grund: " + shortText(error), error);
        }
    }

    private void checkVersion() {
        String descriptor = platform.pluginVersion();
        if (descriptor != null && !descriptor.isEmpty()
                && !descriptor.equals(PluginInfo.VERSION)) {
            platform.warn("Versionen laufen auseinander: der Plugin-Deskriptor sagt " + descriptor
                    + ", PluginInfo.VERSION sagt " + PluginInfo.VERSION
                    + ". Beim Release beide hochziehen, sonst meldet der Update-Checker falsch.", null);
        }
    }

    private void check(View v, Path file) {
        if (v.motd.trim().isEmpty()) {
            platform.warn("In der " + FILE + " steht keine MOTD (Schlüssel 'motd') - "
                    + "der Server zeigt weiter seine eigene MOTD aus der server.properties.", null);
            return;
        }

        List<String> spriteNames = v.settings.keys("sprites");
        platform.info("MOTD geladen: " + v.motd.split("\n").length + " Zeile(n), "
                + spriteNames.size() + " Sprite(s) definiert, Format '"
                + v.settings.text("format", "auto") + "', Sprites "
                + (v.sprites ? "ab Protokoll " + v.minProtocol : "aus")
                + (v.debug ? ", Debug an" : ""));
        if (v.motds.size() > 1) {
            platform.info("MOTD-Wechsel: " + v.motds.size() + " MOTDs, alle "
                    + v.settings.number("motd-wechsel.abstand", 300) + " Sekunden, Modus "
                    + v.settings.text("motd-wechsel.modus", "REIHE"));
        }

        for (int index = 0; index < v.motds.size(); index++) {
            checkOne(v, spriteNames, v.motds.get(index), v.motds.size() > 1 ? index + 1 : 0);
        }
        checkSprites(v, spriteNames);

        if (v.debug) {
            platform.info("Debug: gelesen aus " + file);
            for (int index = 0; index < v.motds.size(); index++) {
                String[] lines = v.motds.get(index).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    platform.info("Debug: MOTD " + (index + 1) + " Zeile " + (i + 1) + " roh = " + lines[i]);
                }
            }
        }
    }

    private void checkSprites(View v, List<String> spriteNames) {
        for (String name : spriteNames) {
            Sprite made = sprite(v, name);

            if (made.image().trim().isEmpty()) {
                platform.warn("Beim Sprite '" + name + "' fehlt der Schlüssel 'sprite' - der "
                        + "Client kann nichts zeichnen und zeigt den Fallback-Text.", null);
            }
            if (made.fallback().trim().isEmpty()) {
                platform.warn("Beim Sprite '" + name + "' fehlt 'fallback'. Clients, die das "
                        + "Sprite nicht zeichnen können, zeigen dort dann gar nichts.", null);
            }

            String raw = rawAtlas(v.settings, name);
            String full = raw.indexOf(':') < 0 ? "minecraft:" + raw : raw;
            if (!knownAtlas(full)) {
                String guess = atlasGuess(full);
                if (guess.isEmpty()) {
                    platform.warn("Das Atlas '" + raw + "' beim Sprite '" + name + "' kennt "
                            + "Minecraft so nicht - der Client findet das Bild dann nicht und "
                            + "zeigt den Fallback-Text. Gültig sind: "
                            + String.join(", ", ATLASES), null);
                } else {
                    platform.warn("Das Atlas '" + raw + "' beim Sprite '" + name + "' gibt es "
                            + "nicht, gemeint ist '" + guess + "' (Mehrzahl). Das Plugin "
                            + "benutzt automatisch '" + guess + "', trag es aber am besten "
                            + "richtig in die " + FILE + " ein.", null);
                }
            }

            if (v.debug) {
                platform.info("Debug: Sprite '" + name + "' -> " + made.describe());
            }
        }
    }

    private static boolean knownAtlas(String atlas) {
        for (String entry : ATLASES) {
            if (entry.equals(atlas)) {
                return true;
            }
        }
        return false;
    }

    private static String atlasGuess(String atlas) {
        for (String entry : ATLASES) {
            if (entry.equals(atlas + "s")) {
                return entry;
            }
        }
        return "";
    }

    private void checkOne(View v, List<String> spriteNames, String text, int number) {
        String label = number == 0 ? "Die MOTD" : "MOTD " + number;
        String[] lines = text.split("\n");

        if (lines.length > 2) {
            platform.warn(label + " hat " + lines.length + " Zeilen - Minecraft zeigt nur 2, "
                    + "der Rest wird verworfen.", null);
        }

        for (int i = 0; i < lines.length; i++) {
            int width = visibleLength(lines[i]);
            if (width > WIDTH) {
                platform.warn(label + ", Zeile " + (i + 1) + " ist etwa " + width
                        + " Zeichen breit - der Client schneidet ab rund " + WIDTH
                        + " Zeichen ab. Farbcodes und Tags zählen dabei nicht mit.", null);
            }
        }

        Matcher match = PLACEHOLDER.matcher(text);
        while (match.find()) {
            String name = match.group(1).toLowerCase(Locale.ROOT);
            if (name.equals("online") || name.equals("max") || name.equals("spieler")
                    || name.startsWith("timer_")) {
                continue;
            }
            if (!v.settings.has("sprites." + name)) {
                platform.warn("%" + name + "% steht in " + label + ", ist aber unter 'sprites' nicht "
                        + "definiert - es wird als reiner Text angezeigt. Definiert sind: "
                        + spriteNames, null);
            }
        }

        String open = openTag(text);
        if (open != null) {
            platform.warn("Der Tag <" + open + "> in " + label + " wird nie geschlossen - "
                    + "er färbt alles bis zum Ende der Zeile.", null);
        }
    }

    public PingData ping(int realOnline, int realMax, int protocol) {
        View v = view;

        int online = number(v, "spieler.online-modus", "spieler.online-wert", realOnline, realOnline);
        int max = number(v, "spieler.max-modus", "spieler.max-wert", realMax, online);

        boolean sprites = v.sprites && protocol >= v.minProtocol;
        List<Section> description = split(v, replace(v, pickMotd(v), online, max), sprites);

        boolean versionEnabled = v.settings.flag("version-anzeige.aktiv", false);
        String versionText = versionLine(v, online, max);
        int versionProtocol = v.settings.number("version-anzeige.protokoll", -1);
        List<String> sample = sampleLines(v, online, max);

        if (description.isEmpty()) {
            warnEmpty();
        } else if (v.debug && reported.add(protocol)) {
            platform.info("Debug: Ping mit Protokoll " + protocol + " -> "
                    + description.size() + " Abschnitt(e), Sprites "
                    + (sprites ? "an" : "aus, Fallback wird benutzt") + ", Aufbau " + kinds(description)
                    + ", Hover-Zeilen " + sample.size());
        }

        return new PingData(online, max, description, versionEnabled, versionText, versionProtocol, sample);
    }

    private String pickMotd(View v) {
        if (v.motds.size() <= 1) {
            return v.motd;
        }
        int interval = Math.max(1, v.settings.number("motd-wechsel.abstand", 300));
        long slot = System.currentTimeMillis() / 1000L / interval;
        String mode = v.settings.text("motd-wechsel.modus", "REIHE").toUpperCase(Locale.ROOT);
        if (mode.startsWith("ZUFALL")) {
            return v.motds.get(new Random(slot).nextInt(v.motds.size()));
        }
        return v.motds.get((int) Math.floorMod(slot, v.motds.size()));
    }

    private String versionLine(View v, int online, int max) {
        String text = flatten(v, spriteFallbacks(v,
                replace(v, v.settings.text("version-anzeige.text", "&2%online%&8/&2%max%"), online, max)));

        if (!v.settings.flag("version-anzeige.vor-icon.aktiv", false)) {
            return text;
        }
        String before = flatten(v, spriteFallbacks(v,
                replace(v, v.settings.text("version-anzeige.vor-icon.text", ""), online, max)));
        if (before.trim().isEmpty()) {
            return text;
        }

        int gap = v.settings.number("version-anzeige.vor-icon.abstand-vom-icon", 4);
        int padPixels = VERSION_RIGHT_EDGE + gap - pixelWidth(text);
        int spaces = Math.round(padPixels / (float) SPACE_WIDTH);
        spaces = Math.max(1, Math.min(MAX_PAD, spaces));
        StringBuilder out = new StringBuilder(before);
        for (int i = 0; i < spaces; i++) {
            out.append(' ');
        }
        return out.append(text).toString();
    }

    private List<String> sampleLines(View v, int online, int max) {
        if (!v.settings.flag("hover.aktiv", false)) {
            return Collections.emptyList();
        }
        List<String> raw = v.settings.textList("hover.zeilen");
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            out.add(flatten(v, spriteFallbacks(v, replace(v, line, online, max))));
        }
        return out;
    }

    private String playerNames(View v) {
        List<String> names = platform.playerNames();
        if (names.isEmpty()) {
            return v.settings.text("hover.niemand", "niemand");
        }
        int limit = Math.max(1, v.settings.number("hover.spieler-max", 10));
        String separator = v.settings.text("hover.spieler-trenner", "&7, &f");
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (String name : names) {
            if (shown >= limit) {
                out.append(v.settings.text("hover.spieler-mehr", "&7 und %rest% weitere")
                        .replace("%rest%", String.valueOf(names.size() - shown)));
                break;
            }
            if (shown > 0) {
                out.append(separator);
            }
            out.append(name);
            shown++;
        }
        return out.toString();
    }

    private String spriteFallbacks(View v, String text) {
        Matcher match = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        int position = 0;
        while (match.find()) {
            String name = match.group(1).toLowerCase(Locale.ROOT);
            if (!v.settings.has("sprites." + name)) {
                continue;
            }
            out.append(text, position, match.start());
            out.append(v.settings.text("sprites." + name + ".fallback", ""));
            position = match.end();
        }
        out.append(text.substring(position));
        return out.toString();
    }

    private long remaining(View v, String base) {
        String target = v.settings.text(base + ".ziel", "").trim();
        if (!target.isEmpty()) {
            return Countdown.secondsLeft(target);
        }
        long duration = Countdown.parseDuration(v.settings.text(base + ".dauer", ""));
        if (duration == Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        long gone = (System.currentTimeMillis() - startedAt) / 1000L;
        long left = duration - gone;
        return left < 0 ? 0 : left;
    }

    private String timers(View v, String text) {
        if (!v.settings.flag("timer.aktiv", false) || !text.contains("%timer_")) {
            return text;
        }
        String out = text;
        for (String name : v.settings.keys("timer.liste")) {
            String token = "%timer_" + name + "%";
            if (!out.contains(token)) {
                continue;
            }
            String base = "timer.liste." + name;
            long left = remaining(v, base);
            String value;
            if (left == Long.MIN_VALUE) {
                value = v.settings.text(base + ".fehler", "&c?");
                if (v.debug) {
                    platform.warn("Timer '" + name + "': weder 'ziel' noch 'dauer' ist gültig ("
                            + v.settings.text(base + ".ziel", "") + " / "
                            + v.settings.text(base + ".dauer", "")
                            + "). Erwartet wird JJJJ-MM-TT HH:MM bei ziel oder z.B. 2h30m bei dauer.", null);
                }
            } else if (left <= 0) {
                value = v.settings.text(base + ".vorbei", "");
            } else {
                value = Countdown.format(left,
                        v.settings.text(base + ".format", "%tage%T %stunden%h %minuten%m"));
            }
            out = out.replace(token, value);
        }
        return out;
    }

    private void warnEmpty() {
        long now = System.currentTimeMillis();
        if (now - lastWarning < WARN_PAUSE) {
            return;
        }
        lastWarning = now;
        platform.warn("Die MOTD wäre leer - das Plugin lässt sie deshalb unangetastet, "
                + "damit im Serverbrowser nicht nur eine leere Zeile steht. "
                + "Prüfe den Schlüssel 'motd' in der " + FILE + " und führe /motd aus.", null);
    }

    private static String kinds(List<Section> parts) {
        StringBuilder out = new StringBuilder();
        for (Section part : parts) {
            if (out.length() > 0) {
                out.append('+');
            }
            out.append(part.isSprite() ? "Sprite" : part.isJson() ? "Tags" : "Text");
        }
        return out.toString();
    }

    private static String shortText(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass().getSimpleName();
        }
        String single = message.replace('\n', ' ').replace('\r', ' ').trim();
        return single.length() > 240 ? single.substring(0, 240) + "..." : single;
    }

    static int visibleLength(String text) {
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 7 < text.length()) {
                    i += 8;
                    continue;
                }
                if (code == 'x' && i + 13 < text.length()) {
                    i += 14;
                    continue;
                }
                if ("0123456789abcdefklmnor".indexOf(code) >= 0) {
                    i += 2;
                    continue;
                }
            }
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    i = end + 1;
                    continue;
                }
            }
            if (c == '%') {
                int end = text.indexOf('%', i + 1);
                if (end > i) {
                    width += 2;
                    i = end + 1;
                    continue;
                }
            }
            width++;
            i++;
        }
        return width;
    }

    static int pixelWidth(String text) {
        int width = 0;
        boolean bold = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '&' || c == SECTION) && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 7 < text.length()) {
                    bold = false;
                    i += 8;
                    continue;
                }
                if (code == 'x' && i + 13 < text.length()) {
                    bold = false;
                    i += 14;
                    continue;
                }
                if ("0123456789abcdefklmnor".indexOf(code) >= 0) {
                    if (code == 'l') {
                        bold = true;
                    } else if ("kmno".indexOf(code) < 0) {
                        bold = false;
                    }
                    i += 2;
                    continue;
                }
            }
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    i = end + 1;
                    continue;
                }
            }
            width += charWidth(c) + (bold ? 1 : 0);
            i++;
        }
        return width;
    }

    private static int charWidth(char c) {
        if (c == ' ') {
            return SPACE_WIDTH;
        }
        if ("!',.:;i|".indexOf(c) >= 0) {
            return 2;
        }
        if (c == 'l') {
            return 3;
        }
        if ("\"*[]`".indexOf(c) >= 0) {
            return 4;
        }
        if ("Ifkt()<>{}".indexOf(c) >= 0) {
            return 5;
        }
        if (c == '@' || c == '~') {
            return 7;
        }
        return 6;
    }

    private static String openTag(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String name : new String[]{"gradient", "rainbow", "click"}) {
            if (count(lower, "<" + name) > count(lower, "</" + name + ">")) {
                return name;
            }
        }
        return null;
    }

    private static int count(String text, String part) {
        int match = 0;
        int i = text.indexOf(part);
        while (i >= 0) {
            match++;
            i = text.indexOf(part, i + 1);
        }
        return match;
    }

    private int number(View v, String modePath, String valuePath, int real, int base) {
        String mode = v.settings.text(modePath, "ECHT").toUpperCase(Locale.ROOT);
        int value = v.settings.number(valuePath, 0);
        if (mode.equals("FEST")) {
            return value;
        }
        if (mode.equals("ONLINE_PLUS")) {
            return base + value;
        }
        return real;
    }

    private String replace(View v, String text, int online, int max) {
        String out = text.replace("%online%", String.valueOf(online))
                .replace("%max%", String.valueOf(max));
        if (out.contains("%spieler%")) {
            out = out.replace("%spieler%", playerNames(v));
        }
        return timers(v, out);
    }

    private static List<String> buildMotdList(Config source, String first) {
        List<String> out = new ArrayList<>();
        if (!first.trim().isEmpty()) {
            out.add(first);
        }
        if (source.flag("motd-wechsel.aktiv", false)) {
            for (String entry : source.textList("motd-wechsel.liste")) {
                if (!entry.trim().isEmpty()) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    public List<Section> split(String text, boolean spritesAllowed) {
        return split(view, text, spritesAllowed);
    }

    private List<Section> split(View v, String text, boolean spritesAllowed) {
        List<Section> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Matcher match = PLACEHOLDER.matcher(text);
        int position = 0;

        while (match.find()) {
            String name = match.group(1).toLowerCase(Locale.ROOT);
            if (!v.settings.has("sprites." + name)) {
                continue;
            }
            buffer.append(text, position, match.start());
            if (spritesAllowed) {
                writeText(v, parts, buffer);
                parts.add(Section.sprite(sprite(v, name)));
            } else {
                buffer.append(v.settings.text("sprites." + name + ".fallback", ""));
            }
            position = match.end();
        }
        buffer.append(text.substring(position));
        writeText(v, parts, buffer);
        return parts;
    }

    private static String rawAtlas(Config settings, String name) {
        String raw = settings.text("sprites." + name + ".atlas", "minecraft:blocks").trim();
        return raw.isEmpty() ? "minecraft:blocks" : raw;
    }

    private static String atlasOf(Config settings, String name) {
        String raw = rawAtlas(settings, name);
        String full = raw.indexOf(':') < 0 ? "minecraft:" + raw : raw;
        if (knownAtlas(full)) {
            return full;
        }
        String guess = atlasGuess(full);
        return guess.isEmpty() ? full : guess;
    }

    private Sprite sprite(View v, String name) {
        Sprite made = v.spriteMap.get(name);
        return made != null ? made : View.build(v.settings, name);
    }

    private void writeText(View v, List<Section> parts, StringBuilder buffer) {
        if (buffer.length() == 0) {
            return;
        }
        String raw = buffer.toString();
        if (useMini(v, raw)) {
            List<MiniFormat.Run> runs = MiniFormat.split(raw);
            parts.add(Section.formatted(MiniFormat.toJson(runs), MiniFormat.toLegacy(runs)));
        } else {
            parts.add(Section.text(raw));
        }
        buffer.setLength(0);
    }

    private boolean useMini(View v, String text) {
        String mode = v.settings.text("format", "auto").toLowerCase(Locale.ROOT);
        if (mode.equals("legacy")) {
            return false;
        }
        if (mode.equals("minimessage")) {
            return true;
        }
        return MiniFormat.hasTags(text);
    }

    private String flatten(View v, String text) {
        return useMini(v, text) ? MiniFormat.toLegacy(MiniFormat.split(text)) : text;
    }

    public String commandName() {
        return view.settings.text("befehl.name", "motd");
    }

    public String commandAlias() {
        return view.settings.text("befehl.alias", "motdreload");
    }

    public String commandPermission() {
        return view.settings.text("befehl.rechte", "motd.admin");
    }

    public String message(String key, String fallback) {
        View v = view;
        return flatten(v, language.text("prefix", "") + language.text(key, fallback));
    }

    public String messageJson(String key, String fallback) {
        View v = view;
        String raw = language.text("prefix", "") + language.text(key, fallback);
        if (!useMini(v, raw)) {
            return null;
        }
        return MiniFormat.toJson(MiniFormat.split(raw));
    }

    public String updateMessageJson(String latest, String current) {
        String json = messageJson("update", "&eMOTD %new% ist verfügbar - installiert ist %current%.");
        if (json == null) {
            return null;
        }
        return json.replace("%new%", latest).replace("%current%", current);
    }

    public String updateMessage(String latest, String current) {
        return message("update", "&eMOTD %new% ist verfügbar - installiert ist %current%.")
                .replace("%new%", latest)
                .replace("%current%", current);
    }

    public boolean updateChecker() {
        return view.settings.flag("update-checker", true);
    }

    public boolean isReload(String label, String[] args) {
        if (label != null && label.equalsIgnoreCase(commandAlias())) {
            return true;
        }
        return args != null && args.length > 0 && args[0].equalsIgnoreCase("reload");
    }

    public boolean spritesEnabled() {
        return view.sprites;
    }

    public boolean debug() {
        return view.debug;
    }
}
