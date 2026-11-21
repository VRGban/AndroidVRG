package org.telegram.vrg.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.vrg.VrgConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VrgMusicPlayer {
    public interface Listener {
        default void onTrackChanged(VrgMusicTypes.TrackInfo track) {}
        default void onStateChanged(VrgMusicTypes.PlaybackState state) {}
        default void onPositionChanged(long positionMs, long durationMs) {}
        default void onLibraryChanged() {}
        default void onPlaylistsChanged() {}
        default void onTrackCached(String trackId) {}
        default void onEqChanged() {}
        default void onError(String message) {}
        default void onInfo(String message) {}
        default void onQueueChanged() {}
    }

    private static final String CHANNEL_ID = "vrg_music_playback";
    private static final int NOTIF_ID = 94721;
    private static final String ACTION_PREV = "org.telegram.vrg.music.PREV";
    private static final String ACTION_TOGGLE = "org.telegram.vrg.music.TOGGLE";
    private static final String ACTION_NEXT = "org.telegram.vrg.music.NEXT";

    private static volatile VrgMusicPlayer instance;

    private final VrgSoundCloudClient client = VrgSoundCloudClient.getInstance();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService playExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();

    private ExoPlayer exoPlayer;
    private Equalizer equalizer;
    private LoudnessEnhancer loudnessEnhancer;
    private int attachedAudioSessionId = 0;

    private final ArrayList<VrgMusicTypes.TrackInfo> queue = new ArrayList<>();
    private final ArrayList<Integer> order = new ArrayList<>();
    private int currentIndex = -1;
    private int manualAhead = 0;

    private final ArrayList<VrgMusicTypes.TrackInfo> library = new ArrayList<>();
    private final ArrayList<VrgMusicTypes.Playlist> playlists = new ArrayList<>();
    private final Set<String> downloading = Collections.synchronizedSet(new HashSet<>());

    private VrgMusicTypes.PlaybackState state = VrgMusicTypes.PlaybackState.STOPPED;
    private VrgMusicTypes.RepeatMode repeatMode = VrgMusicTypes.RepeatMode.NONE;
    private boolean shuffle = false;
    private float volume = 1.0f;
    private float boost = 0.0f;
    private boolean eqEnabled = false;
    private final float[] eqGains = new float[] { 0f, 0f, 0f, 0f, 0f };
    private int playSerial = 0;
    private final java.util.LinkedHashMap<String, byte[]> ramCache = new java.util.LinkedHashMap<>();
    private final Set<String> ramFetching = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean playingFromBytes;
    private final Set<String> ytChunkTried = new HashSet<>();
    private int prefetchedSerial = -1;
    private boolean receiverRegistered = false;

    private final Runnable positionTicker = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && (state == VrgMusicTypes.PlaybackState.PLAYING || state == VrgMusicTypes.PlaybackState.LOADING)) {
                long pos = Math.max(0L, exoPlayer.getCurrentPosition());
                long dur = getDurationMs();
                for (Listener l : listeners) {
                    l.onPositionChanged(pos, dur);
                }
                AndroidUtilities.runOnUIThread(this, 400);
            }
        }
    };

    private VrgMusicPlayer() {
        loadLibraryAndPlaylists();
        loadState();
        registerMediaReceiver();
    }

    public static VrgMusicPlayer getInstance() {
        if (instance == null) {
            synchronized (VrgMusicPlayer.class) {
                if (instance == null) {
                    instance = new VrgMusicPlayer();
                }
            }
        }
        return instance;
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void ensurePlayerOnMain() {
        if (exoPlayer != null) {
            return;
        }
        Context ctx = ApplicationLoader.applicationContext;
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();
        exoPlayer = new ExoPlayer.Builder(ctx)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build();
        exoPlayer.setVolume(volume);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    prefetchAround();
                    attachAudioEffects();
                    setState(exoPlayer.getPlayWhenReady() ? VrgMusicTypes.PlaybackState.PLAYING : VrgMusicTypes.PlaybackState.PAUSED);
                } else if (playbackState == Player.STATE_BUFFERING) {
                    setState(VrgMusicTypes.PlaybackState.LOADING);
                } else if (playbackState == Player.STATE_ENDED) {
                    handleEnded();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_READY) {
                    setState(isPlaying ? VrgMusicTypes.PlaybackState.PLAYING : VrgMusicTypes.PlaybackState.PAUSED);
                }
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                attachAudioEffects();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                FileLog.e(error);
                VrgMusicTypes.TrackInfo failing = getCurrentTrack();
                if (failing != null && failing.isYoutube()) {
                    final String failId = failing.id;
                    final String ytUrl = VrgYoutube.lastUrl(failId);
                    if (ytUrl != null && !playingFromBytes && !ytChunkTried.contains(failId)) {
                        ytChunkTried.add(failId);
                        final int serial = playSerial;
                        setState(VrgMusicTypes.PlaybackState.LOADING);
                        ioExecutor.execute(() -> {
                            byte[] bytes = client.downloadChunkedSync(ytUrl, VrgYoutube.headersFor(ytUrl));
                            AndroidUtilities.runOnUIThread(() -> {
                                if (serial != playSerial) {
                                    return;
                                }
                                if (bytes != null && bytes.length >= ytMinBytes(failing)) {
                                    ramPut(failId, bytes);
                                    startExoWithBytes(bytes, 0L, true);
                                } else {
                                    tryAlternativeYoutube(failing, serial);
                                }
                            });
                        });
                        return;
                    }
                    setState(VrgMusicTypes.PlaybackState.PAUSED);
                    notifyError(VrgYoutube.authMessage());
                    return;
                }
                setState(VrgMusicTypes.PlaybackState.PAUSED);
                notifyError("Ошибка воспроизведения: " + (error.getMessage() != null ? error.getMessage() : "поток недоступен"));
            }
        });
    }

    private static long ytMinBytes(VrgMusicTypes.TrackInfo t) {
        long sec = t != null && t.durationMs > 0 ? t.durationMs / 1000 : 60;
        return Math.max(300L * 1024, sec * 5000L);
    }

    private void tryAlternativeYoutube(final VrgMusicTypes.TrackInfo failing, final int serial) {
        setState(VrgMusicTypes.PlaybackState.LOADING);
        ioExecutor.execute(() -> {
            byte[] result = null;
            String altId = null;
            try {
                String q = (failing.artist + " " + VrgTrackMatcher.cleanTitle(failing.title)).trim();
                java.util.ArrayList<VrgMusicTypes.TrackInfo> list = VrgYoutube.searchSync(q, 8);
                int tried = 0;
                for (VrgMusicTypes.TrackInfo cand : list) {
                    if (serial != playSerial) {
                        return;
                    }
                    if (cand.id.equals(failing.id)) {
                        continue;
                    }
                    if (failing.durationMs > 0 && cand.durationMs > 0 && Math.abs(failing.durationMs - cand.durationMs) > 20000) {
                        continue;
                    }
                    if (tried++ >= 3) {
                        break;
                    }
                    String[] err = new String[1];
                    String url = VrgYoutube.resolveSync(cand.id, err);
                    if (url == null) {
                        continue;
                    }
                    byte[] bytes = client.downloadChunkedSync(url, VrgYoutube.headersFor(url));
                    if (bytes != null && bytes.length >= ytMinBytes(failing)) {
                        result = bytes;
                        altId = cand.id;
                        break;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final byte[] fResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (serial != playSerial) {
                    return;
                }
                if (fResult != null) {
                    ramPut(failing.id, fResult);
                    startExoWithBytes(fResult, 0L, true);
                } else {
                    setState(VrgMusicTypes.PlaybackState.PAUSED);
                    notifyError("YouTube не отдаёт этот трек и похожие версии (защита от ботов). Попробуй другой трек.");
                }
            });
        });
    }

    private void attachAudioEffects() {
        if (exoPlayer == null) {
            return;
        }
        int sessionId = exoPlayer.getAudioSessionId();
        if (sessionId <= 0) {
            return;
        }
        if (attachedAudioSessionId != sessionId) {
            releaseAudioEffects();
            attachedAudioSessionId = sessionId;
            try {
                equalizer = new Equalizer(0, sessionId);
            } catch (Throwable ignore) {
                equalizer = null;
            }
            try {
                loudnessEnhancer = new LoudnessEnhancer(sessionId);
            } catch (Throwable ignore) {
                loudnessEnhancer = null;
            }
        }
        applyAudioEffects();
    }

    private void applyAudioEffects() {
        if (equalizer != null) {
            try {
                equalizer.setEnabled(eqEnabled);
                if (eqEnabled) {
                    short bands = equalizer.getNumberOfBands();
                    short[] range = equalizer.getBandLevelRange();
                    short minMb = range != null && range.length >= 2 ? range[0] : -1200;
                    short maxMb = range != null && range.length >= 2 ? range[1] : 1200;
                    for (short b = 0; b < bands; b++) {
                        int idx = Math.min(4, (b * 5) / Math.max(1, bands));
                        short level = (short) Math.max(minMb, Math.min(maxMb, Math.round(eqGains[idx] * 100f)));
                        equalizer.setBandLevel(b, level);
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        if (loudnessEnhancer != null) {
            try {
                boolean active = boost > 0.01f;
                loudnessEnhancer.setEnabled(active);
                if (active) {
                    loudnessEnhancer.setTargetGain(Math.round(boost * 1200f));
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private void releaseAudioEffects() {
        try {
            if (equalizer != null) {
                equalizer.release();
                equalizer = null;
            }
        } catch (Throwable ignore) {
        }
        try {
            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
                loudnessEnhancer = null;
            }
        } catch (Throwable ignore) {
        }
        attachedAudioSessionId = 0;
    }

    public void setQueue(List<VrgMusicTypes.TrackInfo> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(tracks);
        currentIndex = Math.max(0, Math.min(startIndex, queue.size() - 1));
        rebuildOrder();
        playCurrent();
        saveStateAsync();
        notifyQueueChanged();
    }

    public void playTrack(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid()) {
            return;
        }
        ArrayList<VrgMusicTypes.TrackInfo> single = new ArrayList<>();
        single.add(track);
        setQueue(single, 0);
    }

    private void notifyQueueChanged() {
        for (Listener l : listeners) {
            l.onQueueChanged();
        }
    }

    public void enqueueNext(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid()) {
            return;
        }
        if (queue.isEmpty() || currentIndex < 0) {
            playTrack(track);
            return;
        }
        queue.add(track);
        int index = queue.size() - 1;
        int pos = Math.max(order.indexOf(currentIndex), 0);
        int at = Math.min(pos + 1 + manualAhead, order.size());
        order.add(at, index);
        manualAhead++;
        notifyQueueChanged();
        saveStateAsync();
    }

    public void enqueueLast(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid()) {
            return;
        }
        if (queue.isEmpty() || currentIndex < 0) {
            playTrack(track);
            return;
        }
        queue.add(track);
        order.add(queue.size() - 1);
        notifyQueueChanged();
        saveStateAsync();
    }

    public ArrayList<VrgMusicTypes.TrackInfo> getUpcoming() {
        ArrayList<VrgMusicTypes.TrackInfo> result = new ArrayList<>();
        if (currentIndex < 0) {
            return result;
        }
        int pos = Math.max(order.indexOf(currentIndex), 0);
        for (int i = pos + 1; i < order.size(); i++) {
            int qi = order.get(i);
            if (qi >= 0 && qi < queue.size()) {
                result.add(queue.get(qi));
            }
        }
        return result;
    }

    public int getManualAhead() {
        return manualAhead;
    }

    private void removeQueueIndex(int queueIndex) {
        if (queueIndex < 0 || queueIndex >= queue.size()) {
            return;
        }
        order.remove(Integer.valueOf(queueIndex));
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) > queueIndex) {
                order.set(i, order.get(i) - 1);
            }
        }
        queue.remove(queueIndex);
        if (currentIndex > queueIndex) {
            currentIndex--;
        }
    }

    public void removeUpcoming(int index) {
        int at = Math.max(order.indexOf(currentIndex), 0) + 1 + index;
        if (index < 0 || at >= order.size()) {
            return;
        }
        if (index < manualAhead) {
            manualAhead--;
        }
        removeQueueIndex(order.get(at));
        notifyQueueChanged();
        saveStateAsync();
    }

    public void moveUpcoming(int from, int to) {
        int base = Math.max(order.indexOf(currentIndex), 0) + 1;
        int count = order.size() - base;
        if (from < 0 || to < 0 || from >= count || to >= count || from == to) {
            return;
        }
        Integer moved = order.remove(base + from);
        order.add(base + to, moved);
        manualAhead = Math.min(manualAhead, count);
        notifyQueueChanged();
        saveStateAsync();
    }

    public void clearUpcoming() {
        if (currentIndex < 0) {
            return;
        }
        int base = Math.max(order.indexOf(currentIndex), 0) + 1;
        ArrayList<Integer> indices = new ArrayList<>();
        for (int i = base; i < order.size(); i++) {
            indices.add(order.get(i));
        }
        if (indices.isEmpty()) {
            return;
        }
        Collections.sort(indices, Collections.reverseOrder());
        for (int qi : indices) {
            removeQueueIndex(qi);
        }
        manualAhead = 0;
        notifyQueueChanged();
        saveStateAsync();
    }

    public void playUpcoming(int index) {
        int at = Math.max(order.indexOf(currentIndex), 0) + 1 + index;
        if (index < 0 || at >= order.size()) {
            return;
        }
        manualAhead = index < manualAhead ? manualAhead - index - 1 : 0;
        currentIndex = order.get(at);
        playCurrent();
        notifyQueueChanged();
        saveStateAsync();
    }

    private void rebuildOrder() {
        manualAhead = 0;
        order.clear();
        for (int i = 0; i < queue.size(); i++) {
            order.add(i);
        }
        if (shuffle && queue.size() > 1) {
            Collections.shuffle(order, new Random());
            int curPos = order.indexOf(currentIndex);
            if (curPos > 0) {
                order.remove(curPos);
                order.add(0, currentIndex);
            }
        }
    }

    private void playCurrent() {
        VrgMusicTypes.TrackInfo track = getCurrentTrack();
        if (track == null) {
            return;
        }
        final int serial = ++playSerial;
        final long tPlay = System.currentTimeMillis();
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
            } catch (Throwable ignore) {
            }
        }
        setState(VrgMusicTypes.PlaybackState.LOADING);
        for (Listener l : listeners) {
            l.onTrackChanged(track);
        }
        updateNotification();

        final byte[] inRam = ramGet(track.id);
        if (inRam != null) {
            startExoWithBytes(inRam, 0L, true);
            return;
        }
        if (isTrackCached(track.id)) {
            playExecutor.execute(() -> {
                final byte[] cachedBytes = readCachedBytes(track.id);
                AndroidUtilities.runOnUIThread(() -> {
                    if (serial != playSerial) {
                        return;
                    }
                    if (cachedBytes != null) {
                        startExoWithBytes(cachedBytes, 0L, true);
                    } else {
                        playFromNetwork(track, serial);
                    }
                });
            });
            return;
        }
        playFromNetwork(track, serial);
    }

    private void playFromNetwork(VrgMusicTypes.TrackInfo track, int serial) {
        if (track.isYoutube()) {
            notifyInfo("YouTube: готовлю трек, это может занять до 30 секунд");
        }

        client.resolveStreamUrl(track, url -> {
            if (serial != playSerial) {
                return;
            }
            startExoWithUri(Uri.parse(url));
        }, err -> {
            if (serial != playSerial) {
                return;
            }
            setState(VrgMusicTypes.PlaybackState.PAUSED);
            notifyError(err);
        });
    }

    private void startExoWithBytes(byte[] bytes, long startMs, boolean play) {
        ensurePlayerOnMain();
        try {
            playingFromBytes = true;
            androidx.media3.extractor.DefaultExtractorsFactory extractors = new androidx.media3.extractor.DefaultExtractorsFactory()
                .setMp3ExtractorFlags(androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true);
            long tIdx = System.currentTimeMillis();
            final byte[] playable = VrgMp4Index.prepare(bytes);
            ProgressiveMediaSource source = new ProgressiveMediaSource.Factory(() -> new ByteArrayDataSource(playable), extractors)
                .createMediaSource(MediaItem.fromUri(Uri.EMPTY));
            if (startMs > 0) {
                exoPlayer.setMediaSource(source, startMs);
            } else {
                exoPlayer.setMediaSource(source);
            }
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(play);
            attachAudioEffects();
        } catch (Throwable e) {
            FileLog.e(e);
            notifyError("Не удалось запустить трек");
        }
    }

    private void startExoWithUri(Uri uri) {
        ensurePlayerOnMain();
        try {
            playingFromBytes = false;
            MediaItem item = MediaItem.fromUri(uri);
            java.util.Map<String, String> ytHeaders = VrgYoutube.headersFor(uri.toString());
            if (VrgYoutube.isHls(uri.toString())) {
                androidx.media3.datasource.DefaultHttpDataSource.Factory hlsHttp = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true);
                if (ytHeaders != null) {
                    hlsHttp.setDefaultRequestProperties(ytHeaders);
                }
                MediaItem hlsItem = new MediaItem.Builder().setUri(uri).setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build();
                exoPlayer.setMediaSource(new androidx.media3.exoplayer.hls.HlsMediaSource.Factory(hlsHttp).createMediaSource(hlsItem));
            } else if (ytHeaders != null) {
                androidx.media3.datasource.DefaultHttpDataSource.Factory httpFactory = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(ytHeaders);
                exoPlayer.setMediaSource(new ProgressiveMediaSource.Factory(httpFactory).createMediaSource(item));
            } else {
                exoPlayer.setMediaItem(item);
            }
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            attachAudioEffects();
        } catch (Throwable e) {
            FileLog.e(e);
            notifyError("Не удалось запустить трек");
        }
    }

    private final java.util.Set<Object> voiceSources = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private boolean pausedByVoice;

    public static void onTelegramPlayback() {
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.messenger.MediaController mc = org.telegram.messenger.MediaController.getInstance();
            boolean active = mc.getPlayingMessageObject() != null && !mc.isMessagePaused();
            getInstance().setSourceActive("tg", active);
        }, 30);
    }

    public void setSourceActive(Object key, boolean active) {
        AndroidUtilities.runOnUIThread(() -> {
            if (active) {
                voiceSources.add(key);
            } else {
                voiceSources.remove(key);
            }
            if (!voiceSources.isEmpty()) {
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                    exoPlayer.pause();
                    pausedByVoice = true;
                    setState(VrgMusicTypes.PlaybackState.PAUSED);
                }
            } else if (pausedByVoice) {
                pausedByVoice = false;
                if (exoPlayer != null && state == VrgMusicTypes.PlaybackState.PAUSED) {
                    exoPlayer.play();
                    setState(VrgMusicTypes.PlaybackState.PLAYING);
                }
            }
        });
    }

    public void togglePlayPause() {
        AndroidUtilities.runOnUIThread(() -> {
            if (exoPlayer == null) {
                if (getCurrentTrack() != null) {
                    playCurrent();
                }
                return;
            }
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
                setState(VrgMusicTypes.PlaybackState.PAUSED);
            } else {
                if (exoPlayer.getPlaybackState() == Player.STATE_IDLE || exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
                    playCurrent();
                } else {
                    exoPlayer.play();
                    setState(VrgMusicTypes.PlaybackState.PLAYING);
                }
            }
        });
    }

    public void next() {
        if (queue.isEmpty()) {
            return;
        }
        int pos = order.indexOf(currentIndex);
        if (pos < 0) pos = 0;
        int nextPos = pos + 1;
        if (nextPos >= order.size()) {
            nextPos = 0;
            manualAhead = 0;
        } else if (manualAhead > 0) {
            manualAhead--;
        }
        currentIndex = order.get(nextPos);
        playCurrent();
        saveStateAsync();
        notifyQueueChanged();
    }

    public void prev() {
        if (queue.isEmpty()) {
            return;
        }
        if (exoPlayer != null && exoPlayer.getCurrentPosition() > 3500) {
            seek(0);
            return;
        }
        int pos = order.indexOf(currentIndex);
        if (pos < 0) pos = 0;
        int prevPos = pos - 1;
        if (prevPos < 0) {
            prevPos = order.size() - 1;
        }
        manualAhead = 0;
        currentIndex = order.get(prevPos);
        playCurrent();
        saveStateAsync();
        notifyQueueChanged();
    }

    private void handleEnded() {
        if (repeatMode == VrgMusicTypes.RepeatMode.ONE) {
            seek(0);
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(true);
            }
            return;
        }
        if (queue.isEmpty()) {
            setState(VrgMusicTypes.PlaybackState.STOPPED);
            return;
        }
        int pos = order.indexOf(currentIndex);
        if (pos + 1 < order.size()) {
            if (manualAhead > 0) {
                manualAhead--;
            }
            currentIndex = order.get(pos + 1);
            playCurrent();
            notifyQueueChanged();
        } else if (repeatMode == VrgMusicTypes.RepeatMode.ALL) {
            currentIndex = order.get(0);
            playCurrent();
        } else if (!tryRadio()) {
            setState(VrgMusicTypes.PlaybackState.PAUSED);
        }
    }

    private boolean radioBusy;

    private boolean tryRadio() {
        if (!VrgConfig.prefs().getBoolean("player_radio", true) || radioBusy) {
            return false;
        }
        final VrgMusicTypes.TrackInfo cur = getCurrentTrack();
        if (cur == null) {
            return false;
        }
        radioBusy = true;
        client.fetchRelatedTracks(cur.id, list -> {
            radioBusy = false;
            int firstNew = -1;
            if (list != null) {
                for (VrgMusicTypes.TrackInfo t : list) {
                    if (t == null || !t.isValid()) continue;
                    boolean exists = false;
                    for (VrgMusicTypes.TrackInfo q : queue) {
                        if (q.id.equals(t.id)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) continue;
                    queue.add(t);
                    order.add(queue.size() - 1);
                    if (firstNew < 0) firstNew = queue.size() - 1;
                }
            }
            if (firstNew >= 0) {
                currentIndex = firstNew;
                playCurrent();
                saveStateAsync();
                notifyQueueChanged();
            } else {
                setState(VrgMusicTypes.PlaybackState.PAUSED);
            }
        });
        return true;
    }

    public void seek(long ms) {
        if (exoPlayer == null) {
            return;
        }
        final long target = Math.max(0L, ms);
        final VrgMusicTypes.TrackInfo cur = getCurrentTrack();
        if (!playingFromBytes && cur != null) {
            final byte[] inRam = ramGet(cur.id);
            if (inRam != null) {
                final int serialAtSeek = playSerial;
                startExoWithBytes(inRam, 0L, exoPlayer.getPlayWhenReady());
                seekWhenReady(target, 40);
                AndroidUtilities.runOnUIThread(() -> {
                    if (serialAtSeek != playSerial || exoPlayer == null) {
                        return;
                    }
                    long pos = exoPlayer.getCurrentPosition();
                    if (Math.abs(pos - target) > 4000L && target > 4000L) {
                        playingFromBytes = false;
                        final boolean play = exoPlayer.getPlayWhenReady();
                        VrgMusicTypes.TrackInfo c = getCurrentTrack();
                        if (c != null) {
                            synchronized (ramCache) {
                                ramCache.remove(c.id);
                            }
                            client.resolveStreamUrl(c, url -> {
                                if (serialAtSeek != playSerial || exoPlayer == null) {
                                    return;
                                }
                                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)), target);
                                exoPlayer.prepare();
                                exoPlayer.setPlayWhenReady(play);
                            }, err -> {});
                        }
                    }
                }, 1800);
                return;
            }
        }
        if (playingFromBytes) {
            seekWhenReady(target, 40);
        } else {
            exoPlayer.seekTo(target);
        }
    }

    private void seekWhenReady(final long target, final int attempts) {
        if (exoPlayer == null) {
            return;
        }
        long dur = exoPlayer.getDuration();
        boolean ready = exoPlayer.getPlaybackState() == Player.STATE_READY;
        if (ready && (dur <= 0 || dur >= target) || attempts <= 0) {
            exoPlayer.seekTo(target);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> seekWhenReady(target, attempts - 1), 100);
    }

    private byte[] ramGet(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        synchronized (ramCache) {
            return ramCache.get(id);
        }
    }

    private void ramPut(String id, byte[] bytes) {
        final VrgMusicTypes.TrackInfo cur = getCurrentTrack();
        synchronized (ramCache) {
            ramCache.remove(id);
            ramCache.put(id, bytes);
            java.util.Iterator<String> it = ramCache.keySet().iterator();
            while (ramCache.size() > 4 && it.hasNext()) {
                String old = it.next();
                if (cur != null && old.equals(cur.id)) {
                    continue;
                }
                it.remove();
            }
        }
    }

    public void prefetchYoutubeResults(java.util.List<VrgMusicTypes.TrackInfo> results) {
        if (results == null) {
            return;
        }
        int n = 0;
        for (VrgMusicTypes.TrackInfo t : results) {
            if (t != null && t.isYoutube()) {
                prefetchRam(t);
                if (++n >= 2) {
                    break;
                }
            }
        }
    }

    private void prefetchRam(final VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid() || isTrackCached(track.id) || ramGet(track.id) != null || !ramFetching.add(track.id)) {
            return;
        }
        prefetchExecutor.execute(() -> {
            try {
                String streamUrl = client.resolveStreamUrlSync(track, true);
                if (!TextUtils.isEmpty(streamUrl)) {
                    byte[] bytes = streamUrl.contains(".m3u8")
                        ? client.downloadHlsSync(streamUrl)
                        : client.downloadBytesSync(streamUrl);
                    if (bytes != null && bytes.length > 100 * 1024) {
                        ramPut(track.id, bytes);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                ramFetching.remove(track.id);
            }
        });
    }

    private VrgMusicTypes.TrackInfo peekNextTrack() {
        if (queue.isEmpty() || order.isEmpty()) {
            return null;
        }
        int pos = order.indexOf(currentIndex);
        if (pos < 0) {
            return null;
        }
        if (pos + 1 < order.size()) {
            return queue.get(order.get(pos + 1));
        }
        if (repeatMode == VrgMusicTypes.RepeatMode.ALL) {
            return queue.get(order.get(0));
        }
        return null;
    }

    private void prefetchAround() {
        if (prefetchedSerial == playSerial) {
            return;
        }
        prefetchedSerial = playSerial;
        VrgMusicTypes.TrackInfo cur = getCurrentTrack();
        if (cur != null && !playingFromBytes) {
            prefetchRam(cur);
        }
        prefetchRam(peekNextTrack());
    }

    public long getPositionMs() {
        return exoPlayer != null ? Math.max(0L, exoPlayer.getCurrentPosition()) : 0L;
    }

    public long getDurationMs() {
        VrgMusicTypes.TrackInfo t = getCurrentTrack();
        if (t != null && t.durationMs > 0) {
            return t.durationMs;
        }
        if (exoPlayer != null && exoPlayer.getDuration() > 0) {
            return exoPlayer.getDuration();
        }
        return 0L;
    }

    public boolean isPlayerActive() {
        return exoPlayer != null && getCurrentTrack() != null;
    }

    public VrgMusicTypes.TrackInfo getCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            return queue.get(currentIndex);
        }
        return null;
    }

    public VrgMusicTypes.PlaybackState getState() {
        return state;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean enabled) {
        this.shuffle = enabled;
        rebuildOrder();
        saveStateAsync();
    }

    public VrgMusicTypes.RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void cycleRepeatMode() {
        if (repeatMode == VrgMusicTypes.RepeatMode.NONE) repeatMode = VrgMusicTypes.RepeatMode.ALL;
        else if (repeatMode == VrgMusicTypes.RepeatMode.ALL) repeatMode = VrgMusicTypes.RepeatMode.ONE;
        else repeatMode = VrgMusicTypes.RepeatMode.NONE;
        saveStateAsync();
    }

    public float getBoost() {
        return boost;
    }

    public void setBoost(float value) {
        boost = Math.max(0f, Math.min(1f, value));
        applyAudioEffects();
        for (Listener l : listeners) l.onEqChanged();
        saveStateAsync();
    }

    public boolean isEqEnabled() {
        return eqEnabled;
    }

    public void setEqEnabled(boolean enabled) {
        eqEnabled = enabled;
        applyAudioEffects();
        for (Listener l : listeners) l.onEqChanged();
        saveStateAsync();
    }

    public float[] getEqGains() {
        return eqGains.clone();
    }

    public void setEqBand(int band, float gainDb) {
        if (band < 0 || band >= 5) return;
        eqGains[band] = Math.max(-12f, Math.min(12f, gainDb));
        applyAudioEffects();
        for (Listener l : listeners) l.onEqChanged();
        saveStateAsync();
    }

    public void setEqGains(float[] gains) {
        if (gains == null || gains.length < 5) return;
        for (int i = 0; i < 5; i++) {
            eqGains[i] = Math.max(-12f, Math.min(12f, gains[i]));
        }
        applyAudioEffects();
        for (Listener l : listeners) l.onEqChanged();
        saveStateAsync();
    }

    private void setState(VrgMusicTypes.PlaybackState newState) {
        state = newState;
        AndroidUtilities.cancelRunOnUIThread(positionTicker);
        if (state == VrgMusicTypes.PlaybackState.PLAYING || state == VrgMusicTypes.PlaybackState.LOADING) {
            AndroidUtilities.runOnUIThread(positionTicker, 200);
        }
        for (Listener l : listeners) {
            l.onStateChanged(state);
        }
        updateNotification();
    }

    private void notifyInfo(String msg) {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : listeners) {
                l.onInfo(msg);
            }
        });
    }

    private void notifyError(String msg) {
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : listeners) {
                l.onError(msg);
            }
        });
    }

    public ArrayList<VrgMusicTypes.TrackInfo> getLibrary() {
        return new ArrayList<>(library);
    }

    public boolean isInLibrary(String trackId) {
        if (TextUtils.isEmpty(trackId)) return false;
        for (int i = 0; i < library.size(); i++) {
            if (trackId.equals(library.get(i).id)) return true;
        }
        return false;
    }

    public void addToLibrary(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid() || isInLibrary(track.id)) return;
        library.add(0, track);
        saveLibraryAsync();
        for (Listener l : listeners) l.onLibraryChanged();
    }

    public void removeFromLibrary(String trackId) {
        for (int i = 0; i < library.size(); i++) {
            if (trackId.equals(library.get(i).id)) {
                library.remove(i);
                saveLibraryAsync();
                for (Listener l : listeners) l.onLibraryChanged();
                return;
            }
        }
    }

    public boolean toggleLibrary(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid()) return false;
        if (isInLibrary(track.id)) {
            removeFromLibrary(track.id);
            return false;
        } else {
            addToLibrary(track);
            return true;
        }
    }

    public ArrayList<VrgMusicTypes.Playlist> getPlaylists() {
        return new ArrayList<>(playlists);
    }

    public VrgMusicTypes.Playlist findPlaylist(String id) {
        for (int i = 0; i < playlists.size(); i++) {
            if (id.equals(playlists.get(i).id)) return playlists.get(i);
        }
        return null;
    }

    public VrgMusicTypes.Playlist createPlaylist(String name) {
        VrgMusicTypes.Playlist p = new VrgMusicTypes.Playlist();
        p.id = UUID.randomUUID().toString();
        p.name = TextUtils.isEmpty(name) ? "Плейлист" : name.trim();
        playlists.add(0, p);
        saveLibraryAsync();
        for (Listener l : listeners) l.onPlaylistsChanged();
        return p;
    }

    public void deletePlaylist(String id) {
        for (int i = 0; i < playlists.size(); i++) {
            if (id.equals(playlists.get(i).id)) {
                playlists.remove(i);
                saveLibraryAsync();
                for (Listener l : listeners) l.onPlaylistsChanged();
                return;
            }
        }
    }

    public void renamePlaylist(String id, String newName) {
        VrgMusicTypes.Playlist p = findPlaylist(id);
        if (p != null && !TextUtils.isEmpty(newName)) {
            p.name = newName.trim();
            saveLibraryAsync();
            for (Listener l : listeners) l.onPlaylistsChanged();
        }
    }

    public void addTrackToPlaylist(String playlistId, VrgMusicTypes.TrackInfo track) {
        VrgMusicTypes.Playlist p = findPlaylist(playlistId);
        if (p == null || track == null || !track.isValid()) return;
        for (int i = 0; i < p.tracks.size(); i++) {
            if (track.id.equals(p.tracks.get(i).id)) return;
        }
        p.tracks.add(0, track);
        saveLibraryAsync();
        for (Listener l : listeners) l.onPlaylistsChanged();
    }

    public void moveTrackInPlaylist(String playlistId, String trackId, int delta) {
        VrgMusicTypes.Playlist p = findPlaylist(playlistId);
        if (p == null) return;
        int index = -1;
        for (int i = 0; i < p.tracks.size(); i++) {
            if (trackId.equals(p.tracks.get(i).id)) {
                index = i;
                break;
            }
        }
        int target = index + delta;
        if (index < 0 || target < 0 || target >= p.tracks.size()) return;
        VrgMusicTypes.TrackInfo moved = p.tracks.remove(index);
        p.tracks.add(target, moved);
        saveLibraryAsync();
        for (Listener l : listeners) l.onPlaylistsChanged();
    }

    public void reorderTrack(String playlistId, String movedId, String anchorId, boolean after) {
        if (movedId == null || anchorId == null || movedId.equals(anchorId)) return;
        ArrayList<VrgMusicTypes.TrackInfo> list = null;
        boolean isLibrary = playlistId == null || playlistId.isEmpty();
        if (isLibrary) {
            list = library;
        } else {
            VrgMusicTypes.Playlist p = findPlaylist(playlistId);
            if (p != null) list = p.tracks;
        }
        if (list == null) return;
        int from = -1;
        for (int i = 0; i < list.size(); i++) {
            if (movedId.equals(list.get(i).id)) {
                from = i;
                break;
            }
        }
        if (from < 0) return;
        VrgMusicTypes.TrackInfo moved = list.remove(from);
        int to = -1;
        for (int i = 0; i < list.size(); i++) {
            if (anchorId.equals(list.get(i).id)) {
                to = i;
                break;
            }
        }
        if (to < 0) {
            list.add(from, moved);
            return;
        }
        list.add(to + (after ? 1 : 0), moved);
        saveLibraryAsync();
        for (Listener l : listeners) {
            if (isLibrary) l.onLibraryChanged();
            else l.onPlaylistsChanged();
        }
    }

    public void removeTrackFromPlaylist(String playlistId, String trackId) {
        VrgMusicTypes.Playlist p = findPlaylist(playlistId);
        if (p == null) return;
        for (int i = 0; i < p.tracks.size(); i++) {
            if (trackId.equals(p.tracks.get(i).id)) {
                p.tracks.remove(i);
                saveLibraryAsync();
                for (Listener l : listeners) l.onPlaylistsChanged();
                return;
            }
        }
    }

    private static File musicDir() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "vrg_music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static final byte[] SCRAMBLE_KEY = "VRGMusicVaultKey_2026_Obfuscated!".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC = new byte[]{'V', 'R', 'G', 'M'};

    private static File cacheDir() {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "vrg_music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File legacyCacheDir() {
        return new File(musicDir(), "cache");
    }

    private static void scramble(byte[] data, int offset) {
        final int keyLen = SCRAMBLE_KEY.length;
        for (int i = offset; i < data.length; i++) {
            int j = i - offset;
            data[i] = (byte) (data[i] ^ SCRAMBLE_KEY[j % keyLen] ^ ((j * 13 + 7) & 0xff));
        }
    }

    private static String trackHash(String trackId) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(trackId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 24);
        } catch (Throwable e) {
            return Integer.toHexString(trackId.hashCode());
        }
    }

    private File cachedPath(String trackId) {
        return new File(cacheDir(), trackHash(trackId) + ".vrg");
    }

    private void migrateLegacy(String trackId) {
        File legacy = new File(legacyCacheDir(), trackId + ".mp3");
        if (!legacy.exists()) {
            return;
        }
        try {
            byte[] raw = new byte[(int) legacy.length()];
            try (FileInputStream in = new FileInputStream(legacy)) {
                int read = 0;
                while (read < raw.length) {
                    int n = in.read(raw, read, raw.length - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            if (raw.length > 1024) {
                writeCachedBytes(trackId, raw);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        legacy.delete();
    }

    private void writeCachedBytes(String trackId, byte[] bytes) throws java.io.IOException {
        byte[] out = new byte[bytes.length + MAGIC.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(bytes, 0, out, MAGIC.length, bytes.length);
        scramble(out, MAGIC.length);
        File target = cachedPath(trackId);
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(out);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new java.io.IOException("rename failed");
        }
    }

    public boolean cacheBytes(String trackId, byte[] bytes) {
        if (TextUtils.isEmpty(trackId) || bytes == null || bytes.length < 1024) {
            return false;
        }
        try {
            writeCachedBytes(trackId, bytes);
            AndroidUtilities.runOnUIThread(() -> {
                for (Listener l : listeners) l.onTrackCached(trackId);
            });
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public byte[] readCachedBytes(String trackId) {
        if (TextUtils.isEmpty(trackId)) return null;
        migrateLegacy(trackId);
        File f = cachedPath(trackId);
        if (!f.exists() || f.length() <= MAGIC.length + 1024) return null;
        try {
            byte[] raw = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int read = 0;
                while (read < raw.length) {
                    int n = in.read(raw, read, raw.length - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (raw[i] != MAGIC[i]) return null;
            }
            scramble(raw, MAGIC.length);
            byte[] payload = new byte[raw.length - MAGIC.length];
            System.arraycopy(raw, MAGIC.length, payload, 0, payload.length);
            return payload;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public boolean isTrackCached(String trackId) {
        if (TextUtils.isEmpty(trackId)) return false;
        File f = cachedPath(trackId);
        if (f.exists() && f.length() > MAGIC.length + 1024) return true;
        File legacy = new File(legacyCacheDir(), trackId + ".mp3");
        return legacy.exists() && legacy.length() > 1024;
    }

    public long getCacheSize() {
        long total = 0;
        File[] files = cacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.length();
            }
        }
        File[] old = legacyCacheDir().listFiles();
        if (old != null) {
            for (File f : old) {
                total += f.length();
            }
        }
        return total;
    }

    public long getCacheSizeFor(List<VrgMusicTypes.TrackInfo> tracks) {
        long total = 0;
        for (VrgMusicTypes.TrackInfo t : tracks) {
            File f = cachedPath(t.id);
            if (f.exists()) total += f.length();
            File legacy = new File(legacyCacheDir(), t.id + ".mp3");
            if (legacy.exists()) total += legacy.length();
        }
        return total;
    }

    public void clearCacheFor(List<VrgMusicTypes.TrackInfo> tracks) {
        for (VrgMusicTypes.TrackInfo t : tracks) {
            cachedPath(t.id).delete();
            new File(legacyCacheDir(), t.id + ".mp3").delete();
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : listeners) l.onPlaylistsChanged();
        });
    }

    public void clearCache() {
        File[] files = cacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        File[] old = legacyCacheDir().listFiles();
        if (old != null) {
            for (File f : old) {
                f.delete();
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Listener l : listeners) l.onPlaylistsChanged();
        });
    }

    public boolean isTrackDownloading(String trackId) {
        return downloading.contains(trackId);
    }

    public void cacheTrack(VrgMusicTypes.TrackInfo track) {
        if (track == null || !track.isValid() || isTrackCached(track.id) || downloading.contains(track.id)) {
            return;
        }
        downloading.add(track.id);
        ioExecutor.execute(() -> {
            try {
                String streamUrl = client.resolveStreamUrlSync(track);
                if (!TextUtils.isEmpty(streamUrl)) {
                    byte[] bytes = streamUrl.contains(".m3u8")
                        ? client.downloadHlsSync(streamUrl)
                        : client.downloadBytesSync(streamUrl);
                    if (bytes != null && bytes.length > 1024) {
                        writeCachedBytes(track.id, bytes);
                        AndroidUtilities.runOnUIThread(() -> {
                            for (Listener l : listeners) l.onTrackCached(track.id);
                        });
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                downloading.remove(track.id);
            }
        });
    }

    public void cacheTracks(List<VrgMusicTypes.TrackInfo> list) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            cacheTrack(list.get(i));
        }
    }

    private void loadLibraryAndPlaylists() {
        File f = new File(musicDir(), "library.json");
        if (!f.exists()) return;
        try {
            JSONObject root = new JSONObject(readFileText(f));
            JSONArray libArr = root.optJSONArray("library");
            if (libArr != null) {
                library.clear();
                for (int i = 0; i < libArr.length(); i++) {
                    VrgMusicTypes.TrackInfo t = VrgMusicTypes.TrackInfo.fromJson(libArr.optJSONObject(i));
                    if (t.isValid()) library.add(t);
                }
            }
            JSONArray plArr = root.optJSONArray("playlists");
            if (plArr != null) {
                playlists.clear();
                for (int i = 0; i < plArr.length(); i++) {
                    VrgMusicTypes.Playlist p = VrgMusicTypes.Playlist.fromJson(plArr.optJSONObject(i));
                    if (!TextUtils.isEmpty(p.id)) playlists.add(p);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void saveLibraryAsync() {
        final ArrayList<VrgMusicTypes.TrackInfo> libCopy = new ArrayList<>(library);
        final ArrayList<VrgMusicTypes.Playlist> plCopy = new ArrayList<>(playlists);
        ioExecutor.execute(() -> {
            try {
                JSONObject root = new JSONObject();
                JSONArray libArr = new JSONArray();
                for (VrgMusicTypes.TrackInfo t : libCopy) libArr.put(t.toJson());
                root.put("library", libArr);
                JSONArray plArr = new JSONArray();
                for (VrgMusicTypes.Playlist p : plCopy) plArr.put(p.toJson());
                root.put("playlists", plArr);
                writeFileText(new File(musicDir(), "library.json"), root.toString());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private void loadState() {
        File f = new File(musicDir(), "state.json");
        if (!f.exists()) return;
        try {
            JSONObject root = new JSONObject(readFileText(f));
            shuffle = root.optBoolean("shuffle", false);
            int rep = root.optInt("repeat", 0);
            repeatMode = rep == 1 ? VrgMusicTypes.RepeatMode.ALL : (rep == 2 ? VrgMusicTypes.RepeatMode.ONE : VrgMusicTypes.RepeatMode.NONE);
            eqEnabled = root.optBoolean("eqEnabled", false);
            boost = (float) root.optDouble("boost", 0.0);
            JSONArray eqArr = root.optJSONArray("eqGains");
            if (eqArr != null && eqArr.length() >= 5) {
                for (int i = 0; i < 5; i++) eqGains[i] = (float) eqArr.optDouble(i, 0.0);
            }
            JSONArray qArr = root.optJSONArray("queue");
            if (qArr != null) {
                queue.clear();
                for (int i = 0; i < qArr.length(); i++) {
                    VrgMusicTypes.TrackInfo t = VrgMusicTypes.TrackInfo.fromJson(qArr.optJSONObject(i));
                    if (t.isValid()) queue.add(t);
                }
                currentIndex = root.optInt("index", -1);
                if (currentIndex < 0 || currentIndex >= queue.size()) {
                    currentIndex = queue.isEmpty() ? -1 : 0;
                }
                rebuildOrder();
            }
        } catch (Throwable ignore) {
        }
    }

    private void saveStateAsync() {
        final boolean fShuffle = shuffle;
        final int fRep = repeatMode == VrgMusicTypes.RepeatMode.ALL ? 1 : (repeatMode == VrgMusicTypes.RepeatMode.ONE ? 2 : 0);
        final boolean fEq = eqEnabled;
        final float fBoost = boost;
        final float[] fGains = eqGains.clone();
        final ArrayList<VrgMusicTypes.TrackInfo> qCopy = new ArrayList<>(queue);
        final int fIndex = currentIndex;
        ioExecutor.execute(() -> {
            try {
                JSONObject root = new JSONObject();
                root.put("shuffle", fShuffle);
                root.put("repeat", fRep);
                root.put("eqEnabled", fEq);
                root.put("boost", fBoost);
                JSONArray eqArr = new JSONArray();
                for (float g : fGains) eqArr.put(g);
                root.put("eqGains", eqArr);
                JSONArray qArr = new JSONArray();
                for (int i = 0; i < Math.min(qCopy.size(), 100); i++) qArr.put(qCopy.get(i).toJson());
                root.put("queue", qArr);
                root.put("index", fIndex);
                writeFileText(new File(musicDir(), "state.json"), root.toString());
            } catch (Throwable ignore) {
            }
        });
    }

    private static String readFileText(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = fis.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeFileText(File f, String text) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void registerMediaReceiver() {
        if (receiverRegistered) return;
        receiverRegistered = true;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || intent.getAction() == null) return;
                    String a = intent.getAction();
                    if (ACTION_PREV.equals(a)) prev();
                    else if (ACTION_TOGGLE.equals(a)) togglePlayPause();
                    else if (ACTION_NEXT.equals(a)) next();
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(ACTION_PREV);
            f.addAction(ACTION_TOGGLE);
            f.addAction(ACTION_NEXT);
            if (Build.VERSION.SDK_INT >= 33) {
                ApplicationLoader.applicationContext.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ApplicationLoader.applicationContext.registerReceiver(receiver, f);
            }
        } catch (Throwable ignore) {
        }
    }

    private MediaSession mediaSession;

    private void ensureMediaSession() {
        if (mediaSession != null) {
            return;
        }
        mediaSession = new MediaSession(ApplicationLoader.applicationContext, "VrgMusic");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (state != VrgMusicTypes.PlaybackState.PLAYING) {
                    togglePlayPause();
                }
            }

            @Override
            public void onPause() {
                if (state == VrgMusicTypes.PlaybackState.PLAYING) {
                    togglePlayPause();
                }
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long pos) {
                seek(pos);
            }
        });
    }

    private void updateNotification() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            VrgMusicTypes.TrackInfo track = getCurrentTrack();
            if (track == null || state == VrgMusicTypes.PlaybackState.STOPPED) {
                VrgMusicService.stop(ctx);
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "VRG Music", NotificationManager.IMPORTANCE_LOW);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            int piFlags = Build.VERSION.SDK_INT >= 23 ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent prevPi = PendingIntent.getBroadcast(ctx, 1, new Intent(ACTION_PREV).setPackage(ctx.getPackageName()), piFlags);
            PendingIntent togglePi = PendingIntent.getBroadcast(ctx, 2, new Intent(ACTION_TOGGLE).setPackage(ctx.getPackageName()), piFlags);
            PendingIntent nextPi = PendingIntent.getBroadcast(ctx, 3, new Intent(ACTION_NEXT).setPackage(ctx.getPackageName()), piFlags);

            boolean playing = state == VrgMusicTypes.PlaybackState.PLAYING || state == VrgMusicTypes.PlaybackState.LOADING;
            Bitmap art = client.getCachedArtwork(track.artworkUrl);
            if (art == null && !TextUtils.isEmpty(track.artworkUrl)) {
                client.fetchArtwork(track.artworkUrl, bmp -> updateNotification());
            }
            ensureMediaSession();
            MediaMetadata.Builder md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Math.max(0, getDurationMs()));
            if (art != null) {
                md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
                md.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
            }
            mediaSession.setMetadata(md.build());
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, getPositionMs(), playing ? 1f : 0f)
                .build());
            mediaSession.setActive(true);

            int iconPx = AndroidUtilities.dp(24);
            Notification.Action prevAction = new Notification.Action.Builder(
                Icon.createWithBitmap(VrgGlyph.bitmap(VrgGlyph.Type.PREV, iconPx, 0xFFFFFFFF)), "Пред.", prevPi).build();
            Notification.Action toggleAction = new Notification.Action.Builder(
                Icon.createWithBitmap(VrgGlyph.bitmap(playing ? VrgGlyph.Type.PAUSE : VrgGlyph.Type.PLAY, iconPx, 0xFFFFFFFF)),
                playing ? "Пауза" : "Играть", togglePi).build();
            Notification.Action nextAction = new Notification.Action.Builder(
                Icon.createWithBitmap(VrgGlyph.bitmap(VrgGlyph.Type.NEXT, iconPx, 0xFFFFFFFF)), "След.", nextPi).build();

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.msg_filled_data_music)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(toggleAction)
                .addAction(nextAction);
            if (art != null) {
                b.setLargeIcon(art);
            }
            b.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
            VrgMusicService.post(ctx, b.build(), playing);
        } catch (Throwable ignore) {
        }
    }
}
