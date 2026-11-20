package org.telegram.vrg;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.messenger.R;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VrgUpdater {
    public static final String CURRENT_VERSION = "1.0.4";
    private static final String VERSION_URL = "https://vrgclient.app/version.json";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static volatile boolean checking = false;

    private VrgUpdater() {
    }

    private static boolean autoChecked = false;

    private static final long RECHECK_MS = 2L * 60L * 60L * 1000L;
    private static final long RESHOW_MS = 24L * 60L * 60L * 1000L;

    public static void checkOnce(Context context) {
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = VrgConfig.prefs().getLong("update_last_check", 0);
        if (autoChecked && now - last < RECHECK_MS) {
            return;
        }
        autoChecked = true;
        VrgConfig.prefs().edit().putLong("update_last_check", now).apply();
        AndroidUtilities.runOnUIThread(() -> check(context, null, true), 3500);
    }

    private static boolean shownRecently(String version) {
        long now = System.currentTimeMillis();
        String shownVersion = VrgConfig.prefs().getString("update_shown_version", "");
        long shownAt = VrgConfig.prefs().getLong("update_shown_at", 0);
        return version.equals(shownVersion) && now - shownAt < RESHOW_MS;
    }

    private static void markShown(String version) {
        VrgConfig.prefs().edit()
            .putString("update_shown_version", version)
            .putLong("update_shown_at", System.currentTimeMillis())
            .apply();
    }

    public static void check(BaseFragment fragment) {
        if (fragment == null || fragment.getContext() == null) {
            return;
        }
        check(fragment.getContext(), fragment, false);
    }

    private static void check(Context context, BaseFragment fragment, boolean silent) {
        if (context == null || checking) {
            return;
        }
        checking = true;
        executor.execute(() -> {
            String remoteVer = "";
            String changelog = "";
            String targetUrl = "https://vrgclient.app/#download";
            boolean ok = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    JSONObject obj = new JSONObject(sb.toString());
                    remoteVer = obj.optString("version", "").trim();
                    changelog = obj.optString("changelog", "").trim();
                    String url = obj.optString("url", "").trim();
                    if (!TextUtils.isEmpty(url)) {
                        targetUrl = url;
                    }
                    ok = !TextUtils.isEmpty(remoteVer);
                }
                conn.disconnect();
            } catch (Throwable ignore) {
            } finally {
                checking = false;
            }

            final boolean success = ok;
            final String fVer = remoteVer;
            final String fLog = changelog;
            final String fUrl = targetUrl;
            AndroidUtilities.runOnUIThread(() -> {
                if (!success) {
                    if (!silent && fragment != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "Не удалось проверить обновления").show();
                    }
                    return;
                }
                if (isNewer(fVer, CURRENT_VERSION)) {
                    if (silent && shownRecently(fVer)) {
                        return;
                    }
                    markShown(fVer);
                    String msg = "Вышла новая версия VRG v" + fVer + " (текущая v" + CURRENT_VERSION + ").";
                    if (!TextUtils.isEmpty(fLog)) {
                        msg += "\n\n" + fLog;
                    }
                    new AlertDialog.Builder(context)
                        .setTitle("Доступно обновление VRG v" + fVer)
                        .setMessage(msg)
                        .setPositiveButton("Скачать", (d, w) -> Browser.openUrl(context, fUrl))
                        .setNegativeButton("Позже", null)
                        .show();
                } else if (!silent && fragment != null) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "У вас актуальная версия VRG v" + CURRENT_VERSION).show();
                }
            });
        });
    }

    private static boolean isNewer(String remote, String local) {
        String[] rParts = remote.split("\\.");
        String[] lParts = local.split("\\.");
        int len = Math.max(rParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            int rv = i < rParts.length ? parseIntSafe(rParts[i]) : 0;
            int lv = i < lParts.length ? parseIntSafe(lParts[i]) : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Throwable ignore) {
            return 0;
        }
    }
}
