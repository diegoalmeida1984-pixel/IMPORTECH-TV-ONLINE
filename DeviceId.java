package com.importech.tvonline;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

public final class DeviceId {
    private static final String PREF = "importech_tv_online";
    private static final String KEY = "device_code";

    public static String get(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String code = sp.getString(KEY, null);
        if (code == null || !code.matches("ITV-\\d{6}")) {
            SecureRandom r = new SecureRandom();
            int n = 100000 + r.nextInt(900000);
            code = "ITV-" + n;
            sp.edit().putString(KEY, code).apply();
        }
        return code;
    }
}
