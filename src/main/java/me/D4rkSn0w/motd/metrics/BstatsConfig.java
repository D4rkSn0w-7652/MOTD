package me.D4rkSn0w.motd.metrics;

import me.D4rkSn0w.motd.core.ServerPlatform;
import org.bstats.config.MetricsConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class BstatsConfig {

    private static final List<String> HEADER = Arrays.asList(
            "# bStats (https://bStats.org) collects some basic information for plugin authors, like how",
            "# many people use their plugin and their total player count. It's recommended to keep bStats",
            "# enabled, but if you're not comfortable with this, you can turn this setting off. There is no",
            "# performance penalty associated with having metrics enabled, and data sent to bStats is fully",
            "# anonymous.",
            "# Learn more here: https://bstats.org/docs/server-owners");

    final boolean enabled;
    final String serverUuid;
    final boolean logErrors;
    final boolean logSentData;
    final boolean logResponseStatus;
    final Path path;

    private BstatsConfig(boolean enabled, String serverUuid, boolean logErrors,
                        boolean logSentData, boolean logResponseStatus, Path path) {
        this.enabled = enabled;
        this.serverUuid = serverUuid;
        this.logErrors = logErrors;
        this.logSentData = logSentData;
        this.logResponseStatus = logResponseStatus;
        this.path = path;
    }

    static BstatsConfig load(ServerPlatform platform) throws IOException {
        Path folder = platform.dataFolder().getParent().resolve("bStats");
        switch (platform.bstatsPlatform()) {
            case "velocity":
                return velocity(folder.resolve("config.txt"));
            case "sponge":
                return sponge(folder);
            default:
                return yaml(folder.resolve("config.yml"));
        }
    }

    private static BstatsConfig velocity(Path file) throws IOException {
        MetricsConfig config = new MetricsConfig(file.toFile(), true);
        return new BstatsConfig(config.isEnabled(), config.getServerUUID(),
                config.isLogErrorsEnabled(), config.isLogSentDataEnabled(),
                config.isLogResponseStatusTextEnabled(), file);
    }

    private static BstatsConfig yaml(Path file) throws IOException {
        if (!Files.exists(file)) {
            String uuid = UUID.randomUUID().toString();
            writeYaml(file, true, uuid, false, false, false);
            return new BstatsConfig(true, uuid, false, false, false, file);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String content = String.join("\n", lines);

        boolean enabled = true;
        String uuid = null;
        boolean error = false;
        boolean data = false;
        boolean response = false;

        Object parsed = null;
        try {
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (Throwable ignoriert) {
            parsed = null;
        }

        if (parsed instanceof Map) {
            Map<?, ?> values = (Map<?, ?>) parsed;
            enabled = wahr(values.get("enabled"), true);
            uuid = text(values.get("serverUuid"));
            error = wahr(values.get("logFailedRequests"), false);
            data = wahr(values.get("logSentData"), false);
            response = wahr(values.get("logResponseStatusText"), false);
        } else {
            enabled = wahr(lineValue(lines, "enabled"), true);
            uuid = text(lineValue(lines, "server-uuid"));
            error = wahr(lineValue(lines, "log-errors"), false);
            data = wahr(lineValue(lines, "log-sent-data"), false);
            response = wahr(lineValue(lines, "log-response-status-text"), false);
            if (uuid != null) {
                writeYaml(file, enabled, uuid, error, data, response);
            }
        }

        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            writeYaml(file, enabled, uuid, error, data, response);
        }
        return new BstatsConfig(enabled, uuid, error, data, response, file);
    }

    private static BstatsConfig sponge(Path folder) throws IOException {
        Path gemeinsam = folder.resolve("config.conf");
        String uuid = null;
        boolean error = false;
        boolean data = false;
        boolean response = false;

        if (Files.exists(gemeinsam)) {
            List<String> lines = Files.readAllLines(gemeinsam, StandardCharsets.UTF_8);
            uuid = text(hoconValue(lines, "serverUuid"));
            error = wahr(hoconValue(lines, "logFailedRequests"), false);
            data = wahr(hoconValue(lines, "logSentData"), false);
            response = wahr(hoconValue(lines, "logResponseStatusText"), false);
        }

        Path eigene = folder.resolve("motd-server-uuid.txt");
        if (uuid == null && Files.exists(eigene)) {
            uuid = text(new String(Files.readAllBytes(eigene), StandardCharsets.UTF_8).trim());
        }
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            Files.createDirectories(folder);
            Files.write(eigene, uuid.getBytes(StandardCharsets.UTF_8));
        }
        return new BstatsConfig(true, uuid, error, data, response, gemeinsam);
    }

    private static void writeYaml(Path file, boolean enabled, String uuid,
                                     boolean error, boolean data, boolean response) throws IOException {
        List<String> out = new ArrayList<>(HEADER);
        out.add("enabled: " + enabled);
        out.add("serverUuid: " + uuid);
        out.add("logFailedRequests: " + error);
        out.add("logSentData: " + data);
        out.add("logResponseStatusText: " + response);
        Files.createDirectories(file.getParent());
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    private static String lineValue(List<String> lines, String keys) {
        String start = keys + "=";
        for (String line : lines) {
            String clean = line.trim();
            if (clean.startsWith(start)) {
                return clean.substring(start.length()).trim();
            }
        }
        return null;
    }

    private static String hoconValue(List<String> lines, String keys) {
        for (String line : lines) {
            String clean = line.trim();
            if (!clean.startsWith(keys)) {
                continue;
            }
            String rest = clean.substring(keys.length()).trim();
            if (rest.startsWith("=") || rest.startsWith(":")) {
                String value = rest.substring(1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static boolean wahr(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        String lower = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (lower.equals("true")) {
            return true;
        }
        if (lower.equals("false")) {
            return false;
        }
        return fallback;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String clean = String.valueOf(value).trim();
        return clean.isEmpty() ? null : clean;
    }
}
