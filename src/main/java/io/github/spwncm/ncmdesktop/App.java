package io.github.spwncm.ncmdesktop;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** 程序入口：默认启动图形界面；--cli 走命令行模式。 */
public final class App {

    public static final String VERSION = "1.1.1";

    private App() {
    }

    public static void main(String[] args) {
        for (String a : args) {
            if ("--cli".equals(a)) {
                System.exit(Cli.run(args));
            }
            if ("--version".equals(a) || "-v".equals(a)) {
                System.out.println("NCM Helper " + VERSION);
                System.exit(0);
            }
            if ("--help".equals(a) || "-h".equals(a)) {
                System.out.println("""
                        网易云 NCM 自动处理工具

                        图形界面：  NCM Helper.exe
                        命令行：    NCM Helper.exe --cli [选项]

                        选项：
                          --source <目录>    下载目录（多个用 ; 分隔；默认自动识别）
                          --library <目录>   音乐库目录（默认 D:\\music）
                          --no-lyrics        不联网抓歌词
                          --no-move-audio    只处理 NCM，不移动已有 FLAC/MP3
                          --version          显示版本
                        """);
                System.exit(0);
            }
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
