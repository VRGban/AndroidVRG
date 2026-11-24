package org.telegram.vrg.music;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public final class VrgMusicFromChat {
    private VrgMusicFromChat() {
    }

    public static boolean isAudioDocument(MessageObject message) {
        if (message == null) {
            return false;
        }
        TLRPC.Document doc = message.getDocument();
        if (doc == null) {
            return false;
        }
        if (doc.mime_type != null && doc.mime_type.startsWith("audio/")) {
            return true;
        }
        String name = FileLoader.getDocumentFileName(doc);
        if (name == null) {
            return false;
        }
        name = name.toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".opus") || name.endsWith(".aac");
    }

    public static void add(ChatActivity chat, MessageObject message) {
        if (chat == null || message == null || chat.getParentActivity() == null) {
            return;
        }
        final TLRPC.Document doc = message.getDocument();
        if (doc == null) {
            return;
        }
        String title = "";
        String performer = "";
        long durationSec = 0;
        for (TLRPC.DocumentAttribute attr : doc.attributes) {
            if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                title = attr.title != null ? attr.title : "";
                performer = attr.performer != null ? attr.performer : "";
                durationSec = (long) attr.duration;
            }
        }
        if (TextUtils.isEmpty(title)) {
            String name = FileLoader.getDocumentFileName(doc);
            int dot = name.lastIndexOf('.');
            title = dot > 0 ? name.substring(0, dot) : name;
        }
        final VrgMusicTypes.TrackInfo track = new VrgMusicTypes.TrackInfo();
        track.id = "tg_" + doc.id;
        track.title = title;
        track.artist = performer;
        track.durationMs = durationSec * 1000L;
        track.streamApiUrl = "tg:" + doc.id;
        track.artworkUrl = "";

        final VrgMusicPlayer player = VrgMusicPlayer.getInstance();
        final ArrayList<VrgMusicTypes.Playlist> playlists = player.getPlaylists();
        final CharSequence[] items = new CharSequence[playlists.size() + 2];
        items[0] = "Моя музыка (любимое)";
        for (int i = 0; i < playlists.size(); i++) {
            items[i + 1] = "Плейлист: " + playlists.get(i).name;
        }
        items[items.length - 1] = "+ Новый плейлист…";
        new AlertDialog.Builder(chat.getParentActivity())
            .setTitle("Добавить в VRG Music")
            .setItems(items, (d, which) -> {
                if (which == 0) {
                    download(chat, message, doc, track, () -> {
                        if (!player.isInLibrary(track.id)) {
                            player.toggleLibrary(track);
                        }
                    }, "Добавлено в «Мою музыку»");
                } else if (which == items.length - 1) {
                    promptNewPlaylist(chat, message, doc, track);
                } else {
                    final VrgMusicTypes.Playlist pl = playlists.get(which - 1);
                    download(chat, message, doc, track, () -> player.addTrackToPlaylist(pl.id, track), "Добавлено в «" + pl.name + "»");
                }
            })
            .show();
    }

    private static void promptNewPlaylist(ChatActivity chat, MessageObject message, TLRPC.Document doc, VrgMusicTypes.TrackInfo track) {
        final Context ctx = chat.getParentActivity();
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(ctx);
        android.widget.EditText edit = new android.widget.EditText(ctx);
        edit.setHint("Название плейлиста");
        edit.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        edit.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, org.telegram.ui.Components.LayoutHelper.createFrame(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT, 24, 8, 24, 8));
        new AlertDialog.Builder(ctx)
            .setTitle("Новый плейлист")
            .setView(wrap)
            .setPositiveButton("Создать", (d, w) -> {
                final String name = edit.getText() != null ? edit.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    return;
                }
                final VrgMusicPlayer player = VrgMusicPlayer.getInstance();
                final VrgMusicTypes.Playlist pl = player.createPlaylist(name);
                download(chat, message, doc, track, () -> player.addTrackToPlaylist(pl.id, track), "Добавлено в «" + pl.name + "»");
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private static void download(ChatActivity chat, MessageObject message, TLRPC.Document doc, VrgMusicTypes.TrackInfo track, Runnable onReady, String doneText) {
        final int account = chat.getCurrentAccount();
        final FileLoader loader = FileLoader.getInstance(account);
        BulletinFactory.of(chat).createSimpleBulletin(R.raw.info, "Загружаю трек…").show();
        loader.loadFile(doc, message, FileLoader.PRIORITY_HIGH, 0);
        new Thread(() -> {
            byte[] bytes = null;
            long deadline = System.currentTimeMillis() + 180_000L;
            while (System.currentTimeMillis() < deadline) {
                File f = loader.getPathToAttach(doc);
                if (f != null && f.exists() && f.length() > 1024 && (doc.size <= 0 || f.length() >= doc.size)) {
                    try (FileInputStream in = new FileInputStream(f)) {
                        bytes = new byte[(int) f.length()];
                        int read = 0;
                        while (read < bytes.length) {
                            int n = in.read(bytes, read, bytes.length - read);
                            if (n < 0) break;
                            read += n;
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                        bytes = null;
                    }
                    break;
                }
                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    return;
                }
            }
            final byte[] result = bytes;
            final boolean ok = result != null && VrgMusicPlayer.getInstance().cacheBytes(track.id, result);
            AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    onReady.run();
                    BulletinFactory.of(chat).createSimpleBulletin(R.raw.info, doneText).show();
                } else {
                    BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, "Не удалось загрузить трек").show();
                }
            });
        }, "VrgMusicFromChat").start();
    }
}

