package org.telegram.vrg.music;

import android.content.Context;
import android.text.TextUtils;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.vrg.VrgConfig;

import java.io.File;
import java.util.ArrayList;

public final class VrgYoutube {

    private static boolean initDone;
    private static boolean initFailed;
    private static boolean updateStarted;
    private static final Object EXEC_LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicInteger FG = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.ConcurrentHashMap<String, Object[]> URL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static void cancelBackground() {
        try {
            YoutubeDL.getInstance().destroyProcessById("vrg-bg");
        } catch (Throwable ignore) {
        }
    }
    private static final java.util.Set<String> HLS_URLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isHls(String url) {
        return url != null && HLS_URLS.contains(url);
    }
    private static final String[] CLIENT_ARGS = {
        "youtube:skip=hls,dash",
        "youtube:skip=hls,dash",
        "youtube:player_client=android_vr;skip=hls,dash",
        "youtube:player_client=ios;skip=hls,dash",
        "youtube:player_client=tv;skip=hls,dash",
        "youtube:player_client=mweb;skip=hls,dash",
    };
    private static volatile int lastGood = 0;
    private static final boolean[] CLIENT_COOKIES = {true, false, false, false, true, true};
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> CLIENT_INDEX = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean nextClient(String trackId) {
        int cur = CLIENT_INDEX.containsKey(trackId) ? CLIENT_INDEX.get(trackId) : 0;
        if (cur + 1 >= CLIENT_ARGS.length) {
            CLIENT_INDEX.remove(trackId);
            return false;
        }
        CLIENT_INDEX.put(trackId, cur + 1);
        return true;
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, String>> HEADERS = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.Map<String, String> headersFor(String url) {
        return url == null ? null : HEADERS.get(url);
    }

    private VrgYoutube() {
    }

    public static File cookiesFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "vrg_youtube_cookies.txt");
    }

    public static boolean isLoggedIn() {
        File f = cookiesFile();
        return f.exists() && f.length() > 100;
    }

    public static void logout() {
        File f = cookiesFile();
        if (f.exists()) {
            f.delete();
        }
    }

    private static synchronized boolean ensureInit() {
        if (initDone) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        if (android.os.Build.VERSION.SDK_INT < 24) {
            initFailed = true;
            return false;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (!VrgConfig.prefs().getBoolean("yt_dlp_reset_v2", false)) {
                try {
                    File base = new File(ctx.getNoBackupFilesDir(), "youtubedl-android/yt-dlp");
                    deleteRecursive(base);
                } catch (Throwable ignore) {
                }
                VrgConfig.prefs().edit().putBoolean("yt_dlp_reset_v2", true).putLong("yt_dlp_last_update", 0L).apply();
            }
            long t0 = System.currentTimeMillis();
            YoutubeDL.getInstance().init(ctx);
            initDone = true;
            if (!updateStarted) {
                updateStarted = true;
                final long last = VrgConfig.prefs().getLong("yt_dlp_last_update", 0L);
                final boolean never = !VrgConfig.prefs().getBoolean("yt_dlp_updated_v2", false);
                if (never || System.currentTimeMillis() - last > 7L * 24 * 3600 * 1000) {
                    synchronized (EXEC_LOCK) {
                        try {
                            if (!VrgConfig.prefs().getBoolean("yt_dlp_force_v2", false)) {
                                ctx.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit().clear().apply();
                                VrgConfig.prefs().edit().putBoolean("yt_dlp_force_v2", true).apply();
                            }
                            long u0 = System.currentTimeMillis();
                            YoutubeDL.UpdateStatus st = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel._STABLE);
                            try {
                                YoutubeDLResponse vr = YoutubeDL.getInstance().execute(new YoutubeDLRequest("--version"));
                            } catch (Throwable ve) {
                                android.util.Log.e("VRGYT", "version check failed", ve);
                            }
                            VrgConfig.prefs().edit().putLong("yt_dlp_last_update", System.currentTimeMillis()).putBoolean("yt_dlp_updated_v2", true).apply();
                        } catch (Throwable e) {
                            android.util.Log.e("VRGYT", "yt-dlp update failed", e);
                        }
                    }
                }
            }
            return true;
        } catch (Throwable e) {
            android.util.Log.e("VRGYT", "init failed", e);
            FileLog.e(e);
            initFailed = true;
            return false;
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursive(k);
            }
        }
        f.delete();
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, String> LAST_URL = new java.util.concurrent.ConcurrentHashMap<>();

    public static String lastUrl(String trackId) {
        return LAST_URL.get(trackId);
    }

    private static YoutubeDLRequest request(String target) {
        return request(target, true);
    }

    private static YoutubeDLRequest request(String target, boolean withCookies) {
        YoutubeDLRequest r = new YoutubeDLRequest(target);
        try {
            File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "yt-dlp-cache");
            cacheDir.mkdirs();
            r.addOption("--cache-dir", cacheDir.getAbsolutePath());
        } catch (Throwable ignore) {
        }
        try {
            File qjs = new File(ApplicationLoader.applicationContext.getApplicationInfo().nativeLibraryDir, "libqjs.so");
            if (qjs.exists()) {
                r.addOption("--js-runtimes", "quickjs:" + qjs.getAbsolutePath());
            } else {
            }
        } catch (Throwable ignore) {
        }
        if (withCookies && isLoggedIn()) {
            r.addOption("--cookies", cookiesFile().getAbsolutePath());
        }
        return r;
    }

    public static String authMessage() {
        return isLoggedIn()
            ? "YouTube не отдаёт этот трек приложению (защита от ботов), хотя вход выполнен. Попробуй другой трек."
            : "Для этого трека YouTube требует вход в аккаунт. Войди в Google в настройках (раздел «Плеер»).";
    }

    public static boolean isAuthMessage(String m) {
        return m != null && m.contains("требует вход в аккаунт");
    }

    public static String friendlyError(String raw) {
        if (raw == null) {
            return "YouTube: не удалось получить данные";
        }
        String lower = raw.toLowerCase();
        if (lower.contains("sign in") || lower.contains("not a bot") || lower.contains("--cookies") || lower.contains("authentication")) {
            return authMessage();
        }
        if (lower.contains("unavailable") || lower.contains("private") || lower.contains("removed")) {
            return "Видео недоступно на YouTube";
        }
        String s = raw.trim();
        return "YouTube: " + (s.length() > 140 ? s.substring(s.length() - 140) : s);
    }

    private static VrgMusicTypes.TrackInfo parseLine(String line, double maxDuration) {
        try {
            JSONObject o = new JSONObject(line);
            String id = o.optString("id", "");
            String title = o.optString("title", "");
            if (id.length() != 11 || TextUtils.isEmpty(title)) {
                return null;
            }
            double duration = o.optDouble("duration", 0);
            if (duration <= 0 || duration > maxDuration) {
                return null;
            }
            String channel = o.optString("channel", "");
            if (TextUtils.isEmpty(channel) || "null".equals(channel)) {
                channel = o.optString("uploader", "");
            }
            channel = channel.replace(" - Topic", "");
            VrgMusicTypes.TrackInfo t = new VrgMusicTypes.TrackInfo();
            t.id = "yt:" + id;
            t.title = title;
            t.artist = channel;
            t.durationMs = (long) (duration * 1000.0);
            t.artworkUrl = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
            t.streamApiUrl = "yt:" + id;
            t.permalinkUrl = "https://www.youtube.com/watch?v=" + id;
            return t;
        } catch (Throwable e) {
            return null;
        }
    }

    private static ArrayList<VrgMusicTypes.TrackInfo> parseLines(String out, double maxDuration) {
        ArrayList<VrgMusicTypes.TrackInfo> list = new ArrayList<>();
        if (out == null) {
            return list;
        }
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) != '{') {
                continue;
            }
            VrgMusicTypes.TrackInfo t = parseLine(line, maxDuration);
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    public static ArrayList<VrgMusicTypes.TrackInfo> searchSync(String query, int limit) {
        if (TextUtils.isEmpty(query) || !ensureInit()) {
            return new ArrayList<>();
        }
        cancelBackground();
        try {
            YoutubeDLRequest r = request("ytsearch" + limit + ":" + query.trim());
            r.addOption("--flat-playlist");
            r.addOption("--dump-json");
            r.addOption("--skip-download");
            long t0 = System.currentTimeMillis();
            YoutubeDLResponse resp;
            synchronized (EXEC_LOCK) {
                resp = YoutubeDL.getInstance().execute(r);
            }
            ArrayList<VrgMusicTypes.TrackInfo> res = parseLines(resp.getOut(), 20 * 60);
            return res;
        } catch (Throwable e) {
            android.util.Log.e("VRGYT", "search failed", e);
            FileLog.e(e);
            return new ArrayList<>();
        }
    }

    public static ArrayList<VrgMusicTypes.TrackInfo> playlistSync(String url, String[] errorOut) {
        if (!ensureInit()) {
            if (errorOut != null) {
                errorOut[0] = "YouTube недоступен на этом устройстве";
            }
            return new ArrayList<>();
        }
        try {
            YoutubeDLRequest r = request(url.trim());
            r.addOption("--flat-playlist");
            r.addOption("--dump-json");
            r.addOption("--skip-download");
            YoutubeDLResponse resp;
            synchronized (EXEC_LOCK) {
                resp = YoutubeDL.getInstance().execute(r);
            }
            return parseLines(resp.getOut(), 40 * 60);
        } catch (Throwable e) {
            FileLog.e(e);
            if (errorOut != null) {
                errorOut[0] = friendlyError(e.getMessage());
            }
            return new ArrayList<>();
        }
    }

    private static boolean probe(String url, java.util.Map<String, String> headers) {
        return probeRange(url, headers, "bytes=0-1") && probeRange(url, headers, "bytes=1048576-1048700");
    }

    private static boolean probeRange(String url, java.util.Map<String, String> headers, String range) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            if (headers != null) {
                for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setRequestProperty("Range", range);
            int code = conn.getResponseCode();
            return code == 200 || code == 206 || code == 416;
        } catch (Throwable e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String resolveSync(String trackId, String[] errorOut) {
        return resolveSync(trackId, errorOut, false);
    }

    public static String resolveSync(String trackId, String[] errorOut, boolean background) {
        if (trackId == null || !trackId.startsWith("yt:")) {
            return null;
        }
        Object[] cached = URL_CACHE.get(trackId);
        if (cached != null && System.currentTimeMillis() - (Long) cached[2] < 3L * 3600 * 1000) {
            String cu = (String) cached[0];
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> ch = (java.util.Map<String, String>) cached[1];
            if (ch != null) {
                HEADERS.put(cu, ch);
            }
            LAST_URL.put(trackId, cu);
            return cu;
        }
        if (background) {
            if (FG.get() > 0) {
                return null;
            }
        } else {
            FG.incrementAndGet();
            cancelBackground();
        }
        try {
            String u = resolveInner(trackId, errorOut, background);
            if (u != null) {
                URL_CACHE.put(trackId, new Object[]{u, HEADERS.get(u), System.currentTimeMillis()});
            }
            return u;
        } finally {
            if (!background) {
                FG.decrementAndGet();
            }
        }
    }

    private static String resolveInner(String trackId, String[] errorOut, boolean bg) {
        if (trackId == null || !trackId.startsWith("yt:")) {
            return null;
        }
        if (!ensureInit()) {
            if (errorOut != null) {
                errorOut[0] = "YouTube недоступен на этом устройстве";
            }
            return null;
        }
        String lastError = null;
        for (int step = 0; step < CLIENT_ARGS.length; step++) {
            final int idx = (lastGood + step) % CLIENT_ARGS.length;
            try {
                boolean cookieOk = CLIENT_COOKIES[idx];
                if (cookieOk && !isLoggedIn()) {
                    continue;
                }
                YoutubeDLRequest r = request("https://www.youtube.com/watch?v=" + trackId.substring(3), cookieOk);
                if (CLIENT_ARGS[idx] != null) {
                    r.addOption("--extractor-args", CLIENT_ARGS[idx]);
                }
                r.addOption("-f", "ba[ext=m4a][protocol^=http]/ba[protocol^=http]/ba[protocol=m3u8_native]");
                r.addOption("--dump-json");
                r.addOption("--no-playlist");
                long t0 = System.currentTimeMillis();
                YoutubeDLResponse resp;
                synchronized (EXEC_LOCK) {
                    resp = YoutubeDL.getInstance().execute(r, bg ? "vrg-bg" : "vrg-fg", null);
                }
                String out = resp.getOut();
                String url = null;
                boolean hls = false;
                java.util.HashMap<String, String> map = null;
                if (out != null) {
                    for (String line : out.split("\n")) {
                        line = line.trim();
                        if (line.isEmpty() || line.charAt(0) != '{') {
                            continue;
                        }
                        try {
                            JSONObject o = new JSONObject(line);
                            String u = o.optString("url", "");
                            if (u.startsWith("http")) {
                                url = u;
                                String proto = o.optString("protocol", "");
                                hls = proto.contains("m3u8") || u.contains(".m3u8") || u.contains("hls_playlist");
                                try {
                                    android.net.Uri pu = android.net.Uri.parse(u);
                                    StringBuilder pn = new StringBuilder();
                                    for (String k : pu.getQueryParameterNames()) {
                                        pn.append(k).append(',');
                                    }
                                } catch (Throwable ignore) {
                                }
                                JSONObject h = o.optJSONObject("http_headers");
                                if (h != null) {
                                    map = new java.util.HashMap<>();
                                    java.util.Iterator<String> it = h.keys();
                                    while (it.hasNext()) {
                                        String k = it.next();
                                        map.put(k, h.optString(k, ""));
                                    }
                                }
                                break;
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                }
                boolean ok = url != null && (hls || probe(url, map));
                if (ok) {
                    if (map != null) {
                        HEADERS.put(url, map);
                    }
                    LAST_URL.put(trackId, url);
                    lastGood = idx;
                    if (hls) {
                        HLS_URLS.add(url);
                    }
                    return url;
                }
                if (url == null) {
                    lastError = "YouTube: не получена ссылка";
                }
            } catch (Throwable e) {
                String cname = e.getClass().getName();
                if (cname.contains("Cancel") || e instanceof InterruptedException || e.getMessage() == null) {
                    return null;
                }
                android.util.Log.e("VRGYT", "resolve client#" + idx + " failed: " + e.getMessage());
                lastError = e.getMessage();
                String low = lastError == null ? "" : lastError.toLowerCase();
                if (low.contains("unavailable") || low.contains("private") || low.contains("removed")) {
                    break;
                }
            }
        }
        if (errorOut != null) {
            errorOut[0] = friendlyError(lastError != null ? lastError : "sign in");
        }
        return null;
    }
}

