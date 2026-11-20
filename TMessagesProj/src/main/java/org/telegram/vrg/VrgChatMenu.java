package org.telegram.vrg;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public final class VrgChatMenu {
    private VrgChatMenu() {
    }

    public static void show(ChatActivity chatActivity) {
        if (chatActivity == null || chatActivity.getParentActivity() == null) {
            return;
        }
        final Context context = chatActivity.getParentActivity();
        final int account = chatActivity.getCurrentAccount();
        final long dialogId = chatActivity.getDialogId();
        final TLRPC.User user = chatActivity.getCurrentUser();
        final TLRPC.Chat chat = chatActivity.getCurrentChat();
        final String peerName = user != null
            ? UserObject.getUserName(user)
            : (chat != null && chat.title != null ? chat.title : String.valueOf(dialogId));

        final int ghostMode = VrgGhost.getReadOverride(dialogId);
        final boolean hasLock = VrgChatLock.hasPassword(dialogId);
        final long emojiStatusId = user != null
            ? DialogObject.getEmojiStatusDocumentId(user.emoji_status)
            : (chat != null ? DialogObject.getEmojiStatusDocumentId(chat.emoji_status) : 0L);

        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();

        items.add("Посмотреть удалёнки");
        icons.add(R.drawable.msg_openin);
        actions.add(() -> chatActivity.vrgOpenDeletedChat());

        items.add("Очистить удалёнки");
        icons.add(R.drawable.msg_delete);
        actions.add(() -> {
            VrgStore.getInstance().forgetPeer(account, dialogId);
            NotificationCenter.getInstance(account).postNotificationName(
                NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_ALL
            );
            BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Удалённые сообщения очищены").show();
        });

        items.add((ghostMode == VrgGhost.OVERRIDE_FORCE_READ ? "✓ " : "") + "Всегда читать в этом чате");
        icons.add(R.drawable.msg_markread);
        actions.add(() -> {
            int next = ghostMode == VrgGhost.OVERRIDE_FORCE_READ ? VrgGhost.OVERRIDE_DEFAULT : VrgGhost.OVERRIDE_FORCE_READ;
            VrgGhost.setReadOverride(dialogId, next);
            BulletinFactory.of(chatActivity).createSimpleBulletin(
                R.raw.info,
                next == VrgGhost.OVERRIDE_FORCE_READ ? "В этом чате сообщения всегда читаются" : "Исключение снято"
            ).show();
        });

        items.add((ghostMode == VrgGhost.OVERRIDE_FORCE_UNREAD ? "✓ " : "") + "Никогда не читать в этом чате");
        icons.add(R.drawable.msg_stealth_locked);
        actions.add(() -> {
            int next = ghostMode == VrgGhost.OVERRIDE_FORCE_UNREAD ? VrgGhost.OVERRIDE_DEFAULT : VrgGhost.OVERRIDE_FORCE_UNREAD;
            VrgGhost.setReadOverride(dialogId, next);
            BulletinFactory.of(chatActivity).createSimpleBulletin(
                R.raw.info,
                next == VrgGhost.OVERRIDE_FORCE_UNREAD ? "В этом чате сообщения не читаются" : "Исключение снято"
            ).show();
        });

        items.add(hasLock ? "Снять пароль с чата" : "Поставить пароль на чат");
        icons.add(R.drawable.msg_secret);
        actions.add(() -> showPasswordDialog(chatActivity, context, dialogId, peerName, hasLock));

        final boolean isLocalPinned = VrgPins.isPinned(dialogId);
        items.add(isLocalPinned ? "Открепить локально" : "Закрепить локально (без лимита)");
        icons.add(R.drawable.msg_pin);
        actions.add(() -> {
            boolean nowPinned = VrgPins.toggle(dialogId);
            MessagesController.getInstance(account).sortDialogs(null);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
            BulletinFactory.of(chatActivity).createSimpleBulletin(
                R.raw.info,
                nowPinned ? "Чат закреплён локально" : "Чат откреплён локально"
            ).show();
        });

        items.add("Быстрые шаблоны");
        icons.add(R.drawable.msg_edit);
        actions.add(() -> VrgTemplates.showPickerInChat(chatActivity));

        if (user != null && !user.self && !user.bot && VrgAutoReply.isEnabled()) {
            final boolean isIgnored = VrgAutoReply.isIgnored(dialogId);
            items.add(isIgnored ? "Включить автоответчик для чата" : "Игнорировать автоответчиком");
            icons.add(R.drawable.msg_mute);
            actions.add(() -> {
                if (isIgnored) {
                    VrgAutoReply.removeIgnored(dialogId);
                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Чат убран из исключений автоответчика").show();
                } else {
                    VrgAutoReply.addIgnored(dialogId, peerName);
                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Чат добавлен в исключения автоответчика").show();
                }
            });
        }

        if (emojiStatusId != 0) {
            items.add("Копировать ID эмодзи-статуса");
            icons.add(R.drawable.msg_copy);
            actions.add(() -> {
                AndroidUtilities.addToClipboard(String.valueOf(emojiStatusId));
                BulletinFactory.of(chatActivity).createCopyBulletin("ID статуса скопирован: " + emojiStatusId).show();
            });
        }

        items.add("Отправить видео как кружочек");
        icons.add(R.drawable.msg_video);
        actions.add(() -> VrgRoundVideo.pick(chatActivity));

        items.add("Переименовать локально");
        icons.add(R.drawable.msg_edit);
        actions.add(() -> showRenameDialog(chatActivity, context, account, dialogId, peerName));

        items.add("Перейти к первому сообщению");
        icons.add(R.drawable.msg_go_up);
        actions.add(() -> VrgPeerActions.jumpToFirstMessage(chatActivity));

        boolean canDeleteMine = chat == null || !ChatObject.isChannelAndNotMegaGroup(chat) || ChatObject.canPost(chat);
        if (canDeleteMine) {
            items.add("Удалить все мои сообщения");
            icons.add(R.drawable.msg_delete);
            actions.add(() -> VrgPeerActions.deleteAllMyMessages(chatActivity));
        }

        int[] iconArr = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) {
            iconArr[i] = icons.get(i);
        }

        new AlertDialog.Builder(context)
            .setTitle("VRG")
            .setItems(items.toArray(new CharSequence[0]), iconArr, (d, which) -> {
                if (which >= 0 && which < actions.size()) {
                    actions.get(which).run();
                }
            })
            .show();
    }

    private static void showPasswordDialog(ChatActivity chatActivity, Context context, long dialogId, String peerName, boolean hasLock) {
        FrameLayout wrap = new FrameLayout(context);
        EditText edit = new EditText(context);
        edit.setHint(hasLock ? "Введите текущий пароль" : "Введите новый пароль");
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT, 24, 8, 24, 8));

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(hasLock ? "Снять пароль с чата" : "Пароль на чат")
            .setView(wrap)
            .setPositiveButton(hasLock ? "Снять" : "Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = edit.getText() != null ? edit.getText().toString() : "";
                if (TextUtils.isEmpty(text)) {
                    AndroidUtilities.shakeView(edit);
                    return;
                }
                if (hasLock) {
                    if (!VrgChatLock.verify(dialogId, text)) {
                        edit.setError("Неверный пароль");
                        AndroidUtilities.shakeView(edit);
                        return;
                    }
                    VrgChatLock.clearPassword(dialogId);
                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Пароль снят").show();
                } else {
                    VrgChatLock.setPassword(dialogId, text, peerName);
                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.info, "Пароль установлен").show();
                }
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private static void showRenameDialog(ChatActivity chatActivity, Context context, int account, long dialogId, String currentName) {
        String existing = VrgRename.lookup(dialogId);
        FrameLayout wrap = new FrameLayout(context);
        EditText edit = new EditText(context);
        edit.setHint("Новое имя (пусто = сброс)");
        edit.setText(existing != null ? existing : currentName);
        edit.setSelection(edit.getText().length());
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT, 24, 8, 24, 8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle("Локальное имя чата")
            .setView(wrap)
            .setPositiveButton("Сохранить", (d, w) -> {
                String text = edit.getText() != null ? edit.getText().toString().trim() : "";
                VrgRename.set(dialogId, text);
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME
                );
            })
            .setNegativeButton("Отмена", null);
        if (existing != null) {
            builder.setNeutralButton("Сбросить", (d, w) -> {
                VrgRename.set(dialogId, "");
                NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces,
                    MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME
                );
            });
        }
        builder.show();
    }
}
