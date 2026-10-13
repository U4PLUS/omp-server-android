package com.dsh.openmpserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class OmpServerService extends Service {
    public static final String CHANNEL_ID = "omp_server_channel";
    public static final String ACTION_START = "com.dsh.openmpserver.START";
    public static final String ACTION_STOP = "com.dsh.openmpserver.STOP";
    public static final String ACTION_RESTART = "com.dsh.openmpserver.RESTART";
    public static volatile StringBuilder logBuffer = new StringBuilder();
    public static volatile boolean serverRunning = false;
    public static volatile long serverStartMillis = 0;
    public static volatile String lastError = "";
    public static int serverPort = 7777;
    private Process serverProc;
    private File logFile;
    private File serverDir;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "open.mp Server", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopServer();
            appendLog("Stopped by user.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RESTART.equals(action)) {
            stopServer();
            new Thread(this::startServer).start();
            return START_STICKY;
        }
        if (serverRunning && serverProc != null && serverProc.isAlive()) {
            appendLog("Server already running.");
            return START_STICKY;
        }
        startForeground(1, buildNotification("Starting..."));
        new Thread(this::startServer).start();
        return START_STICKY;
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("open.mp Server")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
    }

    private void startServer() {
        try {
            serverDir = new File(getFilesDir(), "server");
            serverDir.mkdirs();
            logFile = new File(getExternalFilesDir(null), "app_log.txt");
            appendLog("=== session " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " ===");
            appendLog("Data dir : " + serverDir.getAbsolutePath());
            appendLog("Log file : " + (logFile != null ? logFile.getAbsolutePath() : "(null)"));

            copyAssets("server", serverDir);
            mergeGamemodes();
            applyPort();

            File exe = new File(serverDir, "omp-server");
            appendLog("Launching omp-server...");
            if (!launch(exe, serverDir)) {
                lastError = "exec failed (see log)";
                appendLog("LAUNCH FAILED. Error strategies exhausted.");
                notifyLog("Launch failed - open log");
                return;
            }
            serverRunning = true;
            serverStartMillis = System.currentTimeMillis();
            appendLog("PID " + serverProc.pid() + " running. Port=" + serverPort);
            readStream(serverProc.getInputStream());
            int rc = serverProc.waitFor();
            serverRunning = false;
            appendLog("Server exited, code=" + rc);
            notifyLog("Server exited (" + rc + ")");
        } catch (Exception e) {
            lastError = e.toString();
            appendLog("FAILED: " + e);
            notifyLog("Failed: " + e);
        }
    }

    private boolean launch(File exe, File dir) {
        String p = exe.getAbsolutePath();
        appendLog("DIAG exists=" + exe.exists() + " size=" + exe.length() + " canExec=" + exe.canExecute());
        if (exec(p, dir)) return true;

        String chmodOut = sh("chmod 755 '" + p + "'");
        appendLog("DIAG chmod => '" + chmodOut + "' canExec=" + exe.canExecute());
        if (exec(p, dir)) return true;

        String lsOut = sh("ls -lZ '" + p + "'");
        appendLog("DIAG ls -lZ: " + lsOut);
        if (exec(p, dir)) return true;

        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", "exec '" + p + "'");
            pb.directory(dir);
            pb.redirectErrorStream(true);
            serverProc = pb.start();
            appendLog("Launched via sh -c exec");
            return true;
        } catch (IOException e) {
            appendLog("DIAG sh-exec failed: " + e.getMessage());
        }

        File c = new File(getCacheDir(), "omp-server");
        try {
            copyFile(exe, c);
            c.setExecutable(true, false);
            appendLog("DIAG retry from cache: " + c.getAbsolutePath() + " canExec=" + c.canExecute());
            if (exec(c.getAbsolutePath(), dir)) {
                appendLog("Running from cache dir");
                return true;
            }
        } catch (IOException e) {
            appendLog("DIAG cache copy: " + e.getMessage());
        }
        return false;
    }

    private boolean exec(String path, File dir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path);
            pb.directory(dir);
            pb.redirectErrorStream(true);
            serverProc = pb.start();
            appendLog("Launched: " + path);
            return true;
        } catch (IOException e) {
            appendLog("DIAG exec('" + path + "') -> " + e.getMessage());
            return false;
        }
    }

    private String sh(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", cmd});
            String s = readFully(p.getInputStream());
            p.waitFor(5, TimeUnit.SECONDS);
            return s.trim();
        } catch (Exception e) {
            return "sh-err:" + e;
        }
    }

    private String readFully(InputStream is) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream os = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private void stopServer() {
        serverRunning = false;
        if (serverProc != null) {
            serverProc.destroy();
            try { serverProc.waitFor(3000, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            if (serverProc.isAlive()) serverProc.destroyForcibly();
            serverProc = null;
        }
    }

    private void readStream(final InputStream is) {
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) appendLog(line);
            } catch (IOException ignored) {}
        }).start();
    }

    private void appendLog(String s) {
        synchronized (logBuffer) {
            if (logBuffer.length() > 60000) logBuffer.setLength(logBuffer.length() - 20000);
            logBuffer.append(s).append('\n');
        }
        if (logFile != null) {
            try (FileWriter w = new FileWriter(logFile, true)) {
                w.write(s);
                w.write('\n');
                w.flush();
            } catch (IOException ignored) {}
        }
        notifyLog("Running...");
    }

    private void notifyLog(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(1, buildNotification(text));
    }

    private void copyAssets(String path, File targetDir) throws IOException {
        AssetManager am = getAssets();
        String[] entries = am.list(path);
        if (entries == null) return;
        for (String e : entries) {
            File out = new File(targetDir, e);
            String child = path + "/" + e;
            try (InputStream in = am.open(child)) {
                try (FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            } catch (FileNotFoundException fnf) {
                appendLog("Extracting dir: " + child);
                out.mkdirs();
                copyAssets(child, out);
            }
        }
    }

    /** Merge user-provided gamemodes from external storage into the server dir. */
    private void mergeGamemodes() {
        try {
            File ext = getExternalFilesDir("gamemodes");
            if (ext == null || !ext.exists()) {
                appendLog("Gamemodes source not present: " + (ext == null ? "no ext dir" : ext.getAbsolutePath()));
                return;
            }
            File gm = new File(serverDir, "gamemodes");
            gm.mkdirs();
            File[] list = ext.listFiles();
            if (list != null) {
                int n = 0;
                for (File f : list) {
                    if (f.isFile()) {
                        copyFile(f, new File(gm, f.getName()));
                        n++;
                    }
                }
                appendLog("Gamemodes merged: " + n + " file(s) from " + ext.getAbsolutePath());
            }
        } catch (Exception e) {
            appendLog("mergeGamemodes: " + e);
        }
    }

    /** Read "port" from config.json onto the UI-facing field. */
    private void applyPort() {
        File cfg = new File(serverDir, "config.json");
        serverPort = 7777;
        if (cfg.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(cfg))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"port\"\\s*:\\s*(\\d+)").matcher(sb.toString());
                if (m.find()) serverPort = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        appendLog("Configured port: " + serverPort);
    }

    public static String getConfigJson() {
        return lastConfig;
    }
    private static String lastConfig = "";
    /** Save raw JSON over server/config.json (called from UI thread indirectly). */
    public static void setConfigJson(String json) {
        lastConfig = json;
    }

    /** Persist current config.json contents for the Config tab. */
    public void saveConfigFromUi(final String json) {
        new Thread(() -> {
            try {
                File cfg = new File(serverDir != null ? serverDir : new File(getFilesDir(), "server"), "config.json");
                try (FileWriter w = new FileWriter(cfg)) {
                    w.write(json);
                    w.flush();
                }
                appendLog("config.json saved (restart to apply)");
                serverPort = 7777;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"port\"\\s*:\\s*(\\d+)").matcher(json);
                if (m.find()) serverPort = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                appendLog("saveConfig: " + e);
            }
        }).start();
    }

    public File getServerDir() {
        return serverDir;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
