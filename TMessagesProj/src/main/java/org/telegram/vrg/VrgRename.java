package org.telegram.vrg;

import android.text.TextUtils;

public final class VrgRename {
    private VrgRename() {
    }

    private static String key(long dialogId) {
        return "rename_peer_" + dialogId;
    }

    public static String lookup(long dialogId) {
        if (dialogId == 0) {
            return null;
        }
        String val = VrgConfig.prefs().getString(key(dialogId), null);
        return TextUtils.isEmpty(val) ? null : val;
    }

    public static void set(long dialogId, String name) {
        if (dialogId == 0) {
            return;
        }
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.isEmpty()) {
            VrgConfig.prefs().edit().remove(key(dialogId)).apply();
        } else {
            VrgConfig.prefs().edit().putString(key(dialogId), trimmed).apply();
        }
    }
}
