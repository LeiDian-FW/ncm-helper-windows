package io.github.spwncm.ncmdesktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** 日志：控制台 + 日志文件 + 可选的界面回调。 */
public final class Log {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static volatile Path file;
    private static volatile Consumer<String> sink;

    private Log() {
    }

    public static void init(Path logFile) {
        file = logFile;
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException ignored) {
        }
    }

    /** 界面回调：每条日志以 "[HH:mm:ss] [级别] 内容" 形式转发 */
    public static void setSink(Consumer<String> s) {
        sink = s;
    }

    public static void info(String msg) {
        write("INFO", msg);
    }

    public static void warn(String msg) {
        write("WARN", msg);
    }

    public static void error(String msg) {
        write("ERROR", msg);
    }

    private static void write(String level, String msg) {
        String time = LocalDateTime.now().format(FMT);
        String line = "[" + time + "] [" + level + "] " + msg;
        System.out.println(line);
        Consumer<String> s = sink;
        if (s != null) {
            try {
                s.accept(line);
            } catch (Throwable ignored) {
            }
        }
        Path f = file;
        if (f == null) return;
        try {
            Files.writeString(f, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
