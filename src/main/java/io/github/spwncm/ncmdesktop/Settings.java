package io.github.spwncm.ncmdesktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 应用配置：%APPDATA%\NCMHelper\config.json */
public final class Settings {

    public List<String> sourceDirs = new ArrayList<>();
    public String library = "D:\\music";
    public boolean autoWatch = false;
    public int interval = 60;
    public boolean moveAudio = true;
    public boolean fetchLyrics = true;
    public boolean lyricsOnline = true;
    public boolean writeLrc = true;
    public int historyDays = 30;

    public static Path dataDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home", ".") + "\\AppData\\Roaming";
        }
        return Paths.get(appData, "NCMHelper");
    }

    public static Path configFile() {
        return dataDir().resolve("config.json");
    }

    public static Settings load() {
        Settings s = new Settings();
        Path f = configFile();
        if (!Files.isRegularFile(f)) return s;
        try {
            Map<String, Object> m = Json.asMap(Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
            s.library = Json.str(m, "library", s.library);
            for (Object o : Json.asList(m.get("sourceDirs"))) {
                String v = String.valueOf(o).strip();
                if (!v.isEmpty()) s.sourceDirs.add(v);
            }
            s.autoWatch = bool(m, "autoWatch", s.autoWatch);
            s.interval = (int) Json.lng(m, "interval", s.interval);
            s.moveAudio = bool(m, "moveAudio", s.moveAudio);
            s.fetchLyrics = bool(m, "fetchLyrics", s.fetchLyrics);
            s.lyricsOnline = bool(m, "lyricsOnline", s.lyricsOnline);
            s.writeLrc = bool(m, "writeLrc", s.writeLrc);
            s.historyDays = (int) Json.lng(m, "historyDays", s.historyDays);
        } catch (Exception ignored) {
        }
        return s;
    }

    public void save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("library", library);
        m.put("sourceDirs", sourceDirs);
        m.put("autoWatch", autoWatch);
        m.put("interval", interval);
        m.put("moveAudio", moveAudio);
        m.put("fetchLyrics", fetchLyrics);
        m.put("lyricsOnline", lyricsOnline);
        m.put("writeLrc", writeLrc);
        m.put("historyDays", historyDays);
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v instanceof String str) {
                sb.append('"').append(str.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else if (v instanceof List<?> list) {
                sb.append('[');
                for (int k = 0; k < list.size(); k++) {
                    if (k > 0) sb.append(", ");
                    sb.append('"').append(String.valueOf(list.get(k)).replace("\\", "\\\\")).append('"');
                }
                sb.append(']');
            } else {
                sb.append(v);
            }
            sb.append(++i < m.size() ? ",\n" : "\n");
        }
        sb.append("}\n");
        try {
            Files.createDirectories(dataDir());
            Files.writeString(configFile(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public Pipeline.JobConfig toJobConfig() {
        List<Path> dirs = new ArrayList<>();
        for (String s : sourceDirs) {
            for (String part : s.split(";")) {
                String p = part.strip();
                if (!p.isEmpty()) dirs.add(Paths.get(p));
            }
        }
        Path lib = Paths.get(library == null || library.isBlank() ? "D:\\music" : library);
        return Pipeline.JobConfig.of(lib, autoWatch, dirs, interval, moveAudio, fetchLyrics);
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }
}
