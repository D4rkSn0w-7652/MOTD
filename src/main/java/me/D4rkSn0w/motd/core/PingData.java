package me.D4rkSn0w.motd.core;

import java.util.List;

public final class PingData {

    private final int online;
    private final int max;
    private final List<Section> description;
    private final boolean versionEnabled;
    private final String versionText;
    private final int versionProtocol;
    private final List<String> sample;

    public PingData(int online, int max, List<Section> description,
                    boolean versionEnabled, String versionText, int versionProtocol,
                    List<String> sample) {
        this.online = online;
        this.max = max;
        this.description = description;
        this.versionEnabled = versionEnabled;
        this.versionText = versionText;
        this.versionProtocol = versionProtocol;
        this.sample = sample;
    }

    public int online() {
        return online;
    }

    public int max() {
        return max;
    }

    public List<Section> description() {
        return description;
    }

    public boolean hasDescription() {
        return !description.isEmpty();
    }

    public boolean versionEnabled() {
        return versionEnabled;
    }

    public String versionText() {
        return versionText;
    }

    public int versionProtocol() {
        return versionProtocol;
    }

    public List<String> sample() {
        return sample;
    }

    public boolean hasSample() {
        return !sample.isEmpty();
    }
}
