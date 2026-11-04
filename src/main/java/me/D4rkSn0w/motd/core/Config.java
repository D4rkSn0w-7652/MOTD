package me.D4rkSn0w.motd.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Config {

    private final Map<String, Object> values;

    private Config(Map<String, Object> values) {
        this.values = values;
    }

    public static Config empty() {
        return new Config(new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public static Config load(Reader reader) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(4 * 1024 * 1024);
        options.setAllowDuplicateKeys(false);
        Object loaded = new Yaml(new SafeConstructor(options)).load(reader);
        return loaded instanceof Map
                ? new Config((Map<String, Object>) loaded)
                : empty();
    }

    public Object fetch(String path) {
        Object current = values;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    public boolean has(String path) {
        return fetch(path) != null;
    }

    public String text(String path, String fallback) {
        Object value = fetch(path);
        return value == null ? fallback : String.valueOf(value);
    }

    public int number(String path, int fallback) {
        Object value = fetch(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignoriert) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean flag(String path, boolean fallback) {
        Object value = fetch(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value).trim());
    }

    public List<String> textList(String path) {
        Object value = fetch(path);
        List<String> out = new ArrayList<>();
        if (value instanceof List) {
            for (Object entry : (List<?>) value) {
                if (entry != null) {
                    out.add(String.valueOf(entry));
                }
            }
        } else if (value != null) {
            String single = String.valueOf(value);
            if (!single.isEmpty()) {
                out.add(single);
            }
        }
        return out;
    }

    public List<String> keys(String path) {
        Object value = fetch(path);
        List<String> names = new ArrayList<>();
        if (value instanceof Map) {
            for (Object keys : ((Map<?, ?>) value).keySet()) {
                names.add(String.valueOf(keys));
            }
        }
        return names;
    }
}
