package org.telegram.vrg;

import android.text.TextUtils;

import java.util.ArrayList;

public final class VrgPins {
    private static final String KEY = "local_pins";

    private VrgPins() {
    }

    public static boolean isPinned(long dialogId) {
        return index(dialogId) > 0;
    }

    public static int index(long dialogId) {
        ArrayList<Long> list = list();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == dialogId) {
                return 10 + i;
            }
        }
        return 0;
    }

    public static boolean toggle(long dialogId) {
        ArrayList<Long> list = list();
        int idx = list.indexOf(dialogId);
        boolean pinnedNow;
        if (idx >= 0) {
            list.remove(idx);
            pinnedNow = false;
        } else {
            list.add(dialogId);
            pinnedNow = true;
        }
        save(list);
        return pinnedNow;
    }

    public static ArrayList<Long> list() {
        ArrayList<Long> out = new ArrayList<>();
        String raw = VrgConfig.prefs().getString(KEY, "");
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        String[] parts = raw.split(",");
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) {
                try {
                    long id = Long.parseLong(p.trim());
                    if (id != 0) {
                        out.add(id);
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return out;
    }

    private static void save(ArrayList<Long> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(list.get(i));
        }
        VrgConfig.prefs().edit().putString(KEY, sb.toString()).apply();
    }
}
