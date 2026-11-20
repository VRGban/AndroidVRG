package org.telegram.vrg;

import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

public final class VrgBackup {
    private static final String FILE_NAME = "vrg_backup.json";

    private VrgBackup() {
    }

    private static File musicFile(String name) {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "vrg_music");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, name);
    }

    private static String readText(File f) {
        if (f == null || !f.exists() || f.length() == 0 || f.length() > 8 * 1024 * 1024) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeText(File f, String text) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static File getBackupFile() {
        File dir = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = ApplicationLoader.getFilesDirFixed();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    public static void exportSettings(BaseFragment fragment) {
        try {
            JSONObject root = new JSONObject();
            JSONObject prefsJson = new JSONObject();
            Map<String, ?> all = VrgConfig.prefs().getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                Object val = e.getValue();
                if (val instanceof Boolean || val instanceof Integer || val instanceof Long || val instanceof String) {
                    prefsJson.put(e.getKey(), val);
                }
            }
            root.put("version", VrgUpdater.CURRENT_VERSION);
            root.put("prefs", prefsJson);
            String lib = readText(musicFile("library.json"));
            if (lib != null) {
                root.put("music_library", lib);
            }
            String st = readText(musicFile("state.json"));
            if (st != null) {
                root.put("music_state", st);
            }

            File file = getBackupFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(
                    R.raw.info,
                    "Настройки сохранены: " + file.getName()
                ).show();
            }
        } catch (Throwable e) {
            FileLog.e(e);
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, "Ошибка экспорта настроек").show();
            }
        }
    }

    public static boolean importSettings(BaseFragment fragment) {
        File file = getBackupFile();
        if (!file.exists()) {
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "Файл резервной копии не найден").show();
            }
            return false;
        }
        try {
            if (file.length() > 2 * 1024 * 1024) {
                if (fragment != null) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, "Файл резервной копии слишком большой").show();
                }
                return false;
            }
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read = 0;
                while (read < data.length) {
                    int n = fis.read(data, read, data.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
            }
            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONObject prefsJson = root.optJSONObject("prefs");
            if (prefsJson == null) {
                prefsJson = root;
            }
            SharedPreferences current = VrgConfig.prefs();
            Map<String, ?> existing = current.getAll();
            SharedPreferences.Editor editor = current.edit();
            Iterator<String> it = prefsJson.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object val = prefsJson.opt(k);
                if (k.length() > 128) {
                    continue;
                }
                Object old = existing.get(k);
                if (old != null && val != null && !old.getClass().equals(val.getClass())) {
                    continue;
                }
                if (val instanceof Boolean) {
                    editor.putBoolean(k, (Boolean) val);
                } else if (val instanceof Integer) {
                    editor.putInt(k, (Integer) val);
                } else if (val instanceof Long) {
                    editor.putLong(k, (Long) val);
                } else if (val instanceof String) {
                    editor.putString(k, (String) val);
                }
            }
            editor.apply();
            String lib = root.optString("music_library", "");
            if (!lib.isEmpty()) {
                writeText(musicFile("library.json"), lib);
                String st = root.optString("music_state", "");
                if (!st.isEmpty()) {
                    writeText(musicFile("state.json"), st);
                }
            }
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, lib.isEmpty() ? "Настройки VRG восстановлены" : "Настройки и музыка восстановлены. Перезапустите приложение").show();
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            if (fragment != null) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, "Ошибка импорта настроек").show();
            }
            return false;
        }
    }
}
