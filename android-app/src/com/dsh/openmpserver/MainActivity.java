package com.dsh.openmpserver;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private LinearLayout pageStatus, pageLog, pageConfig, pageFiles;
    private TextView statusText, logView, fileListText;
    private EditText configEdit;
    private File serverDir;
    private File logFile;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshStatus();
            if (pageLog.getVisibility() == View.VISIBLE) {
                synchronized (OmpServerService.logBuffer) {
                    logView.setText(OmpServerService.logBuffer.toString());
                }
            }
            handler.postDelayed(this, 800);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        serverDir = new File(getFilesDir(), "server");
        logFile = new File(getExternalFilesDir(null), "app_log.txt");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ---- tab bar ----
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        String[] tabs = {"Status", "Log", "Config", "Files"};
        for (String t : tabs) {
            Button b = new Button(this);
            b.setText(t);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            b.setOnClickListener(v -> showTab(t));
            tabBar.addView(b);
        }
        root.addView(tabBar);

        // ---- content ----
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);

        buildStatusPage();
        buildLogPage();
        buildConfigPage();
        buildFilesPage();

        setContentView(root);
        showTab("Status");
    }

    private LinearLayout newPage() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(12, 12, 12, 12);
        return p;
    }

    private void buildStatusPage() {
        pageStatus = newPage();
        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextIsSelectable(true);
        pageStatus.addView(statusText);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this);
        start.setText("Start");
        start.setOnClickListener(v -> startService(new Intent(this, OmpServerService.class).setAction(OmpServerService.ACTION_START)));
        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setOnClickListener(v -> startService(new Intent(this, OmpServerService.class).setAction(OmpServerService.ACTION_STOP)));
        Button restart = new Button(this);
        restart.setText("Restart");
        restart.setOnClickListener(v -> startService(new Intent(this, OmpServerService.class).setAction(OmpServerService.ACTION_RESTART)));
        row.addView(start); row.addView(stop); row.addView(restart);
        pageStatus.addView(row);
        content.addView(pageStatus);
    }

    private void buildLogPage() {
        pageLog = newPage();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = new Button(this);
        clear.setText("Clear");
        clear.setOnClickListener(v -> { synchronized (OmpServerService.logBuffer) { OmpServerService.logBuffer.setLength(0); } });
        Button copy = new Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(v -> {
            String s;
            synchronized (OmpServerService.logBuffer) { s = OmpServerService.logBuffer.toString(); }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("omp log", s));
        });
        row.addView(clear); row.addView(copy);
        pageLog.addView(row);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(logView);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        pageLog.addView(sv);
        content.addView(pageLog);
    }

    private void buildConfigPage() {
        pageConfig = newPage();
        TextView hint = new TextView(this);
        hint.setText("Edit config.json (raw JSON). Save writes to server dir; restart server to apply.\n\nKeys: host, port, max_players, password, rcon { enable, password }, lan_mode, announce.\nExample port change: \"port\": 7777");
        hint.setTextSize(12);
        pageConfig.addView(hint);

        configEdit = new EditText(this);
        configEdit.setTextSize(12);
        configEdit.setGravity(Gravity.TOP | Gravity.START);
        configEdit.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        pageConfig.addView(configEdit);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button load = new Button(this);
        load.setText("Load");
        load.setOnClickListener(v -> {
            String json = readConfig();
            configEdit.setText(json != null ? json : "{ }");
        });
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            startService(new Intent(this, OmpServerService.class).setAction(OmpServerService.ACTION_START)); // ensure service
            OmpServerService.setConfigJson(configEdit.getText().toString());
            new Thread(() -> {
                try {
                    serverDir.mkdirs();
                    File cfg = new File(serverDir, "config.json");
                    try (FileWriter w = new FileWriter(cfg)) { w.write(configEdit.getText().toString()); w.flush(); }
                } catch (Exception e) { /* ignore */ }
            }).start();
        });
        row.addView(load); row.addView(save);
        pageConfig.addView(row);
        content.addView(pageConfig);
    }

    private void buildFilesPage() {
        pageFiles = newPage();
        TextView hint = new TextView(this);
        hint.setText("Server directory contents. To add gamemodes without repacking:\nput .amx files into\n" + new File(getExternalFilesDir("gamemodes"), "").getAbsolutePath() + "\nthen press Refresh & Start.");
        hint.setTextSize(12);
        pageFiles.addView(hint);
        Button refresh = new Button(this);
        refresh.setText("Refresh List");
        refresh.setOnClickListener(v -> listFiles());
        pageFiles.addView(refresh);
        fileListText = new TextView(this);
        fileListText.setTextSize(11);
        fileListText.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(fileListText);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        pageFiles.addView(sv);
        content.addView(pageFiles);
    }

    private void showTab(String t) {
        pageStatus.setVisibility(t.equals("Status") ? View.VISIBLE : View.GONE);
        pageLog.setVisibility(t.equals("Log") ? View.VISIBLE : View.GONE);
        pageConfig.setVisibility(t.equals("Config") ? View.VISIBLE : View.GONE);
        pageFiles.setVisibility(t.equals("Files") ? View.VISIBLE : View.GONE);
        if (t.equals("Config")) configEdit.setText(readConfig() != null ? readConfig() : "{ }");
        if (t.equals("Files")) listFiles();
        if (t.equals("Log")) {
            synchronized (OmpServerService.logBuffer) { logView.setText(OmpServerService.logBuffer.toString()); }
        }
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        if (OmpServerService.serverRunning) {
            long secs = (System.currentTimeMillis() - OmpServerService.serverStartMillis) / 1000;
            sb.append("STATUS: RUNNING\n");
            sb.append("Port    : ").append(OmpServerService.serverPort).append("\n");
            sb.append("Uptime  : ").append(formatUptime(secs)).append("\n");
        } else {
            sb.append("STATUS: STOPPED\n");
            if (OmpServerService.lastError != null && !OmpServerService.lastError.isEmpty()) {
                sb.append("Last error: ").append(OmpServerService.lastError).append("\n");
            }
        }
        sb.append("\nData dir : ").append(serverDir.getAbsolutePath()).append("\n");
        sb.append("Log file : ").append(logFile != null ? logFile.getAbsolutePath() : "?").append("\n");
        sb.append("Log size : ").append(OmpServerService.logBuffer.length()).append(" chars\n");
        statusText.setText(sb.toString());
    }

    private String formatUptime(long s) {
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private String readConfig() {
        File cfg = new File(serverDir, "config.json");
        if (!cfg.exists()) return null;
        try (BufferedReader r = new BufferedReader(new FileReader(cfg))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private void listFiles() {
        StringBuilder sb = new StringBuilder();
        collect(serverDir, "", sb);
        fileListText.setText(sb.length() > 0 ? sb.toString() : "(empty; start server once to extract assets)");
    }

    private void collect(File dir, String indent, StringBuilder sb) {
        File[] files = dir.listFiles();
        if (files == null) { sb.append(indent).append(dir.getName()).append(" (?)").append('\n'); return; }
        for (File f : files) {
            if (f.isDirectory()) {
                sb.append(indent).append("[").append(f.getName()).append("/]").append('\n');
                collect(f, indent + "  ", sb);
            } else {
                sb.append(indent).append(f.getName()).append("  ").append(f.length()).append(" B").append('\n');
            }
        }
    }

    @Override
    protected void onResume() { super.onResume(); handler.post(tick); }

    @Override
    protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }
}
