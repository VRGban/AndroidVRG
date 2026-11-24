package org.telegram.vrg;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VrgGhost {
    public static final int OVERRIDE_DEFAULT = 0;
    public static final int OVERRIDE_FORCE_READ = 1;
    public static final int OVERRIDE_FORCE_UNREAD = 2;

    private static final Set<Long> allowOnceDialogs = Collections.synchronizedSet(new HashSet<>());

    private VrgGhost() {
    }

    public static void allowReadOnce(long dialogId) {
        if (dialogId != 0) {
            allowOnceDialogs.add(dialogId);
        }
    }

    public static int getReadOverride(long dialogId) {
        int mode = VrgConfig.prefs().getInt("ghost_chat_" + dialogId, OVERRIDE_DEFAULT);
        return (mode == OVERRIDE_FORCE_READ || mode == OVERRIDE_FORCE_UNREAD) ? mode : OVERRIDE_DEFAULT;
    }

    public static void setReadOverride(long dialogId, int mode) {
        String key = "ghost_chat_" + dialogId;
        if (mode != OVERRIDE_FORCE_READ && mode != OVERRIDE_FORCE_UNREAD) {
            VrgConfig.prefs().edit().remove(key).apply();
        } else {
            VrgConfig.prefs().edit().putInt(key, mode).apply();
        }
    }

    public static boolean shouldBlockRead(long dialogId) {
        if (allowOnceDialogs.remove(dialogId)) {
            return false;
        }
        int override = getReadOverride(dialogId);
        if (override == OVERRIDE_FORCE_READ) {
            return false;
        }
        if (override == OVERRIDE_FORCE_UNREAD) {
            return true;
        }
        return VrgConfig.isGhostRead();
    }
}

