package me.D4rkSn0w.motd.core;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Language {

    public static final String FALLBACK_CODE = "en_US";

    private static final List<String> BUNDLED = Arrays.asList("de_DE", "en_US");
    private static final String FOLDER = "lang";

    private final ServerPlatform platform;

    private Config messages = Config.empty();
    private Config backup = Config.empty();
    private String code = FALLBACK_CODE;

    public Language(ServerPlatform platform) {
        this.platform = platform;
    }

    public String code() {
        return code;
    }

    public void load(String wanted) {
        Path folder = platform.dataFolder().resolve(FOLDER);
        try {
            Files.createDirectories(folder);
        } catch (Throwable error) {
            platform.warn("Der Ordner " + folder + " konnte nicht angelegt werden.", error);
        }

        for (String name : BUNDLED) {
            copyIfMissing(folder, name);
        }

        backup = read(folder, FALLBACK_CODE);

        String clean = wanted == null ? "" : wanted.trim();
        if (clean.isEmpty()) {
            clean = FALLBACK_CODE;
        }

        Config found = read(folder, clean);
        if (found == null) {
            platform.warn("Die Sprachdatei " + folder.resolve(clean + ".yml")
                    + " gibt es nicht. Es wird " + FALLBACK_CODE + " benutzt. Vorhanden: "
                    + available(folder), null);
            clean = FALLBACK_CODE;
            found = backup;
        }

        code = clean;
        messages = found == null ? Config.empty() : found;

        if (backup == null) {
            backup = Config.empty();
        }
        platform.info("Sprache geladen: " + code + " (" + folder + ")");
    }

    public String text(String key, String fallback) {
        Object value = messages.fetch(key);
        if (value != null) {
            return String.valueOf(value);
        }
        Object second = backup.fetch(key);
        if (second != null) {
            return String.valueOf(second);
        }
        return fallback;
    }

    private void copyIfMissing(Path folder, String name) {
        Path target = folder.resolve(name + ".yml");
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = platform.bundledResource(FOLDER + "/" + name + ".yml")) {
            if (in == null) {
                return;
            }
            Files.copy(in, target);
            platform.info("Sprachdatei angelegt: " + target);
        } catch (Throwable error) {
            platform.warn("Sprachdatei " + target + " konnte nicht angelegt werden.", error);
        }
    }

    private Config read(Path folder, String name) {
        Path file = folder.resolve(name + ".yml");
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Config.load(reader);
        } catch (Throwable error) {
            platform.warn("Sprachdatei " + file + " konnte nicht gelesen werden.", error);
            return null;
        }
    }

    private String available(Path folder) {
        StringBuilder out = new StringBuilder();
        try (java.util.stream.Stream<Path> stream = Files.list(folder)) {
            for (Path file : (Iterable<Path>) stream.sorted()::iterator) {
                String name = file.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(name.substring(0, name.length() - 4));
            }
        } catch (Throwable ignored) {
            return "keine";
        }
        return out.length() == 0 ? "keine" : out.toString();
    }
}
