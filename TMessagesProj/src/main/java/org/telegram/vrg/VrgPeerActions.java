package org.telegram.vrg;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

public final class VrgPeerActions {
    private VrgPeerActions() {
    }

    public static void jumpToFirstMessage(ChatActivity chatActivity) {
        if (chatActivity == null) {
            return;
        }
        final int account = chatActivity.getCurrentAccount();
        final long dialogId = chatActivity.getDialogId();
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        if (peer == null) {
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.offset_id = 1;
        req.offset_date = 0;
        req.add_offset = -1;
        req.limit = 1;
        req.max_id = 0;
        req.min_id = 0;
        req.hash = 0;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Не удалось загрузить историю чата").show();
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            if (res.messages == null || res.messages.isEmpty() || res.messages.get(0) == null) {
                BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "В этом чате пока нет сообщений").show();
                return;
            }
            int firstId = res.messages.get(0).id;
            if (firstId <= 0) {
                BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Не удалось найти первое сообщение").show();
                return;
            }
            chatActivity.scrollToMessageId(firstId, 0, true, 0, true, 0);
        }));
    }

    public static void deleteAllMyMessages(ChatActivity chatActivity) {
        if (chatActivity == null || chatActivity.getParentActivity() == null) {
            return;
        }
        final int account = chatActivity.getCurrentAccount();
        final long dialogId = chatActivity.getDialogId();
        new AlertDialog.Builder(chatActivity.getParentActivity())
            .setTitle("Удалить все мои сообщения")
            .setMessage("Удалить все ваши сообщения в этом чате (для всех, где возможно)? Отменить это действие нельзя.")
            .setPositiveButton("Удалить все", (d, w) -> {
                BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Ищу и удаляю ваши сообщения...").show();
                runDeleteStep(chatActivity, account, dialogId, 0, 0);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private static void runDeleteStep(ChatActivity chatActivity, int account, long dialogId, int offsetId, int totalDeleted) {
        MessagesController mc = MessagesController.getInstance(account);
        TLRPC.InputPeer peer = mc.getInputPeer(dialogId);
        if (peer == null) {
            return;
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.offset_id = offsetId;
        req.limit = 100;
        if (dialogId < 0) {
            req.flags |= 1;
            req.from_id = mc.getInputPeer(UserConfig.getInstance(account).getClientUserId());
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(chatActivity).createSimpleBulletin(
                        R.raw.info,
                        totalDeleted > 0 ? ("Удалено сообщений: " + totalDeleted) : "Ваших сообщений не найдено"
                    ).show();
                });
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            if (res.messages == null || res.messages.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(chatActivity).createSimpleBulletin(
                        R.raw.info,
                        totalDeleted > 0 ? ("Удалено сообщений: " + totalDeleted) : "Ваших сообщений не найдено"
                    ).show();
                });
                return;
            }
            ArrayList<Integer> myIds = new ArrayList<>();
            int minId = offsetId;
            for (int i = 0; i < res.messages.size(); i++) {
                TLRPC.Message m = res.messages.get(i);
                if (m == null) {
                    continue;
                }
                if (minId == 0 || m.id < minId) {
                    minId = m.id;
                }
                if (m.out) {
                    myIds.add(m.id);
                }
            }
            final int nextOffset = minId;
            final int nextTotal = totalDeleted + myIds.size();
            if (!myIds.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    mc.deleteMessages(myIds, null, null, dialogId, 0, true, 0);
                });
            }
            if (res.messages.size() >= 100 && nextOffset > 1 && nextOffset != offsetId) {
                runDeleteStep(chatActivity, account, dialogId, nextOffset, nextTotal);
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(chatActivity).createSimpleBulletin(
                        R.raw.info,
                        nextTotal > 0 ? ("Удалено сообщений: " + nextTotal) : "Ваших сообщений не найдено"
                    ).show();
                });
            }
        });
    }
}

