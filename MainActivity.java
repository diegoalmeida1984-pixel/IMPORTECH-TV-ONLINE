package com.importech.tvonline;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.*;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private TextView status;
    private String code;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null) {
                status.setText(message);
                status.setTextColor(Color.rgb(34, 197, 94));
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        code = DeviceId.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,17,31));
        root.setPadding(18, 12, 18, 12);

        TextView title = new TextView(this);
        title.setText("IMPORTECH TV ONLINE   •   " + code);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(4, 6, 4, 8);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("Iniciando comunicação...");
        status.setTextColor(Color.rgb(245,158,11));
        status.setTextSize(15);
        status.setPadding(4, 0, 4, 8);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setBackgroundColor(Color.rgb(7,17,31));
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("● ONLINE — aguardando Painel ADM");
                status.setTextColor(Color.rgb(34,197,94));
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    status.setText("Sem internet. Vou reconectar automaticamente.");
                    status.setTextColor(Color.rgb(245,158,11));
                }
            }
            @SuppressWarnings("deprecation")
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                status.setText("Sem internet. Vou reconectar automaticamente.");
                status.setTextColor(Color.rgb(245,158,11));
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        startAgent();
        loadBoxPage();
        watchNetwork();
    }

    private void startAgent() {
        Intent i = new Intent(this, AgentService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void loadBoxPage() {
        web.loadUrl("https://importech-tv-online.vercel.app/?mode=box&code=" + code);
    }

    private void watchNetwork() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        status.setText("Internet detectada. Reconectando...");
                        status.setTextColor(Color.rgb(34,197,94));
                        loadBoxPage();
                        startAgent();
                    });
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(commandReceiver, new IntentFilter("com.importech.tvonline.COMMAND"));
    }

    @Override protected void onStop() {
        try { unregisterReceiver(commandReceiver); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (networkCallback != null && android.os.Build.VERSION.SDK_INT >= 24) {
            try {
                ((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE))
                    .unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
