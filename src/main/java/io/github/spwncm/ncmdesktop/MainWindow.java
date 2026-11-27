package io.github.spwncm.ncmdesktop;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** 主窗口。 */
public final class MainWindow extends JFrame {

    private final JTextField srcField = new JTextField();
    private final JTextField libField = new JTextField();
    private final JCheckBox watchBox = new JCheckBox("自动监视", true);
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(60, 10, 3600, 10));
    private final JCheckBox moveAudioBox = new JCheckBox("同时归档下载目录里的 FLAC/MP3", true);
    private final JCheckBox fetchLyricsBox = new JCheckBox("转换后补歌词", true);
    private final JCheckBox onlineBox = new JCheckBox("在线歌词", true);
    private final JCheckBox writeLrcBox = new JCheckBox("保存 .lrc", true);

    private final JButton detectBtn = new JButton("自动识别");
    private final JButton runBtn = new JButton("立即处理一次");
    private final JButton watchBtn = new JButton("开始监视");
    private final JButton historyBtn = new JButton("转换记录");
    private final JTextArea logArea = new JTextArea();
    private final JLabel status = new JLabel("就绪");

    private Pipeline pipeline;
    private History history;
    private boolean oneShotBusy;
    private boolean watching;

    public MainWindow() {
        super("NCM 助手 · Windows 版 v" + App.VERSION);
        Settings s = Settings.load();
        applySettings(s);
        history = new History(Settings.dataDir(), s.historyDays);

        Log.setSink(line -> SwingUtilities.invokeLater(() -> appendLog(line)));
        Log.init(Settings.dataDir().resolve("ncm-helper.log"));

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        buildUi();
        pack();
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);

        appendLog("NCM 助手 v" + App.VERSION + " 已启动");
        appendLog("配置目录: " + Settings.dataDir());
        if (srcField.getText().isBlank()) {
            autoDetectAsync();
        } else {
            appendLog("当前下载目录: " + srcField.getText());
        }
    }

    // ---------------- 界面 ----------------
    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("下载目录"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(srcField, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 2;
        form.add(detectBtn, c);
        c.gridx = 3;
        form.add(button("浏览…", e -> browseInto(srcField)), c);

        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("音乐库  "), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(libField, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 2;
        form.add(button("浏览…", e -> browseInto(libField)), c);
        c.gridx = 3;
        form.add(button("数据目录", e -> openDataDir()), c);

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.X_AXIS));
        options.add(watchBox);
        options.add(Box.createHorizontalStrut(8));
        options.add(new JLabel("间隔"));
        options.add(Box.createHorizontalStrut(4));
        intervalSpinner.setMaximumSize(new Dimension(70, 28));
        options.add(intervalSpinner);
        options.add(Box.createHorizontalStrut(4));
        options.add(new JLabel("秒"));
        options.add(Box.createHorizontalStrut(16));
        options.add(moveAudioBox);
        options.add(Box.createHorizontalStrut(16));
        options.add(fetchLyricsBox);
        options.add(Box.createHorizontalStrut(8));
        options.add(onlineBox);
        options.add(Box.createHorizontalStrut(8));
        options.add(writeLrcBox);
        options.add(Box.createHorizontalGlue());

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 4;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(options, c);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        runBtn.setPreferredSize(new Dimension(140, 32));
        watchBtn.setPreferredSize(new Dimension(120, 32));
        actions.add(runBtn);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(watchBtn);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(historyBtn);
        actions.add(Box.createHorizontalStrut(16));
        actions.add(new JLabel("提示：转换结果按 歌手/专辑 归档；源 NCM 校验通过后才会删除"));
        actions.add(Box.createHorizontalGlue());
        c.gridy = 3;
        form.add(actions, c);

        content.add(form, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("运行日志"));
        content.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.WEST);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);

        detectBtn.addActionListener(e -> autoDetectAsync());
        runBtn.addActionListener(e -> runOnce());
        watchBtn.addActionListener(e -> toggleWatch());
        historyBtn.addActionListener(e -> showHistoryDialog());
    }

    private static JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    // ---------------- 动作 ----------------
    private void browseInto(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择目录");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void openDataDir() {
        try {
            java.awt.Desktop.getDesktop().open(Settings.dataDir().toFile());
        } catch (IOException | UnsupportedOperationException e) {
            appendLog("无法打开数据目录: " + e.getMessage());
        }
    }

    private void autoDetectAsync() {
        detectBtn.setEnabled(false);
        new SwingWorker<List<Path>, Void>() {
            @Override
            protected List<Path> doInBackground() {
                return Pipeline.detectSourcesNow();
            }

            @Override
            protected void done() {
                detectBtn.setEnabled(true);
                try {
                    List<Path> dirs = get();
                    if (dirs.isEmpty()) {
                        appendLog("未自动识别到下载目录，请手动选择或填写");
                        return;
                    }
                    List<String> texts = new ArrayList<>();
                    for (Path p : dirs) texts.add(p.toString());
                    srcField.setText(String.join(";", texts));
                    appendLog("识别到下载目录: " + srcField.getText());
                } catch (Exception e) {
                    appendLog("识别失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    private Settings collectSettings() {
        Settings s = new Settings();
        s.sourceDirs = new ArrayList<>();
        for (String part : srcField.getText().split(";")) {
            String p = part.strip();
            if (!p.isEmpty()) s.sourceDirs.add(p);
        }
        s.library = libField.getText().strip();
        s.autoWatch = watchBox.isSelected();
        s.interval = (Integer) intervalSpinner.getValue();
        s.moveAudio = moveAudioBox.isSelected();
        s.fetchLyrics = fetchLyricsBox.isSelected();
        s.lyricsOnline = onlineBox.isSelected();
        s.writeLrc = writeLrcBox.isSelected();
        return s;
    }

    private void applySettings(Settings s) {
        srcField.setText(String.join(";", s.sourceDirs));
        libField.setText(s.library);
        watchBox.setSelected(s.autoWatch);
        intervalSpinner.setValue(Math.max(10, Math.min(3600, s.interval)));
        moveAudioBox.setSelected(s.moveAudio);
        fetchLyricsBox.setSelected(s.fetchLyrics);
        onlineBox.setSelected(s.lyricsOnline);
        writeLrcBox.setSelected(s.writeLrc);
    }

    private Pipeline buildPipeline(Settings s, boolean watchMode) {
        Pipeline p = new Pipeline(Settings.dataDir(),
                new NeteaseLyrics(Settings.dataDir().resolve("lyrics-cache")));
        p.setOptions(s.lyricsOnline, s.writeLrc);
        p.setListener(new Pipeline.Listener() {
            @Override
            public void stats(int converted, int moved, int skipped, int failed) {
                SwingUtilities.invokeLater(() -> status.setText(
                        "已转换 " + converted + " · 已归档 " + moved
                                + " · 跳过 " + skipped + " · 失败 " + failed));
            }

            @Override
            public void onRecord(History.Record record) {
                if (history != null) history.add(record);
            }

            @Override
            public void finished() {
                SwingUtilities.invokeLater(() -> {
                    if (!watchMode) {
                        oneShotBusy = false;
                        runBtn.setEnabled(true);
                        status.setText("处理完成");
                    }
                });
            }
        });
        p.apply(s.toJobConfig());
        return p;
    }

    private void runOnce() {
        if (oneShotBusy) return;
        Settings s = collectSettings();
        s.save();
        appendLog("—— 开始处理（" + (s.sourceDirs.isEmpty() ? "自动识别下载目录" : s.sourceDirs) + "）——");
        oneShotBusy = true;
        runBtn.setEnabled(false);
        status.setText("正在处理…");
        Pipeline p = buildPipeline(s, false);
        Thread t = new Thread(() -> {
            try {
                p.runOnce(true);
            } catch (Throwable e) {
                Log.error("处理异常: " + e);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    oneShotBusy = false;
                    runBtn.setEnabled(true);
                    status.setText("处理完成");
                });
            }
        }, "ncm-one-shot");
        t.setDaemon(true);
        t.start();
    }

    private void toggleWatch() {
        if (watching) {
            stopWatch();
            return;
        }
        Settings s = collectSettings();
        watchBox.setSelected(true);
        s.autoWatch = true;
        s.save();
        if (pipeline != null) pipeline.stop();
        pipeline = buildPipeline(s, true);
        pipeline.start();
        watching = true;
        watchBtn.setText("停止监视");
        appendLog("已开始自动监视，间隔 " + s.interval + " 秒");
        status.setText("监视中…");
    }

    private void stopWatch() {
        if (pipeline != null) pipeline.stop();
        watching = false;
        watchBtn.setText("开始监视");
        Settings s = collectSettings();
        s.autoWatch = false;
        s.save();
        appendLog("已停止自动监视");
        status.setText("就绪");
    }

    private void showHistoryDialog() {
        JDialog dlg = new JDialog(this, "转换记录", true);
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"时间", "状态", "歌曲", "歌手", "目标文件"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(46);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(430);
        JScrollPane scroll = new JScrollPane(table);

        JLabel info = new JLabel();
        Runnable refresh = () -> {
            model.setRowCount(0);
            for (History.Record r : history.recent(500)) {
                model.addRow(new Object[]{r.timeText(), r.status, r.title, r.artist, r.dest});
            }
            info.setText("共 " + history.count() + " 条（最多显示最近 500 条）");
        };
        refresh.run();

        JSpinner daysSpinner = new JSpinner(
                new SpinnerNumberModel(history.retainDays(), 1, 3650, 1));
        daysSpinner.addChangeListener(e -> {
            int days = (Integer) daysSpinner.getValue();
            Settings st = collectSettings();
            st.historyDays = days;
            st.save();
            history.setRetainDays(days);
            refresh.run();
        });
        JPanel top = new JPanel();
        top.add(new JLabel("保留最近"));
        top.add(daysSpinner);
        top.add(new JLabel("天，超期记录自动删除"));

        JButton refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> refresh.run());
        JButton clearBtn = new JButton("清空记录");
        clearBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dlg, "确定清空全部转换记录？", "确认",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                history.clear();
                refresh.run();
            }
        });
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dlg.dispose());

        JPanel buttons = new JPanel();
        buttons.add(refreshBtn);
        buttons.add(clearBtn);
        buttons.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(info, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(content);
        dlg.setSize(1040, 540);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void shutdown() {
        try {
            if (pipeline != null) pipeline.stop();
            collectSettings().save();
            if (history != null) history.purge();
        } catch (Exception ignored) {
        }
        dispose();
        System.exit(0);
    }
}
