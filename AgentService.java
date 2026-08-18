package com.importech.tvonline;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class AgentService extends Service {
    private volatile boolean running = true;
    private Thread worker;
    private static final String CHANNEL_ID = "importech_agent";

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("IMPORTECH TV ONLINE")
         .setContentText("Agente remoto ativo")
         .setSmallIcon(android.R.drawable.stat_notify_sync)
         .setOngoing(true);
        startForeground(101, b.build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop, "ImportechAgent");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            HttpURLConnection con = null;
            try {
                String code = DeviceId.get(this);
                String topic = fetchTopic(code);
                if (topic == null || topic.isEmpty()) throw new IOException("sem tópico");

                URL url = new URL("https://ntfy.sh/" + topic + "/sse");
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(0);
                con.setRequestProperty("Accept", "text/event-stream");
                con.connect();

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        con.getInputStream(), StandardCharsets.UTF_8));

                String line;
                while (running && (line = br.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        try {
                            JSONObject obj = new JSONObject(data);
                            if ("message".equals(obj.optString("event"))) {
                                handleCommand(obj.optString("message", ""));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                sleep(5000);
            } finally {
                if (con != null) con.disconnect();
            }
        }
    }

    private String fetchTopic(String code) throws Exception {
        URL url = new URL("https://importech-tv-online.vercel.app/api/channel?code="
                + URLEncoder.encode(code, "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            return obj.optString("topic", "");
        } finally {
            c.disconnect();
        }
    }

    private void handleCommand(String msg) {
        if ("PING".equals(msg)) {
            sendStatus("✅ TESTE RECEBIDO DO PAINEL ADM");
            return;
        }

        if (msg.startsWith("PAIR|")) {
            sendStatus("✅ BOX VINCULADA — " + msg.substring(5));
            return;
        }

        if (msg.startsWith("NOTICE|")) {
            String text = msg.substring(7);
            sendStatus("📢 " + text);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show());
            return;
        }

        if (msg.startsWith("INSTALL|")) {
            String apkUrl = msg.substring(8).trim();
            if (!apkUrl.startsWith("https://")) {
                sendStatus("❌ Link do APK inválido");
                return;
            }
            sendStatus("⬇️ Baixando APK...");
            try {
                File apk = downloadApk(apkUrl);
                sendStatus("✅ APK baixado. Preparando instalação...");
                requestInstall(apk);
            } catch (Exception e) {
                sendStatus("❌ Falha ao baixar/instalar APK: " + e.getMessage());
            }
        }
    }

    private File downloadApk(String apkUrl) throws Exception {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = getFilesDir();
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, "importech-remoto.apk");
        HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "IMPORTECH-TV-ONLINE/2.0");
        c.connect();

        int status = c.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status);
        }

        try (InputStream in = c.getInputStream();
             OutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }

        if (file.length() < 1024) throw new IOException("arquivo APK muito pequeno");
        return file;
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= 26 &&
                !getPackageManager().canRequestPackageInstalls()) {

            sendStatus("⚠️ Autorize 'Instalar apps desconhecidos' para IMPORTECH TV ONLINE");

            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(settings);
            } catch (Exception e) {
                sendStatus("❌ Não consegui abrir a permissão de instalação");
                return;
            }

            new Thread(() -> {
                for (int i = 0; i < 60; i++) {
                    sleep(1000);
                    if (getPackageManager().canRequestPackageInstalls()) {
                        installFile(apk);
                        return;
                    }
                }
                sendStatus("⚠️ Permissão não liberada. Envie o comando novamente.");
            }).start();
            return;
        }

        installFile(apk);
    }

    private void installFile(File apk) {
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        apk);
            } else {
                uri = Uri.fromFile(apk);
            }

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            sendStatus("📦 Instalador aberto na TV. Confirme INSTALAR.");
        } catch (Exception e) {
            sendStatus("❌ Não consegui abrir o instalador: " + e.getMessage());
        }
    }

    private void sendStatus(String display) {
        Intent i = new Intent("com.importech.tvonline.COMMAND");
        i.setPackage(getPackageName());
        i.putExtra("message", display);
        sendBroadcast(i);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "IMPORTECH TV ONLINE",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Mantém a comunicação remota da TV Box ativa");
            ch.enableLights(false);
            ch.setLightColor(Color.GREEN);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    @Override public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
