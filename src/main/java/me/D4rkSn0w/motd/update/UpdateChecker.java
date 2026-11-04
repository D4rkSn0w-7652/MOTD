package me.D4rkSn0w.motd.update;

import me.D4rkSn0w.motd.core.MotdCore;
import me.D4rkSn0w.motd.core.ServerPlatform;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class UpdateChecker {

    public static final String PERMISSION = "motd.update";

    private static final String SPIGOT = "https://api.spigotmc.org/legacy/update.php?resource=139865";
    private static final String MODRINTH = "https://api.modrinth.com/v2/project/spritemotd/version";
    private static final String HANGAR = "https://hangar.papermc.io/api/v1/projects/MOTD/latestrelease";
    private static final long FIRST_CHECK = 5;
    private static final long INTERVAL = TimeUnit.MINUTES.toSeconds(30);

    private final ServerPlatform platform;
    private final ScheduledExecutorService scheduler;

    private volatile String latestVersion;
    private volatile boolean errorReported;

    private UpdateChecker(ServerPlatform platform) {
        this.platform = platform;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "MOTD-Update");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static UpdateChecker start(ServerPlatform platform, MotdCore core) {
        if (!core.updateChecker()) {
            return null;
        }
        UpdateChecker checker = new UpdateChecker(platform);
        checker.scheduler.scheduleAtFixedRate(checker::check, FIRST_CHECK, INTERVAL, TimeUnit.SECONDS);
        return checker;
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    public boolean updateAvailable() {
        String latest = latestVersion;
        return latest != null && isNewer(latest, platform.pluginVersion());
    }

    public String latestVersion() {
        return latestVersion;
    }

    private void check() {
        StringBuilder error = new StringBuilder();
        String found = better(better(spigot(error), modrinth(error)), hangar(error));

        if (found == null) {
            if (!errorReported) {
                errorReported = true;
                String text = error.toString();
                if (text.contains("PKIX") || text.contains("certificate") || text.contains("SSL")) {
                    platform.info("Update-Prüfung fehlgeschlagen: Java vertraut den Zertifikaten nicht. "
                            + "Meist läuft auf dem Server eine TLS-Prüfung durch Antivirus oder Proxy. "
                            + "Die MOTD ist davon nicht betroffen. Details: " + text);
                } else {
                    platform.info("Update-Prüfung fehlgeschlagen, das Plugin läuft normal weiter. "
                            + "Details: " + text);
                }
            }
            return;
        }

        errorReported = false;
        boolean isNew = !found.equals(latestVersion);
        latestVersion = found;
        if (isNew && updateAvailable()) {
            platform.info("Update verfügbar: " + found
                    + " (installiert: " + platform.pluginVersion() + ")");
        }
    }

    private String spigot(StringBuilder error) {
        try {
            return plausible(clean(fetch(SPIGOT)));
        } catch (Throwable exception) {
            note(error, "SpigotMC", exception);
            return null;
        }
    }

    private String modrinth(StringBuilder error) {
        try {
            return plausible(fromList(fetch(MODRINTH), "version_number"));
        } catch (Throwable exception) {
            note(error, "Modrinth", exception);
            return null;
        }
    }

    private String hangar(StringBuilder error) {
        try {
            return plausible(clean(fetch(HANGAR)));
        } catch (Throwable exception) {
            note(error, "Hangar", exception);
            return null;
        }
    }

    private static String plausible(String version) {
        if (version == null) {
            return null;
        }
        String clean = version.trim();
        if (clean.isEmpty() || clean.length() > 32) {
            return null;
        }
        boolean digit = false;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c == '<' || c == '>' || c == '{' || c == ' ') {
                return null;
            }
        }
        return digit ? clean : null;
    }

    private static void note(StringBuilder error, String name, Throwable exception) {
        if (error.length() > 0) {
            error.append(", ");
        }
        error.append(name).append(": ").append(exception.getMessage());
    }

    private static String better(String left, String right) {
        if (left == null || left.isEmpty()) {
            return right == null || right.isEmpty() ? null : right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        return isNewer(right, left) ? right : left;
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("User-Agent", "MOTD/" + platform.pluginVersion());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                if (buffer.length() > 1_000_000) {
                    break;
                }
            }
            return buffer.toString().trim();
        } finally {
            connection.disconnect();
        }
    }

    private static Object read(String content) {
        LoaderOptions config = new LoaderOptions();
        config.setCodePointLimit(1_000_000);
        return new Yaml(new SafeConstructor(config)).load(content);
    }

    private static String fromList(String content, String feld) {
        Object parsed = read(content);
        if (parsed instanceof List) {
            List<?> entries = (List<?>) parsed;
            if (!entries.isEmpty() && entries.get(0) instanceof Map) {
                Object value = ((Map<?, ?>) entries.get(0)).get(feld);
                return value == null ? null : clean(String.valueOf(value));
            }
        }
        return null;
    }

    private static String clean(String version) {
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        return clean;
    }

    static boolean isNewer(String latest, String current) {
        int[] a = parts(latest);
        int[] b = parts(current);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    private static int[] parts(String version) {
        String[] raw = version.replaceAll("[^0-9.]", "").split("[.]");
        int[] numbers = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                numbers[i] = raw[i].isEmpty() ? 0 : Integer.parseInt(raw[i]);
            } catch (NumberFormatException error) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
