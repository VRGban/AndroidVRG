package org.telegram.vrg;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VrgStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "vrg_store.db";
    private static final int DB_VERSION = 1;

    private static volatile VrgStore instance;
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private final Set<String> deletedCache = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> permanentNextKeys = Collections.synchronizedSet(new HashSet<>());
    private final HashMap<String, ArrayList<EditEntry>> editCache = new HashMap<>();
    private volatile boolean loaded = false;

    public static final class DeletedLogEntry {
        public final long dialogId;
        public final int msgId;
        public final String preview;
        public final long deletedAt;

        public DeletedLogEntry(long dialogId, int msgId, String preview, long deletedAt) {
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.preview = preview;
            this.deletedAt = deletedAt;
        }
    }

    public static final class EditEntry {
        public final String text;
        public final long editedAt;

        public EditEntry(String text, long editedAt) {
            this.text = text;
            this.editedAt = editedAt;
        }
    }

    private VrgStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public static VrgStore getInstance() {
        if (instance == null) {
            synchronized (VrgStore.class) {
                if (instance == null) {
                    instance = new VrgStore(ApplicationLoader.applicationContext);
                    instance.loadAllAsync();
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS deleted_messages (" +
            "account INTEGER NOT NULL, " +
            "dialog_id INTEGER NOT NULL, " +
            "msg_id INTEGER NOT NULL, " +
            "deleted_at INTEGER NOT NULL, " +
            "PRIMARY KEY (account, dialog_id, msg_id))"
        );
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS edit_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "account INTEGER NOT NULL, " +
            "dialog_id INTEGER NOT NULL, " +
            "msg_id INTEGER NOT NULL, " +
            "old_text TEXT NOT NULL, " +
            "edited_at INTEGER NOT NULL)"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edit_lookup ON edit_history (account, dialog_id, msg_id)");
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS deleted_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "account INTEGER NOT NULL, " +
            "dialog_id INTEGER NOT NULL, " +
            "msg_id INTEGER NOT NULL, " +
            "preview TEXT NOT NULL, " +
            "deleted_at INTEGER NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    private void loadAllAsync() {
        if (loaded) {
            return;
        }
        dbExecutor.execute(this::ensureLoadedSync);
    }

    private void ensureLoadedSync() {
        if (loaded) {
            return;
        }
        try {
            SQLiteDatabase db = getReadableDatabase();
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS deleted_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "account INTEGER NOT NULL, " +
                "dialog_id INTEGER NOT NULL, " +
                "msg_id INTEGER NOT NULL, " +
                "preview TEXT NOT NULL, " +
                "deleted_at INTEGER NOT NULL)"
            );
            try (Cursor c = db.rawQuery("SELECT account, dialog_id, msg_id FROM deleted_messages", null)) {
                while (c.moveToNext()) {
                    deletedCache.add(key(c.getInt(0), c.getLong(1), c.getInt(2)));
                }
            }
            synchronized (editCache) {
                try (Cursor c = db.rawQuery("SELECT account, dialog_id, msg_id, old_text, edited_at FROM edit_history ORDER BY id ASC", null)) {
                    while (c.moveToNext()) {
                        String k = key(c.getInt(0), c.getLong(1), c.getInt(2));
                        ArrayList<EditEntry> list = editCache.get(k);
                        if (list == null) {
                            list = new ArrayList<>();
                            editCache.put(k, list);
                        }
                        list.add(new EditEntry(c.getString(3), c.getLong(4)));
                    }
                }
            }
            loaded = true;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String key(int account, long dialogId, int msgId) {
        return account + ":" + dialogId + ":" + msgId;
    }

    public void markPermanentNext(long dialogId, java.util.List<Integer> mids) {
        if (mids == null) {
            return;
        }
        for (int i = 0; i < mids.size(); i++) {
            Integer id = mids.get(i);
            if (id != null && id > 0) {
                permanentNextKeys.add(dialogId + ":" + id);
            }
        }
    }

    public boolean consumePermanentDelete(long dialogId, java.util.List<Integer> mids) {
        if (mids == null || mids.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < mids.size(); i++) {
            Integer id = mids.get(i);
            if (id != null && permanentNextKeys.remove(dialogId + ":" + id)) {
                any = true;
            }
        }
        return any;
    }

    public void forget(int account, long dialogId, int msgId) {
        deletedCache.remove(key(account, dialogId, msgId));
        deletedCache.remove(key(account, 0L, msgId));
        dbExecutor.execute(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete("deleted_messages", "account = ? AND msg_id = ? AND (dialog_id = ? OR dialog_id = 0)",
                    new String[] { String.valueOf(account), String.valueOf(msgId), String.valueOf(dialogId) });
                db.delete("deleted_log", "account = ? AND msg_id = ? AND (dialog_id = ? OR dialog_id = 0)",
                    new String[] { String.valueOf(account), String.valueOf(msgId), String.valueOf(dialogId) });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public void forgetPeer(int account, long dialogId) {
        String prefix = account + ":" + dialogId + ":";
        synchronized (deletedCache) {
            java.util.Iterator<String> it = deletedCache.iterator();
            while (it.hasNext()) {
                if (it.next().startsWith(prefix)) {
                    it.remove();
                }
            }
        }
        dbExecutor.execute(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.delete("deleted_messages", "account = ? AND dialog_id = ?",
                    new String[] { String.valueOf(account), String.valueOf(dialogId) });
                db.delete("deleted_log", "account = ? AND dialog_id = ?",
                    new String[] { String.valueOf(account), String.valueOf(dialogId) });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public void markDeleted(int account, long dialogId, int msgId) {
        markDeleted(account, dialogId, msgId, null);
    }

    public void markDeleted(int account, long dialogId, int msgId, String preview) {
        if (msgId <= 0) {
            return;
        }
        ensureLoadedSync();
        String k = key(account, dialogId, msgId);
        if (!deletedCache.add(k)) {
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        final String cleanPreview = preview != null ? preview : "";
        dbExecutor.execute(() -> {
            try {
                SQLiteDatabase db = getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("account", account);
                cv.put("dialog_id", dialogId);
                cv.put("msg_id", msgId);
                cv.put("deleted_at", now);
                db.insertWithOnConflict("deleted_messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

                if (!cleanPreview.isEmpty() || dialogId != 0) {
                    ContentValues logCv = new ContentValues();
                    logCv.put("account", account);
                    logCv.put("dialog_id", dialogId);
                    logCv.put("msg_id", msgId);
                    logCv.put("preview", cleanPreview.isEmpty() ? "[Сообщение]" : cleanPreview);
                    logCv.put("deleted_at", now);
                    db.insert("deleted_log", null, logCv);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public ArrayList<Integer> getDeletedIdsForPeerSync(int account, long dialogId) {
        ensureLoadedSync();
        ArrayList<Integer> out = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String sql = "SELECT DISTINCT msg_id FROM deleted_messages WHERE account = ? AND (dialog_id = ? OR dialog_id = 0) ORDER BY msg_id DESC LIMIT 500";
            try (Cursor c = db.rawQuery(sql, new String[] { String.valueOf(account), String.valueOf(dialogId) })) {
                while (c.moveToNext()) {
                    int mid = c.getInt(0);
                    if (mid > 0) {
                        out.add(mid);
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    public List<DeletedLogEntry> getDeletedLogSync(int account, long dialogId, int limit) {
        ensureLoadedSync();
        ArrayList<DeletedLogEntry> out = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            String sql = dialogId != 0
                ? "SELECT dialog_id, msg_id, preview, deleted_at FROM deleted_log WHERE account = ? AND dialog_id = ? ORDER BY id DESC LIMIT ?"
                : "SELECT dialog_id, msg_id, preview, deleted_at FROM deleted_log WHERE account = ? ORDER BY id DESC LIMIT ?";
            String[] args = dialogId != 0
                ? new String[] { String.valueOf(account), String.valueOf(dialogId), String.valueOf(limit) }
                : new String[] { String.valueOf(account), String.valueOf(limit) };
            try (Cursor c = db.rawQuery(sql, args)) {
                while (c.moveToNext()) {
                    out.add(new DeletedLogEntry(c.getLong(0), c.getInt(1), c.getString(2), c.getLong(3)));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    public void clearDeletedLog(int account, long dialogId) {
        dbExecutor.execute(() -> {
            try {
                if (dialogId != 0) {
                    getWritableDatabase().delete("deleted_log", "account = ? AND dialog_id = ?", new String[] { String.valueOf(account), String.valueOf(dialogId) });
                } else {
                    getWritableDatabase().delete("deleted_log", "account = ?", new String[] { String.valueOf(account) });
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public boolean isDeleted(int account, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        int msgId = messageObject.getId();
        if (msgId <= 0) {
            return false;
        }
        long dialogId = messageObject.getDialogId();
        return deletedCache.contains(key(account, dialogId, msgId))
            || deletedCache.contains(key(account, 0L, msgId));
    }

    public void recordEdit(int account, long dialogId, int msgId, String oldText) {
        if (TextUtils.isEmpty(oldText) || !VrgConfig.isKeepEdited() || msgId <= 0) {
            return;
        }
        ensureLoadedSync();
        String k = key(account, dialogId, msgId);
        long now = System.currentTimeMillis() / 1000L;
        synchronized (editCache) {
            ArrayList<EditEntry> list = editCache.get(k);
            if (list == null) {
                list = new ArrayList<>();
                editCache.put(k, list);
            } else if (!list.isEmpty() && oldText.equals(list.get(list.size() - 1).text)) {
                return;
            }
            list.add(new EditEntry(oldText, now));
        }
        dbExecutor.execute(() -> {
            try {
                ContentValues cv = new ContentValues();
                cv.put("account", account);
                cv.put("dialog_id", dialogId);
                cv.put("msg_id", msgId);
                cv.put("old_text", oldText);
                cv.put("edited_at", now);
                getWritableDatabase().insert("edit_history", null, cv);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public boolean hasEditHistory(int account, long dialogId, int msgId) {
        if (msgId <= 0) {
            return false;
        }
        synchronized (editCache) {
            ArrayList<EditEntry> list = editCache.get(key(account, dialogId, msgId));
            return list != null && !list.isEmpty();
        }
    }

    public List<EditEntry> getEditHistory(int account, long dialogId, int msgId) {
        synchronized (editCache) {
            ArrayList<EditEntry> list = editCache.get(key(account, dialogId, msgId));
            if (list == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(list);
        }
    }
}
