package org.telegram.vrg;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VrgChatLock {
    private static final Set<Long> unlocked = Collections.synchronizedSet(new HashSet<>());

    private VrgChatLock() {
    }

    private static String key(long dialogId) {
        return "lock_chat_" + dialogId;
    }

    private static String nameKey(long dialogId) {
        return "lock_chat_name_" + dialogId;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable e) {
            return input;
        }
    }

    public static boolean hasPassword(long dialogId) {
        return !TextUtils.isEmpty(VrgConfig.prefs().getString(key(dialogId), ""));
    }

    public static boolean isUnlocked(long dialogId) {
        return unlocked.contains(dialogId);
    }

    public static void unlock(long dialogId) {
        unlocked.add(dialogId);
    }

    public static void lockAll() {
        unlocked.clear();
    }

    public static void setPassword(long dialogId, String password, String title) {
        if (TextUtils.isEmpty(password)) {
            clearPassword(dialogId);
            return;
        }
        VrgConfig.prefs().edit()
            .putString(key(dialogId), sha256(password))
            .putString(nameKey(dialogId), title != null ? title : "")
            .apply();
        unlocked.remove(dialogId);
    }

    public static void clearPassword(long dialogId) {
        VrgConfig.prefs().edit()
            .remove(key(dialogId))
            .remove(nameKey(dialogId))
            .apply();
        unlocked.remove(dialogId);
    }

    public static final class LockedEntry {
        public final long dialogId;
        public final String title;

        public LockedEntry(long dialogId, String title) {
            this.dialogId = dialogId;
            this.title = title;
        }
    }

    public static java.util.ArrayList<LockedEntry> listLocked() {
        java.util.ArrayList<LockedEntry> out = new java.util.ArrayList<>();
        java.util.Map<String, ?> all = VrgConfig.prefs().getAll();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("lock_chat_") && !k.startsWith("lock_chat_name_") && e.getValue() instanceof String && !TextUtils.isEmpty((String) e.getValue())) {
                try {
                    long did = Long.parseLong(k.substring("lock_chat_".length()));
                    String title = VrgConfig.prefs().getString(nameKey(did), "");
                    if (TextUtils.isEmpty(title)) {
                        title = String.valueOf(did);
                    }
                    out.add(new LockedEntry(did, title));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return out;
    }

    public static boolean verify(long dialogId, String password) {
        if (TextUtils.isEmpty(password)) {
            return false;
        }
        String expected = VrgConfig.prefs().getString(key(dialogId), "");
        return !TextUtils.isEmpty(expected) && expected.equals(sha256(password));
    }

    public static void promptUnlock(Context context, long dialogId, Runnable onSuccess) {
        promptUnlock(context, dialogId, onSuccess, null);
    }

    public static void promptUnlock(Context context, long dialogId, Runnable onSuccess, Runnable onCancel) {
        if (context == null || !hasPassword(dialogId) || isUnlocked(dialogId)) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }
        final boolean[] done = new boolean[1];
        FrameLayout wrap = new FrameLayout(context);
        EditText edit = new EditText(context);
        edit.setHint("Введите пароль от чата");
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT, 24, 8, 24, 8));

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("Чат защищён паролем")
            .setView(wrap)
            .setPositiveButton("Открыть", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Забыли пароль?", (d, w) -> {
                done[0] = true;
                if (context instanceof android.app.Activity) {
                    VrgSecurity.resetWithDevicePassword((android.app.Activity) context, () -> {
                        clearPassword(dialogId);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }, onCancel);
                } else if (onCancel != null) {
                    onCancel.run();
                }
            })
            .create();
        dialog.setOnDismissListener(d -> {
            if (!done[0] && onCancel != null) {
                onCancel.run();
            }
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pass = edit.getText() != null ? edit.getText().toString() : "";
                if (verify(dialogId, pass)) {
                    unlock(dialogId);
                    done[0] = true;
                    dialog.dismiss();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } else {
                    edit.setError("Неверный пароль");
                    AndroidUtilities.shakeView(edit);
                }
            });
        });
        dialog.show();
    }
}

