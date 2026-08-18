package com.importech.tvonline;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent s = new Intent(context, AgentService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
            else context.startService(s);
        }
    }
}
