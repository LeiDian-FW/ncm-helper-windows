package io.github.spwncm.ncmdesktop;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** 命令行模式：无界面执行一轮处理，便于脚本化与自动化测试。 */
public final class Cli {

    private Cli() {
    }

    public static int run(String[] args) {
        Settings s = Settings.load();
        String source = null;
        String library = null;
        boolean lyrics = true;
        boolean moveAudio = true;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--source" -> {
                    if (i + 1 < args.length) source = args[++i];
                }
                case "--library" -> {
                    if (i + 1 < args.length) library = args[++i];
                }
                case "--no-lyrics" -> lyrics = false;
                case "--no-move-audio" -> moveAudio = false;
                default -> {
                }
            }
        }
        if (source != null && !source.isBlank()) {
            s.sourceDirs = new ArrayList<>(List.of(source.split(";")));
        }
        if (library != null && !library.isBlank()) s.library = library;
        s.fetchLyrics = lyrics;
        s.moveAudio = moveAudio;
        if (s.library.isBlank()) s.library = "D:\\music";

        Log.init(Settings.dataDir().resolve("ncm-helper.log"));
        final int[] st = new int[4];
        Pipeline pipeline = new Pipeline(Settings.dataDir(),
                new NeteaseLyrics(Settings.dataDir().resolve("lyrics-cache")));
        pipeline.setOptions(lyrics, true);
        pipeline.setListener(new Pipeline.Listener() {
            @Override
            public void stats(int converted, int moved, int skipped, int failed) {
                st[0] = converted;
                st[1] = moved;
                st[2] = skipped;
                st[3] = failed;
            }
        });
        pipeline.apply(s.toJobConfig());
        System.out.println("NCM Helper " + App.VERSION + " CLI");
        System.out.println("下载目录: " + (s.sourceDirs.isEmpty()
                ? Pipeline.detectSourcesNow() : s.sourceDirs));
        System.out.println("音乐库  : " + Paths.get(s.library));
        try {
            pipeline.runOnce(true);
        } catch (Throwable e) {
            Log.error("处理失败: " + e);
            return 2;
        }
        System.out.printf("RESULT converted=%d moved=%d skipped=%d failed=%d%n",
                st[0], st[1], st[2], st[3]);
        return st[3] > 0 ? 1 : 0;
    }
}
