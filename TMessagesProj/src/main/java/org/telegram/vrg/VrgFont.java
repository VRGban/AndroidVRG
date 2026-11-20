package org.telegram.vrg;

import android.graphics.Typeface;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;

public final class VrgFont {
    public static final String MODE_INTER = "inter";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_CUSTOM = "custom";

    private static Typeface regular;
    private static Typeface medium;
    private static boolean loaded;

    private VrgFont() {
    }

    public static String mode() {
        return VrgConfig.prefs().getString("font_mode", MODE_INTER);
    }

    public static void setMode(String mode) {
        VrgConfig.prefs().edit().putString("font_mode", mode).apply();
    }

    public static String customPath() {
        return VrgConfig.prefs().getString("font_custom_path", "");
    }

    public static void setCustomPath(String path) {
        VrgConfig.prefs().edit().putString("font_custom_path", path).putString("font_mode", MODE_CUSTOM).apply();
    }

    public static String modeTitle() {
        switch (mode()) {
            case MODE_SYSTEM:
                return "Системный";
            case MODE_CUSTOM:
                String p = customPath();
                return p.isEmpty() ? "Свой файл" : new File(p).getName();
            default:
                return "Inter";
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            String mode = mode();
            if (MODE_INTER.equals(mode)) {
                regular = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), "fonts/inter_regular.ttf");
                medium = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), "fonts/inter_medium.ttf");
            } else if (MODE_CUSTOM.equals(mode)) {
                String path = customPath();
                if (!path.isEmpty() && new File(path).exists()) {
                    Typeface base = Typeface.createFromFile(path);
                    regular = base;
                    medium = Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, 500, false) : Typeface.create(base, Typeface.BOLD);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            regular = null;
            medium = null;
        }
    }

    public static Typeface regular() {
        load();
        return regular;
    }

    public static Typeface medium() {
        load();
        return medium;
    }
}
