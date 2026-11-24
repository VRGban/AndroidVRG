package org.telegram.vrg.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.vrg.VrgConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VrgSoundCloudClient {
    private static final String HOME_URL = "https://soundcloud.com/";
    private static final String SEARCH_URL = "https://api-v2.soundcloud.com/search/tracks";
    private static final String TRACK_URL = "https://api-v2.soundcloud.com/tracks/";
    private static final String RESOLVE_URL = "https://api-v2.soundcloud.com/resolve";
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static final Pattern HYDRATED_CLIENT_ID = Pattern.compile("\"apiClient\"\\s*,\\s*\"data\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"([A-Za-z0-9]{32})\"");
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"");
    private static final Pattern JS_CLIENT_ID = Pattern.compile("client_id\\s*[:=]\\s*[\"']?([A-Za-z0-9]{32})\\b");
    private static final Pattern SPOTIFY_URL = Pattern.compile("open\\.spotify\\.com/(?:intl-[a-z-]+/)?(playlist|album|track)/([A-Za-z0-9]+)");
    private static final Pattern NEXT_DATA = Pattern.compile("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);

    private static volatile VrgSoundCloudClient instance;

    private final ExecutorService netExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService artExecutor = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> artworkCache = new LruCache<String, Bitmap>(160) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };
    private final Set<String> artworkLoading = Collections.synchronizedSet(new HashSet<>());
    private volatile String clientId = "";
    private final Object clientIdLock = new Object();

    public interface LyricsCallback {
        void onResult(String trackId, String plainLyrics, String syncedLyrics);
    }

    public interface PlaylistCallback {
        void onResult(ArrayList<VrgMusicTypes.TrackInfo> tracks, String error);
    }

    public interface SpotifyCallback {
        void onResult(ArrayList<String> queries, String error);
    }

    private VrgSoundCloudClient() {
    }

    public static VrgSoundCloudClient getInstance() {
        if (instance == null) {
            synchronized (VrgSoundCloudClient.class) {
                if (instance == null) {
                    instance = new VrgSoundCloudClient();
                }
            }
        }
        return instance;
    }

    private static final class HttpResult {
        int code;
        byte[] body;
        String error;
    }

    private HttpResult httpGet(String urlStr, boolean json, String customUa) {
        HttpResult res = new HttpResult();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", customUa != null ? customUa : USER_AGENT);
            if (json) {
                conn.setRequestProperty("Accept", "application/json");
            }
            res.code = conn.getResponseCode();
            InputStream is = res.code >= 200 && res.code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    bos.write(buf, 0, n);
                }
                res.body = bos.toByteArray();
                is.close();
            }
        } catch (Throwable e) {
            res.error = e.getMessage() != null ? e.getMessage() : "Ошибка сети";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return res;
    }

    private String ensureClientIdSync(boolean forceRefresh) {
        synchronized (clientIdLock) {
            if (!forceRefresh && !TextUtils.isEmpty(clientId)) {
                return clientId;
            }
            if (forceRefresh) {
                clientId = "";
            }
            HttpResult home = httpGet(HOME_URL, false, null);
            if (home.body == null) {
                return "";
            }
            String html = new String(home.body, StandardCharsets.UTF_8);
            Matcher hm = HYDRATED_CLIENT_ID.matcher(html);
            if (hm.find()) {
                clientId = hm.group(1);
                return clientId;
            }
            ArrayList<String> scripts = new ArrayList<>();
            Matcher sm = SCRIPT_SRC.matcher(html);
            while (sm.find()) {
                scripts.add(0, sm.group(1));
            }
            for (int i = 0; i < Math.min(scripts.size(), 10); i++) {
                HttpResult jsRes = httpGet(scripts.get(i), false, null);
                if (jsRes.body != null) {
                    String js = new String(jsRes.body, StandardCharsets.UTF_8);
                    Matcher jm = JS_CLIENT_ID.matcher(js);
                    if (jm.find()) {
                        clientId = jm.group(1);
                        return clientId;
                    }
                }
            }
            return "";
        }
    }

    private static String appendParam(String baseUrl, String key, String value) {
        Uri uri = Uri.parse(baseUrl);
        Uri.Builder b = uri.buildUpon();
        b.appendQueryParameter(key, value);
        return b.build().toString();
    }

    private final java.util.concurrent.ExecutorService ytExecutor = java.util.concurrent.Executors.newFixedThreadPool(2);

    private static String searchMode() {
        return VrgConfig.prefs().getString("search_sources", "all");
    }

    private ArrayList<VrgMusicTypes.TrackInfo> searchSoundcloudSync(String trimmed, String[] error) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String cid = ensureClientIdSync(attempt > 0);
            if (TextUtils.isEmpty(cid)) {
                error[0] = "Не удалось получить client_id SoundCloud";
                return null;
            }
            String url = Uri.parse(SEARCH_URL).buildUpon()
                .appendQueryParameter("q", trimmed)
                .appendQueryParameter("client_id", cid)
                .appendQueryParameter("limit", "25")
                .appendQueryParameter("offset", "0")
                .build().toString();
            HttpResult r = httpGet(url, true, null);
            if ((r.code == 401 || r.code == 403) && attempt == 0) {
                continue;
            }
            if (r.code != 200 || r.body == null) {
                error[0] = "Ошибка поиска SoundCloud (HTTP " + r.code + ")";
                return null;
            }
            try {
                JSONObject obj = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
                JSONArray col = obj.optJSONArray("collection");
                ArrayList<VrgMusicTypes.TrackInfo> out = new ArrayList<>();
                if (col != null) {
                    for (int i = 0; i < col.length(); i++) {
                        VrgMusicTypes.TrackInfo t = parseTrack(col.optJSONObject(i));
                        if (t.isValid()) {
                            out.add(t);
                        }
                    }
                }
                return out;
            } catch (Throwable e) {
                FileLog.e(e);
                error[0] = "Ошибка разбора ответа SoundCloud";
                return null;
            }
        }
        return null;
    }

    private static ArrayList<VrgMusicTypes.TrackInfo> interleave(ArrayList<VrgMusicTypes.TrackInfo> a, ArrayList<VrgMusicTypes.TrackInfo> b) {
        ArrayList<VrgMusicTypes.TrackInfo> out = new ArrayList<>();
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    public void searchTracks(String query, Utilities.Callback<ArrayList<VrgMusicTypes.TrackInfo>> onOk, Utilities.Callback<String> onErr) {
        final String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return;
        }
        netExecutor.execute(() -> {
            final String mode = searchMode();
            java.util.concurrent.Future<ArrayList<VrgMusicTypes.TrackInfo>> ytFuture = null;
            if (!"sc".equals(mode)) {
                ytFuture = ytExecutor.submit(() -> VrgYoutube.searchSync(trimmed, "yt".equals(mode) ? 20 : 12));
            }
            ArrayList<VrgMusicTypes.TrackInfo> sc = new ArrayList<>();
            String[] err = new String[1];
            if (!"yt".equals(mode)) {
                ArrayList<VrgMusicTypes.TrackInfo> r = searchSoundcloudSync(trimmed, err);
                if (r != null) {
                    sc = r;
                }
            }
            ArrayList<VrgMusicTypes.TrackInfo> yt = new ArrayList<>();
            if (ytFuture != null) {
                try {
                    yt = ytFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            final ArrayList<VrgMusicTypes.TrackInfo> out = interleave(sc, yt);
            if (out.isEmpty() && err[0] != null) {
                postError(onErr, err[0]);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (onOk != null) onOk.run(out);
            });
        });
    }

    public VrgMusicTypes.TrackInfo searchBestTrackSync(String line) {
        VrgTrackMatcher.Want w = VrgTrackMatcher.parse(line);
        if (TextUtils.isEmpty(w.title)) {
            return null;
        }
        final String mode = searchMode();
        VrgMusicTypes.TrackInfo best = null;
        double bestScore = -1;
        if (!"yt".equals(mode)) {
            for (String v : VrgTrackMatcher.variants(w)) {
                ArrayList<VrgMusicTypes.TrackInfo> list = searchCandidatesSync(v, 10);
                for (VrgMusicTypes.TrackInfo t : list) {
                    double sc = VrgTrackMatcher.score(w, t);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = t;
                    }
                }
                if (bestScore >= 0.85) {
                    break;
                }
            }
        }
        if (bestScore < 0.6 && !"sc".equals(mode)) {
            String q = (VrgTrackMatcher.mainArtist(w.artist) + " " + VrgTrackMatcher.cleanTitle(w.title)).trim();
            for (VrgMusicTypes.TrackInfo t : VrgYoutube.searchSync(q, 6)) {
                double sc = VrgTrackMatcher.score(w, t);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = t;
                }
            }
        }
        return bestScore >= 0.6 ? best : null;
    }

    public ArrayList<VrgMusicTypes.TrackInfo> searchCandidatesSync(String query, int limit) {
        ArrayList<VrgMusicTypes.TrackInfo> out = new ArrayList<>();
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return out;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String cid = ensureClientIdSync(attempt > 0);
            if (TextUtils.isEmpty(cid)) {
                return out;
            }
            String url = Uri.parse(SEARCH_URL).buildUpon()
                .appendQueryParameter("q", trimmed)
                .appendQueryParameter("client_id", cid)
                .appendQueryParameter("limit", String.valueOf(limit))
                .appendQueryParameter("offset", "0")
                .build().toString();
            HttpResult r = httpGet(url, true, null);
            if ((r.code == 401 || r.code == 403) && attempt == 0) {
                continue;
            }
            if (r.code != 200 || r.body == null) {
                return out;
            }
            try {
                JSONArray col = new JSONObject(new String(r.body, StandardCharsets.UTF_8)).optJSONArray("collection");
                if (col != null) {
                    for (int i = 0; i < col.length(); i++) {
                        VrgMusicTypes.TrackInfo t = parseTrack(col.optJSONObject(i));
                        if (t.isValid()) {
                            out.add(t);
                        }
                    }
                }
            } catch (Throwable ignore) {
            }
            return out;
        }
        return out;
    }

    public VrgMusicTypes.TrackInfo searchOneTrackSync(String query) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String cid = ensureClientIdSync(attempt > 0);
            if (TextUtils.isEmpty(cid)) {
                return null;
            }
            String url = Uri.parse(SEARCH_URL).buildUpon()
                .appendQueryParameter("q", trimmed)
                .appendQueryParameter("client_id", cid)
                .appendQueryParameter("limit", "3")
                .appendQueryParameter("offset", "0")
                .build().toString();
            HttpResult r = httpGet(url, true, null);
            if ((r.code == 401 || r.code == 403) && attempt == 0) {
                continue;
            }
            if (r.code != 200 || r.body == null) {
                return null;
            }
            try {
                JSONArray col = new JSONObject(new String(r.body, StandardCharsets.UTF_8)).optJSONArray("collection");
                if (col != null) {
                    VrgMusicTypes.TrackInfo best = null;
                    for (int i = 0; i < col.length(); i++) {
                        VrgMusicTypes.TrackInfo t = parseTrack(col.optJSONObject(i));
                        if (t.isValid()) {
                            best = t;
                            if (t.durationMs > 0) break;
                        }
                    }
                    return best;
                }
            } catch (Throwable ignore) {
            }
            return null;
        }
        return null;
    }

    public void resolveStreamUrl(VrgMusicTypes.TrackInfo track, Utilities.Callback<String> onUrl, Utilities.Callback<String> onErr) {
        if (track == null || !track.isValid()) {
            postError(onErr, "Трек нельзя воспроизвести");
            return;
        }
        (track.isYoutube() ? ytExecutor : netExecutor).execute(() -> {
            String resolved = resolveStreamUrlSync(track);
            if (!TextUtils.isEmpty(resolved)) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (onUrl != null) onUrl.run(resolved);
                });
            } else {
                postError(onErr, track.isYoutube() && !TextUtils.isEmpty(lastYoutubeError)
                    ? lastYoutubeError
                    : "Не удалось получить поток «" + track.title + "»");
            }
        });
    }

    public volatile String lastYoutubeError = "";

    public String resolveStreamUrlSync(VrgMusicTypes.TrackInfo track) {
        return resolveStreamUrlSync(track, false);
    }

    public String resolveStreamUrlSync(VrgMusicTypes.TrackInfo track, boolean background) {
        if (track == null || !track.isValid()) {
            return "";
        }
        if (track.isYoutube()) {
            String[] e = new String[1];
            String u = VrgYoutube.resolveSync(track.id, e, background);
            lastYoutubeError = e[0] != null ? e[0] : "";
            return u != null ? u : "";
        }
        String streamApi = track.streamApiUrl;
        for (int attempt = 0; attempt < 2; attempt++) {
            String cid = ensureClientIdSync(attempt > 0);
            if (TextUtils.isEmpty(cid)) {
                return "";
            }
            String reqUrl = appendParam(streamApi, "client_id", cid);
            HttpResult r = httpGet(reqUrl, true, null);
            if (r.code == 200 && r.body != null) {
                try {
                    String u = new JSONObject(new String(r.body, StandardCharsets.UTF_8)).optString("url", "");
                    if (!TextUtils.isEmpty(u)) {
                        return u;
                    }
                } catch (Throwable ignore) {
                }
            }
            if (attempt == 0) {

                String freshCid = ensureClientIdSync(r.code == 401 || r.code == 403);
                if (!TextUtils.isEmpty(freshCid)) {
                    HttpResult tr = httpGet(TRACK_URL + track.id + "?client_id=" + freshCid, true, null);
                    if (tr.code == 200 && tr.body != null) {
                        try {
                            VrgMusicTypes.TrackInfo fresh = parseTrack(new JSONObject(new String(tr.body, StandardCharsets.UTF_8)));
                            if (fresh.isValid()) {
                                streamApi = fresh.streamApiUrl;
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }
        return "";
    }

    public void resolvePlaylistUrl(String rawUrl, PlaylistCallback cb) {
        final String cleaned = rawUrl != null ? rawUrl.trim() : "";
        netExecutor.execute(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                String cid = ensureClientIdSync(attempt > 0);
                if (TextUtils.isEmpty(cid)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.onResult(new ArrayList<>(), "Нет client_id SoundCloud");
                    });
                    return;
                }
                String url = Uri.parse(RESOLVE_URL).buildUpon()
                    .appendQueryParameter("url", cleaned)
                    .appendQueryParameter("client_id", cid)
                    .build().toString();
                HttpResult r = httpGet(url, true, null);
                if ((r.code == 401 || r.code == 403) && attempt == 0) {
                    continue;
                }
                if (r.code != 200 || r.body == null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.onResult(new ArrayList<>(), "Ошибка SoundCloud (HTTP " + r.code + ")");
                    });
                    return;
                }
                try {
                    JSONObject obj = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
                    String kind = obj.optString("kind", "");
                    ArrayList<VrgMusicTypes.TrackInfo> out = new ArrayList<>();
                    if ("track".equals(kind)) {
                        VrgMusicTypes.TrackInfo t = parseTrack(obj);
                        if (t.isValid()) out.add(t);
                    } else if ("playlist".equals(kind)) {
                        JSONArray arr = obj.optJSONArray("tracks");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                VrgMusicTypes.TrackInfo t = parseTrack(arr.optJSONObject(i));
                                if (t.isValid()) out.add(t);
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.onResult(out, out.isEmpty() ? "Треки не найдены" : "");
                    });
                    return;
                } catch (Throwable e) {
                    FileLog.e(e);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.onResult(new ArrayList<>(), "Ошибка разбора плейлиста");
                    });
                    return;
                }
            }
        });
    }

    public void fetchSpotifyTracks(String rawUrl, SpotifyCallback cb) {
        final String cleaned = rawUrl != null ? rawUrl.trim() : "";
        Matcher m = SPOTIFY_URL.matcher(cleaned);
        if (!m.find()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (cb != null) cb.onResult(new ArrayList<>(), "Неверная ссылка Spotify");
            });
            return;
        }
        final String embedUrl = "https://open.spotify.com/embed/" + m.group(1) + "/" + m.group(2);
        netExecutor.execute(() -> {
            HttpResult r = httpGet(embedUrl, false, null);
            if (r.code != 200 || r.body == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (cb != null) cb.onResult(new ArrayList<>(), "Не удалось загрузить Spotify (HTTP " + r.code + ")");
                });
                return;
            }
            try {
                String html = new String(r.body, StandardCharsets.UTF_8);
                Matcher jm = NEXT_DATA.matcher(html);
                if (!jm.find()) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.onResult(new ArrayList<>(), "Не удалось разобрать страницу Spotify");
                    });
                    return;
                }
                JSONObject entity = new JSONObject(jm.group(1))
                    .optJSONObject("props")
                    .optJSONObject("pageProps")
                    .optJSONObject("state")
                    .optJSONObject("data")
                    .optJSONObject("entity");
                ArrayList<String> queries = new ArrayList<>();
                if (entity != null) {
                    JSONArray trackList = entity.optJSONArray("trackList");
                    if (trackList != null) {
                        for (int i = 0; i < trackList.length(); i++) {
                            JSONObject item = trackList.optJSONObject(i);
                            if (item == null) continue;
                            String title = item.optString("title", "").trim();
                            String artist = item.optString("subtitle", "").trim();
                            if (!title.isEmpty()) {
                                queries.add(artist.isEmpty() ? title : (artist + " - " + title));
                            }
                        }
                    }
                    if (queries.isEmpty()) {
                        String title = entity.optString("title", "").trim();
                        JSONArray artists = entity.optJSONArray("artists");
                        StringBuilder ab = new StringBuilder();
                        if (artists != null) {
                            for (int i = 0; i < artists.length(); i++) {
                                String n = artists.optJSONObject(i) != null ? artists.optJSONObject(i).optString("name", "").trim() : "";
                                if (!n.isEmpty()) {
                                    if (ab.length() > 0) ab.append(", ");
                                    ab.append(n);
                                }
                            }
                        }
                        if (!title.isEmpty()) {
                            queries.add(ab.length() == 0 ? title : (ab + " - " + title));
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (cb != null) cb.onResult(queries, queries.isEmpty() ? "Плейлист пуст или приватный" : "");
                });
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (cb != null) cb.onResult(new ArrayList<>(), "Ошибка разбора Spotify");
                });
            }
        });
    }

    public void fetchRelatedTracks(String trackId, Utilities.Callback<ArrayList<VrgMusicTypes.TrackInfo>> cb) {
        if (trackId != null && trackId.startsWith("yt:")) {
            AndroidUtilities.runOnUIThread(() -> {
                if (cb != null) cb.run(new ArrayList<>());
            });
            return;
        }
        if (TextUtils.isEmpty(trackId)) {
            return;
        }
        netExecutor.execute(() -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                String cid = ensureClientIdSync(attempt > 0);
                if (TextUtils.isEmpty(cid)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.run(new ArrayList<>());
                    });
                    return;
                }
                String url = TRACK_URL + trackId + "/related?client_id=" + cid + "&limit=15";
                HttpResult r = httpGet(url, true, null);
                if ((r.code == 401 || r.code == 403) && attempt == 0) {
                    continue;
                }
                ArrayList<VrgMusicTypes.TrackInfo> out = new ArrayList<>();
                if (r.code == 200 && r.body != null) {
                    try {
                        JSONArray col = new JSONObject(new String(r.body, StandardCharsets.UTF_8)).optJSONArray("collection");
                        if (col != null) {
                            for (int i = 0; i < col.length(); i++) {
                                VrgMusicTypes.TrackInfo t = parseTrack(col.optJSONObject(i));
                                if (t.isValid()) out.add(t);
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (cb != null) cb.run(out);
                });
                return;
            }
        });
    }

    public void fetchLyrics(VrgMusicTypes.TrackInfo track, LyricsCallback cb) {
        if (track == null || !track.isValid()) {
            return;
        }
        netExecutor.execute(() -> {
            String q = TextUtils.isEmpty(track.artist) ? track.title : (track.artist + " " + track.title);
            String url = Uri.parse("https://lrclib.net/api/search").buildUpon()
                .appendQueryParameter("q", q)
                .build().toString();
            HttpResult r = httpGet(url, true, "VRGMusic/1.0");
            String plain = "";
            String synced = "";
            if (r.code == 200 && r.body != null) {
                try {
                    JSONArray arr = new JSONArray(new String(r.body, StandardCharsets.UTF_8));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.optJSONObject(i);
                        if (item == null) continue;
                        String s = item.optString("syncedLyrics", "");
                        String p = item.optString("plainLyrics", "");
                        if (!TextUtils.isEmpty(s) && !"null".equals(s)) {
                            synced = s;
                            plain = !"null".equals(p) ? p : "";
                            break;
                        }
                        if (TextUtils.isEmpty(plain) && !TextUtils.isEmpty(p) && !"null".equals(p)) {
                            plain = p;
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
            final String fPlain = plain;
            final String fSynced = synced;
            AndroidUtilities.runOnUIThread(() -> {
                if (cb != null) cb.onResult(track.id, fPlain, fSynced);
            });
        });
    }

    public Bitmap getCachedArtwork(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        return artworkCache.get(url);
    }

    public void fetchArtwork(String url, Utilities.Callback<Bitmap> cb) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Bitmap cached = artworkCache.get(url);
        if (cached != null) {
            if (cb != null) cb.run(cached);
            return;
        }
        artExecutor.execute(() -> {
            java.io.File file = artFile(url);
            if (file.exists() && file.length() > 0) {
                Bitmap fromDisk = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (fromDisk != null) {
                    artworkCache.put(url, fromDisk);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.run(fromDisk);
                    });
                    return;
                }
            }
            HttpResult r = httpGet(url, false, null);
            if (r.code == 200 && r.body != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(r.body, 0, r.body.length);
                if (bmp != null) {
                    artworkCache.put(url, bmp);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        fos.write(r.body);
                    } catch (Throwable ignore) {
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (cb != null) cb.run(bmp);
                    });
                }
            }
        });
    }

    private static java.io.File artFile(String url) {
        java.io.File dir = new java.io.File(org.telegram.messenger.ApplicationLoader.getFilesDirFixed(), "vrg_music/art");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String name;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            name = sb.toString();
        } catch (Throwable e) {
            name = Integer.toHexString(url.hashCode());
        }
        return new java.io.File(dir, name + ".img");
    }

    public byte[] downloadBytesSync(String url) {
        java.util.Map<String, String> ytHeaders = VrgYoutube.headersFor(url);
        if (ytHeaders != null) {
            return downloadWithHeadersSync(url, ytHeaders);
        }
        HttpResult r = httpGet(url, false, null);
        return (r.code >= 200 && r.code < 300) ? r.body : null;
    }

    private byte[] chunkedAttempt(String urlStr, java.util.Map<String, String> headers, int chunk, boolean queryRange) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        long start = 0;
        long total = -1;
        try {
            while (total < 0 || start < total) {
                long end = start + chunk - 1;
                String target = queryRange
                    ? urlStr + (urlStr.contains("?") ? "&" : "?") + "range=" + start + "-" + end
                    : urlStr;
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                if (headers != null) {
                    for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                        String k = e.getKey();
                        if (!"Range".equalsIgnoreCase(k) && !"Accept-Encoding".equalsIgnoreCase(k)) {
                            conn.setRequestProperty(k, e.getValue());
                        }
                    }
                }
                if (!queryRange) {
                    conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
                }
                int code = conn.getResponseCode();
                if (code != 200 && code != 206) {
                    conn.disconnect();
                    return null;
                }
                String cr = conn.getHeaderField("Content-Range");
                InputStream is = conn.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                long got = 0;
                while ((n = is.read(buf)) >= 0) {
                    all.write(buf, 0, n);
                    got += n;
                }
                is.close();
                conn.disconnect();
                if (code == 200 && !queryRange) {
                    return all.toByteArray();
                }
                if (cr != null && cr.contains("/")) {
                    try {
                        total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                    } catch (Throwable ignore) {
                        total = -1;
                    }
                }
                if (total < 0) {
                    String clen = Uri.parse(urlStr).getQueryParameter("clen");
                    if (clen != null) {
                        try {
                            total = Long.parseLong(clen);
                        } catch (Throwable ignore) {
                        }
                    }
                }
                if (got <= 0) {
                    break;
                }
                start += got;
                if (total < 0 && got < chunk) {
                    break;
                }
            }
            return all.size() > 0 ? all.toByteArray() : null;
        } catch (Throwable e) {
            return null;
        }
    }

    public byte[] downloadChunkedSync(String urlStr, java.util.Map<String, String> headers) {
        int[] sizes = {1024 * 1024};
        for (boolean q : new boolean[]{true, false}) {
            for (int size : sizes) {
                byte[] r = chunkedAttempt(urlStr, headers, size, q);
                if (r != null && r.length > 100 * 1024) {
                    return r;
                }
            }
        }
        return null;
    }

    private byte[] downloadWithHeadersSync(String urlStr, java.util.Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                if (!"Range".equalsIgnoreCase(e.getKey()) && !"Accept-Encoding".equalsIgnoreCase(e.getKey())) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setRequestProperty("Range", "bytes=0-");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return downloadChunkedSync(urlStr, headers);
            }
            InputStream is = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toByteArray();
        } catch (Throwable e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public byte[] downloadHlsSync(String playlistUrl) {
        return downloadHlsSync(playlistUrl, 0);
    }

    private byte[] downloadHlsSync(String playlistUrl, int depth) {
        if (depth > 2) {
            return null;
        }
        HttpResult r = httpGet(playlistUrl, false, null);
        if (r.code < 200 || r.code >= 300 || r.body == null) {
            return null;
        }
        String text = new String(r.body, java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n");
        java.net.URL base;
        try {
            base = new java.net.URL(playlistUrl);
        } catch (Throwable e) {
            return null;
        }
        boolean master = text.contains("#EXT-X-STREAM-INF");
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        String mapUri = null;
        boolean nextIsVariant = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#EXT-X-MAP:")) {
                int i = line.indexOf("URI=\"");
                if (i >= 0) {
                    int j = line.indexOf('"', i + 5);
                    if (j > i) {
                        mapUri = line.substring(i + 5, j);
                    }
                }
                continue;
            }
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                nextIsVariant = true;
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            if (master) {
                if (nextIsVariant) {
                    try {
                        return downloadHlsSync(new java.net.URL(base, line).toString(), depth + 1);
                    } catch (Throwable e) {
                        return null;
                    }
                }
                continue;
            }
            segments.add(line);
        }
        if (segments.isEmpty()) {
            return null;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            if (mapUri != null) {
                byte[] init = downloadBytesSync(new java.net.URL(base, mapUri).toString());
                if (init == null) {
                    return null;
                }
                out.write(init);
            }
            for (String seg : segments) {
                byte[] part = downloadBytesSync(new java.net.URL(base, seg).toString());
                if (part == null) {
                    return null;
                }
                out.write(part);
            }
        } catch (Throwable e) {
            return null;
        }
        return out.toByteArray();
    }

    private static VrgMusicTypes.TrackInfo parseTrack(JSONObject obj) {
        VrgMusicTypes.TrackInfo t = new VrgMusicTypes.TrackInfo();
        if (obj == null || !"track".equals(obj.optString("kind", ""))) {
            return t;
        }
        String policy = obj.optString("policy", "");
        if ("BLOCK".equals(policy) || "SNIP".equals(policy)) {
            return t;
        }
        long idNum = obj.optLong("id", 0);
        if (idNum <= 0) {
            return t;
        }
        t.id = String.valueOf(idNum);
        t.title = obj.optString("title", "");
        JSONObject user = obj.optJSONObject("user");
        JSONObject pub = obj.optJSONObject("publisher_metadata");
        String pubArtist = pub != null ? pub.optString("artist", "") : "";
        if (!TextUtils.isEmpty(pubArtist) && !"null".equals(pubArtist)) {
            t.artist = pubArtist;
        } else if (user != null) {
            t.artist = user.optString("username", "");
        }
        long fullDur = obj.optLong("full_duration", 0);
        t.durationMs = fullDur > 0 ? fullDur : obj.optLong("duration", 0);
        String art = obj.optString("artwork_url", "");
        if (TextUtils.isEmpty(art) || "null".equals(art)) {
            art = user != null ? user.optString("avatar_url", "") : "";
        }
        t.artworkUrl = !TextUtils.isEmpty(art) ? art.replace("-large.", "-t500x500.") : "";
        t.permalinkUrl = obj.optString("permalink_url", "");
        t.streamApiUrl = pickTranscoding(obj);
        return t;
    }

    private static String pickTranscoding(JSONObject track) {
        JSONObject media = track.optJSONObject("media");
        JSONArray trans = media != null ? media.optJSONArray("transcodings") : null;
        if (trans == null) {
            return "";
        }
        String chosen = "";
        int chosenRank = Integer.MAX_VALUE;
        for (int i = 0; i < trans.length(); i++) {
            JSONObject tr = trans.optJSONObject(i);
            if (tr == null || tr.optBoolean("snipped", false)) {
                continue;
            }
            String url = tr.optString("url", "");
            JSONObject fmt = tr.optJSONObject("format");
            if (TextUtils.isEmpty(url) || fmt == null) {
                continue;
            }
            String protocol = fmt.optString("protocol", "");
            String mime = fmt.optString("mime_type", "").split(";")[0].trim();
            String preset = tr.optString("preset", "");
            int rank = -1;
            if ("audio/mpeg".equals(mime)) {
                rank = "progressive".equals(protocol) ? 0 : ("hls".equals(protocol) ? 1 : -1);
            } else if ("hls".equals(protocol)) {
                if ("audio/mp4".equals(mime)) {
                    rank = preset.contains("160") ? 2 : 3;
                } else if ("audio/ogg".equals(mime)) {
                    rank = 4;
                }
            }
            if (rank >= 0 && rank < chosenRank) {
                chosenRank = rank;
                chosen = url;
            }
        }
        if (TextUtils.isEmpty(chosen)) {
            return "";
        }
        String auth = track.optString("track_authorization", "");
        if (!TextUtils.isEmpty(auth) && !"null".equals(auth)) {
            chosen = appendParam(chosen, "track_authorization", auth);
        }
        return chosen;
    }

    private static void postError(Utilities.Callback<String> onErr, String msg) {
        if (onErr != null) {
            AndroidUtilities.runOnUIThread(() -> onErr.run(msg));
        }
    }
}

