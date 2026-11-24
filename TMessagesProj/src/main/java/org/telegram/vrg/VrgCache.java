package org.telegram.vrg;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;

public final class VrgCache {
    private VrgCache() {
    }

    public static void clear(Utilities.Callback<Long> done) {
        FileLoader.getInstance(UserConfig.selectedAccount).cancelLoadAllFiles();
        Utilities.globalQueue.postRunnable(() -> {
            long freed = 0;
            int[] types = {
                FileLoader.MEDIA_DIR_IMAGE,
                FileLoader.MEDIA_DIR_VIDEO,
                FileLoader.MEDIA_DIR_DOCUMENT,
                FileLoader.MEDIA_DIR_AUDIO,
                FileLoader.MEDIA_DIR_STORIES,
                FileLoader.MEDIA_DIR_CACHE,
                FileLoader.MEDIA_DIR_IMAGE_PUBLIC,
                FileLoader.MEDIA_DIR_VIDEO_PUBLIC
            };
            for (int type : types) {
                try {
                    File dir = FileLoader.checkDirectory(type);
                    if (dir != null) {
                        freed += deleteContents(dir);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            try {
                final int account = UserConfig.selectedAccount;
                java.util.LinkedHashMap<Long, java.util.ArrayList<Integer>> wiped = VrgStore.getInstance().clearAllDeletedSync(account);
                for (java.util.Map.Entry<Long, java.util.ArrayList<Integer>> e : wiped.entrySet()) {
                    final long dialogId = e.getKey();
                    final java.util.ArrayList<Integer> ids = e.getValue();
                    org.telegram.messenger.MessagesStorage storage = org.telegram.messenger.MessagesStorage.getInstance(account);
                    storage.getStorageQueue().postRunnable(() -> {
                        java.util.ArrayList<Long> dialogIds = storage.markMessagesAsDeleted(dialogId, ids, false, true, 0, 0);
                        storage.updateDialogsWithDeletedMessages(dialogId, -dialogId, ids, dialogIds);
                    });
                    AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.messagesDeleted, ids, dialogId < 0 ? -dialogId : 0L, false));
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final long result = freed;
            AndroidUtilities.runOnUIThread(() -> done.run(result));
        });
    }

    public static void wipeMessages(int account, long dialogId, java.util.ArrayList<Integer> ids) {
        if (dialogId == 0 || ids == null || ids.isEmpty()) {
            return;
        }
        org.telegram.messenger.MessagesStorage storage = org.telegram.messenger.MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            java.util.ArrayList<Long> dialogIds = storage.markMessagesAsDeleted(dialogId, ids, false, true, 0, 0);
            storage.updateDialogsWithDeletedMessages(dialogId, -dialogId, ids, dialogIds);
        });
        AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.messagesDeleted, ids, dialogId < 0 ? -dialogId : 0L, false));
    }

    private static long deleteContents(File dir) {
        long freed = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                freed += deleteContents(file);
            } else {
                long size = file.length();
                if (file.delete()) {
                    freed += size;
                }
            }
        }
        return freed;
    }
}

