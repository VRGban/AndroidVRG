package org.telegram.vrg;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class VrgReadTimes {
    private static final int MAX_MARKERS = 300;

    private VrgReadTimes() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("vrg_read_times", 0);
    }

    public static synchronized void record(long dialogId, long upTo) {
        if (dialogId == 0 || upTo <= 0) {
            return;
        }
        try {
            String key = "d" + dialogId;
            String raw = prefs().getString(key, "");
            String[] parts = raw.isEmpty() ? new String[0] : raw.split(",");
            if (parts.length > 0) {
                String[] last = parts[parts.length - 1].split(":");
                if (last.length == 2 && Long.parseLong(last[0]) >= upTo) {
                    return;
                }
            }
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, parts.length - (MAX_MARKERS - 1));
            for (int i = from; i < parts.length; i++) {
                sb.append(parts[i]).append(',');
            }
            sb.append(upTo).append(':').append(System.currentTimeMillis() / 1000L);
            prefs().edit().putString(key, sb.toString()).apply();
        } catch (Throwable ignore) {
        }
    }

    public static synchronized long when(long dialogId, long messageId) {
        try {
            String raw = prefs().getString("d" + dialogId, "");
            if (raw.isEmpty()) {
                return 0;
            }
            for (String part : raw.split(",")) {
                String[] pair = part.split(":");
                if (pair.length == 2 && Long.parseLong(pair[0]) >= messageId) {
                    return Long.parseLong(pair[1]);
                }
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }

    public static String format(long seconds) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date(seconds * 1000L));
    }
}

