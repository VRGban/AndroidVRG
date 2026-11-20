package org.telegram.vrg.music;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VrgMusicTypes {
    private VrgMusicTypes() {
    }

    public enum RepeatMode {
        NONE,
        ALL,
        ONE
    }

    public enum PlaybackState {
        STOPPED,
        LOADING,
        PLAYING,
        PAUSED
    }

    public static final class TrackInfo {
        public String id = "";
        public String title = "";
        public String artist = "";
        public long durationMs = 0;
        public String artworkUrl = "";
        public String streamApiUrl = "";
        public String permalinkUrl = "";

        public boolean isValid() {
            return !TextUtils.isEmpty(id) && !TextUtils.isEmpty(streamApiUrl);
        }

        public boolean isYoutube() {
            return id != null && id.startsWith("yt:");
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("title", title);
                o.put("artist", artist);
                o.put("durationMs", durationMs);
                o.put("artworkUrl", artworkUrl);
                o.put("streamApiUrl", streamApiUrl);
                o.put("permalinkUrl", permalinkUrl);
            } catch (Throwable ignore) {
            }
            return o;
        }

        public static TrackInfo fromJson(JSONObject o) {
            TrackInfo t = new TrackInfo();
            if (o == null) {
                return t;
            }
            t.id = o.optString("id", "");
            t.title = o.optString("title", "");
            t.artist = o.optString("artist", "");
            t.durationMs = o.optLong("durationMs", 0);
            t.artworkUrl = o.optString("artworkUrl", "");
            t.streamApiUrl = o.optString("streamApiUrl", "");
            t.permalinkUrl = o.optString("permalinkUrl", "");
            return t;
        }
    }

    public static final class Playlist {
        public String id = "";
        public String name = "";
        public ArrayList<TrackInfo> tracks = new ArrayList<>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                JSONArray arr = new JSONArray();
                for (int i = 0; i < tracks.size(); i++) {
                    arr.put(tracks.get(i).toJson());
                }
                o.put("tracks", arr);
            } catch (Throwable ignore) {
            }
            return o;
        }

        public static Playlist fromJson(JSONObject o) {
            Playlist p = new Playlist();
            if (o == null) {
                return p;
            }
            p.id = o.optString("id", "");
            p.name = o.optString("name", "");
            JSONArray arr = o.optJSONArray("tracks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    TrackInfo t = TrackInfo.fromJson(arr.optJSONObject(i));
                    if (t.isValid()) {
                        p.tracks.add(t);
                    }
                }
            }
            return p;
        }
    }

    public static final class EqPreset {
        public final String name;
        public final float[] gainsDb;

        public EqPreset(String name, float[] gainsDb) {
            this.name = name;
            this.gainsDb = gainsDb;
        }
    }

    public static final float[] EQ_FREQUENCIES = new float[] { 60f, 230f, 910f, 3600f, 14000f };
    public static final String[] EQ_LABELS = new String[] { "60 Гц", "230 Гц", "910 Гц", "3.6 кГц", "14 кГц" };

    public static final EqPreset[] DEFAULT_EQ_PRESETS = new EqPreset[] {
        new EqPreset("Flat", new float[] { 0f, 0f, 0f, 0f, 0f }),
        new EqPreset("Bass Boost", new float[] { 8f, 5f, 0f, 1f, 2f }),
        new EqPreset("Phonk / Trap", new float[] { 9f, 4f, -2f, 4f, 6f }),
        new EqPreset("Rock", new float[] { 5f, 2f, -2f, 3f, 5f }),
        new EqPreset("Vocal", new float[] { -3f, -1f, 4f, 5f, 2f }),
    };

    public static final class LyricLine {
        public final long timeMs;
        public final String text;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)");

    public static ArrayList<LyricLine> parseSyncedLyrics(String lrc) {
        ArrayList<LyricLine> out = new ArrayList<>();
        if (TextUtils.isEmpty(lrc)) {
            return out;
        }
        String[] lines = lrc.split("\n");
        for (String line : lines) {
            Matcher m = LRC_LINE.matcher(line.trim());
            if (m.find()) {
                try {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    String msStr = m.group(3);
                    long ms = 0;
                    if (!TextUtils.isEmpty(msStr)) {
                        if (msStr.length() == 1) ms = Long.parseLong(msStr) * 100;
                        else if (msStr.length() == 2) ms = Long.parseLong(msStr) * 10;
                        else ms = Long.parseLong(msStr);
                    }
                    String text = m.group(4) != null ? m.group(4).trim() : "";
                    if (!text.isEmpty()) {
                        out.add(new LyricLine(min * 60_000L + sec * 1000L + ms, text));
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        return out;
    }

    public static String formatTime(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return String.format(Locale.US, "%d:%02d", min, sec);
    }
}
