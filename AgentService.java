package com.importech.tvonline;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.widget.Toast;
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
        String display = null;

        if ("PING".equals(msg)) {
            display = "✅ TESTE RECEBIDO DO PAINEL ADM";
        } else if (msg.startsWith("PAIR|")) {
            display = "✅ BOX VINCULADA — " + msg.substring(5);
        } else if (msg.startsWith("NOTICE|")) {
            display = "📢 " + msg.substring(7);
            final String toastText = msg.substring(7);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(this, toastText, Toast.LENGTH_LONG).show());
        }

        if (display != null) {
            Intent i = new Intent("com.importech.tvonline.COMMAND");
            i.setPackage(getPackageName());
            i.putExtra("message", display);
            sendBroadcast(i);
        }
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
