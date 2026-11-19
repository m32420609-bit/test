package com.novaxr;

public record SteamGame(String name, String appId, String installDir, String libraryPath) {
    public boolean isVrHint() {
        String n = name.toLowerCase();
        return n.contains("vr") || n.contains("virtual reality") || n.contains("beat saber")
                || n.contains("half-life: alyx") || n.contains("pavlov");
    }
}
