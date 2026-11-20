package org.telegram.vrg;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class VrgThemeHelper {
    private static final String PREF_KEY = "vrg_black_theme_v5";
    private static final String PREF_ACTIVE_THEME = "vrg_active_theme";

    private VrgThemeHelper() {
    }

    public static void applyDefaultDarkIfFirstLaunch() {
        if (VrgConfig.prefs().getBoolean(PREF_KEY, false)) {
            ensureSelectedTheme();
            return;
        }
        if (applyVrgDarkTheme()) {
            VrgConfig.prefs().edit().putBoolean(PREF_KEY, true).apply();
        }
    }

    public static void ensureSelectedTheme() {
        String desired = VrgConfig.prefs().getString(PREF_ACTIVE_THEME, "dark");
        Theme.ThemeInfo current = Theme.getCurrentTheme();
        String currentName = current != null ? current.name : null;
        boolean isVrgTheme = "VRG Black".equals(currentName) || "VRG Light".equals(currentName);
        if (!isVrgTheme) {
            return;
        }
        String expectedName = "light".equals(desired) ? "VRG Light" : "VRG Black";
        if (expectedName.equals(currentName)) {
            disableAutoNight();
            return;
        }
        if ("light".equals(desired)) {
            applyLightTheme();
        } else {
            applyVrgDarkTheme();
        }
    }

    public static boolean applyVrgDarkTheme() {
        VrgConfig.prefs().edit().putString(PREF_ACTIVE_THEME, "dark").apply();
        return applyAssetTheme("vrg_dark.attheme", "vrg_black_v3.attheme", "VRG Black");
    }

    public static boolean applyLightTheme() {
        VrgConfig.prefs().edit().putString(PREF_ACTIVE_THEME, "light").apply();
        return applyAssetTheme("vrg_light.attheme", "vrg_light_v3.attheme", "VRG Light");
    }

    private static void disableAutoNight() {
        try {
            Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);
            prefs.edit().putInt("selectedAutoNightType", Theme.AUTO_NIGHT_TYPE_NONE).apply();
        } catch (Throwable ignore) {
        }
    }

    private static boolean applyAssetTheme(String assetName, String outName, String themeName) {
        try {
            disableAutoNight();
            File outFile = new File(ApplicationLoader.getFilesDirFixed(), outName);
            try (InputStream is = ApplicationLoader.applicationContext.getAssets().open(assetName);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
            Theme.ThemeInfo info = Theme.applyThemeFile(outFile, themeName, null, false);
            if (info != null) {
                Theme.applyTheme(info);
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    try {
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                            org.telegram.messenger.NotificationCenter.needSetDayNightTheme, info, false, null, -1);
                        if (org.telegram.ui.LaunchActivity.instance != null) {
                            org.telegram.ui.LaunchActivity.instance.rebuildAllFragments(true);
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                });
                return true;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }
}
