package org.telegram.vrg;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;

public final class VrgRoundVideo {
    public static final int REQUEST_PICK = 9731;
    private static final int RESULT_SIZE = 384;
    private static final long MAX_DURATION_MS = 60_000;

    private VrgRoundVideo() {
    }

    public static void pick(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        chat.startActivityForResult(Intent.createChooser(intent, "Выбрать видео для кружочка"), REQUEST_PICK);
    }

    public static boolean handleResult(ChatActivity chat, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK) {
            return false;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        final Uri uri = data.getData();
        final long dialogId = chat.getDialogId();
        final int account = chat.getCurrentAccount();
        new Thread(() -> {
            String path = MediaController.copyFileToCache(uri, "mp4");
            if (path == null) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, "Не удалось открыть видео").show());
                return;
            }
            int width = 0;
            int height = 0;
            int rotation = 0;
            long duration = 0;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(path);
                width = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                height = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                rotation = rot == null ? 0 : Integer.parseInt(rot);
                duration = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    mmr.release();
                } catch (Throwable ignore) {
                }
            }
            if (width <= 0 || height <= 0 || duration <= 0) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, "Не удалось прочитать видео").show());
                return;
            }
            if (duration > MAX_DURATION_MS) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(chat).createSimpleBulletin(R.raw.error, "Кружочек не может быть длиннее 60 секунд").show());
                return;
            }
            final boolean swap = rotation == 90 || rotation == 270;
            final int shownW = swap ? height : width;
            final int shownH = swap ? width : height;
            final int side = Math.min(shownW, shownH);

            VideoEditedInfo info = new VideoEditedInfo();
            info.originalPath = path;
            info.startTime = -1;
            info.endTime = -1;
            info.estimatedDuration = duration;
            info.originalDuration = duration;
            info.originalWidth = width;
            info.originalHeight = height;
            info.rotationValue = rotation;
            info.resultWidth = RESULT_SIZE;
            info.resultHeight = RESULT_SIZE;
            info.bitrate = 1_000_000;
            info.framerate = 30;
            info.roundVideo = true;
            info.estimatedSize = Math.max(1L, new File(path).length() / 2);

            MediaController.CropState crop = new MediaController.CropState();
            crop.cropPx = 0;
            crop.cropPy = 0;
            crop.cropScale = 1;
            crop.cropRotate = 0;
            crop.cropPw = (float) side / shownW;
            crop.cropPh = (float) side / shownH;
            crop.transformWidth = RESULT_SIZE;
            crop.transformHeight = RESULT_SIZE;
            crop.transformRotation = 0;
            crop.mirrored = false;
            info.cropState = crop;

            final String finalPath = path;
            AndroidUtilities.runOnUIThread(() -> {
                SendMessagesHelper.prepareSendingVideo(AccountInstance.getInstance(account), finalPath, info, null, null, dialogId, null, null, null, null, null, 0, null, true, 0, 0, false, false, null, null, 0, 0);
                BulletinFactory.of(chat).createSimpleBulletin(R.raw.info, "Кружочек отправляется…").show();
            });
        }, "VrgRoundVideo").start();
        return true;
    }
}
