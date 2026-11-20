package org.telegram.vrg;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VrgEmojiIdHelper {
    private VrgEmojiIdHelper() {
    }

    public static LinkedHashMap<Long, String> extract(MessageObject msg) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        if (msg == null || msg.messageOwner == null || msg.messageOwner.entities == null) {
            return result;
        }
        String text = msg.messageOwner.message != null ? msg.messageOwner.message : "";
        for (int i = 0; i < msg.messageOwner.entities.size(); i++) {
            TLRPC.MessageEntity entity = msg.messageOwner.entities.get(i);
            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                long docId = ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id;
                if (docId != 0 && !result.containsKey(docId)) {
                    String alt = "";
                    if (entity.offset >= 0 && entity.offset + entity.length <= text.length()) {
                        alt = text.substring(entity.offset, entity.offset + entity.length);
                    }
                    result.put(docId, alt);
                }
            }
        }
        return result;
    }

    public static boolean hasCustomEmojis(MessageObject msg) {
        return !extract(msg).isEmpty();
    }

    public static void showOrCopy(ChatActivity chatActivity, MessageObject msg) {
        if (chatActivity == null || chatActivity.getParentActivity() == null) {
            return;
        }
        LinkedHashMap<Long, String> map = extract(msg);
        if (map.isEmpty()) {
            return;
        }
        if (map.size() == 1) {
            long id = map.keySet().iterator().next();
            String idStr = String.valueOf(id);
            AndroidUtilities.addToClipboard(idStr);
            BulletinFactory.of(chatActivity).createCopyBulletin("ID эмодзи скопирован: " + idStr).show();
            return;
        }
        Context context = chatActivity.getParentActivity();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> idStrings = new ArrayList<>();
        for (Map.Entry<Long, String> entry : map.entrySet()) {
            String idStr = String.valueOf(entry.getKey());
            String alt = entry.getValue();
            labels.add(TextUtils.isEmpty(alt) ? idStr : (alt + "  —  " + idStr));
            idStrings.add(idStr);
        }
        new AlertDialog.Builder(context)
            .setTitle("ID кастомных эмодзи")
            .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                if (which >= 0 && which < idStrings.size()) {
                    String idStr = idStrings.get(which);
                    AndroidUtilities.addToClipboard(idStr);
                    BulletinFactory.of(chatActivity).createCopyBulletin("Скопировано: " + idStr).show();
                }
            })
            .setNeutralButton("Скопировать все", (d, w) -> {
                AndroidUtilities.addToClipboard(TextUtils.join(", ", idStrings));
                BulletinFactory.of(chatActivity).createCopyBulletin("Все ID эмодзи скопированы").show();
            })
            .setPositiveButton("Закрыть", null)
            .show();
    }
}
