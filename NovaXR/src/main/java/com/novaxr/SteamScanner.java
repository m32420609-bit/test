package com.novaxr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

public class SteamScanner {
    private static final Pattern LIBRARY_PATH = Pattern.compile("\"path\"\\s*\"([^\"]+)\"");
    private static final Pattern APP_ID = Pattern.compile("\"appid\"\\s*\"(\\d+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern INSTALL_DIR = Pattern.compile("\"installdir\"\\s*\"((?:\\\\.|[^\"])*)\"");

    public List<SteamGame> scan() {
        Set<Path> libraries = findSteamLibraries();
        Map<String, SteamGame> games = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Path lib : libraries) {
            Path common = lib.resolve("steamapps");
            if (!Files.isDirectory(common)) continue;
            try (Stream<Path> files = Files.list(common)) {
                files.filter(p -> p.getFileName().toString().matches("appmanifest_\\d+\\.acf"))
                    .forEach(p -> parseManifest(p, lib).ifPresent(g -> games.put(g.appId(), g)));
            } catch (IOException ignored) {}
        }
        return new ArrayList<>(games.values());
    }

    private Set<Path> findSteamLibraries() {
        Set<Path> roots = new LinkedHashSet<>();
        String pf = System.getenv("ProgramFiles(x86)");
        if (pf != null) roots.add(Paths.get(pf, "Steam"));
        String pf2 = System.getenv("ProgramFiles");
        if (pf2 != null) roots.add(Paths.get(pf2, "Steam"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) roots.add(Paths.get(local, "Programs", "Steam"));
        for (Path drive : FileSystems.getDefault().getRootDirectories()) {
            roots.add(drive.resolve("Steam"));
            roots.add(drive.resolve("Games").resolve("Steam"));
            roots.add(drive.resolve("Program Files (x86)").resolve("Steam"));
        }
        Set<Path> libraries = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            libraries.add(root);
            Path vdf = root.resolve("steamapps").resolve("libraryfolders.vdf");
            if (Files.exists(vdf)) {
                try {
                    String content = Files.readString(vdf, StandardCharsets.UTF_8);
                    Matcher m = LIBRARY_PATH.matcher(content);
                    while (m.find()) {
                        String raw = m.group(1).replace("\\\\", "\\");
                        Path p = Paths.get(raw);
                        if (Files.isDirectory(p)) libraries.add(p);
                    }
                } catch (Exception ignored) {}
            }
        }
        return libraries;
    }

    private Optional<SteamGame> parseManifest(Path file, Path library) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String id = group(APP_ID, text), name = unescape(group(NAME, text));
            String dir = unescape(group(INSTALL_DIR, text));
            if (id.isBlank() || name.isBlank()) return Optional.empty();
            return Optional.of(new SteamGame(name, id, dir, library.toString()));
        } catch (IOException e) { return Optional.empty(); }
    }
    private String group(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.group(1) : ""; }
    private String unescape(String s) { return s.replace("\\\\", "\\").replace("\\\"", "\""); }

    public static void launch(SteamGame game) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String uri = "steam://run/" + game.appId();
        if (os.contains("win")) new ProcessBuilder("cmd", "/c", "start", "", uri).start();
        else if (os.contains("mac")) new ProcessBuilder("open", uri).start();
        else new ProcessBuilder("xdg-open", uri).start();
    }
}
