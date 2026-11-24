package org.telegram.vrg;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
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

public final class VrgSecurity {
    public static final int REQ_DEVICE_CREDENTIAL = 9751;
    private static final String MASTER_KEY = "vrg_master_hash";
    private static final Set<Long> unlockedAccounts = Collections.synchronizedSet(new HashSet<>());
    private static Runnable pendingReset;
    private static Runnable pendingFail;

    private VrgSecurity() {
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Throwable e) {
            return raw;
        }
    }

    private static String hashMaster(String pw) {
        return sha256("vrg:master:" + pw);
    }

    private static String hashAccount(long userId, String pw) {
        return sha256("vrg:acc:" + userId + ":" + pw);
    }

    private static String accKey(long userId) {
        return "lock_acc_" + userId;
    }

    public static boolean hasMaster() {
        return !TextUtils.isEmpty(VrgConfig.prefs().getString(MASTER_KEY, ""));
    }

    public static boolean verifyMaster(String pw) {
        String expected = VrgConfig.prefs().getString(MASTER_KEY, "");
        return !TextUtils.isEmpty(expected) && expected.equals(hashMaster(pw));
    }

    public static void setMaster(String pw) {
        if (TextUtils.isEmpty(pw)) {
            VrgConfig.prefs().edit().remove(MASTER_KEY).apply();
        } else {
            VrgConfig.prefs().edit().putString(MASTER_KEY, hashMaster(pw)).apply();
        }
    }

    public static boolean hasAccountLock(long userId) {
        return userId != 0 && !TextUtils.isEmpty(VrgConfig.prefs().getString(accKey(userId), ""));
    }

    public static boolean verifyAccount(long userId, String pw) {
        String expected = VrgConfig.prefs().getString(accKey(userId), "");
        return !TextUtils.isEmpty(expected) && expected.equals(hashAccount(userId, pw));
    }

    public static void setAccountLock(long userId, String pw) {
        if (TextUtils.isEmpty(pw)) {
            clearAccountLock(userId);
        } else {
            VrgConfig.prefs().edit().putString(accKey(userId), hashAccount(userId, pw)).apply();
        }
        unlockedAccounts.remove(userId);
    }

    public static void clearAccountLock(long userId) {
        VrgConfig.prefs().edit().remove(accKey(userId)).apply();
        unlockedAccounts.remove(userId);
    }

    public static boolean isAccountUnlocked(long userId) {
        return !hasAccountLock(userId) || unlockedAccounts.contains(userId);
    }

    public static void markAccountUnlocked(long userId) {
        unlockedAccounts.add(userId);
    }

    public interface Verifier {
        boolean verify(String password);
    }

    public interface Result {
        void onPassword(String password);
    }

    public static void promptPassword(Context context, String title, String hint, Verifier verifier, Result onOk, Runnable onForgot) {
        if (context == null) {
            return;
        }
        FrameLayout wrap = new FrameLayout(context);
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.LEFT, 24, 8, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(context)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Подтвердить", null)
            .setNegativeButton("Отмена", null);
        if (onForgot != null) {
            b.setNeutralButton("Забыли пароль?", (d, w) -> onForgot.run());
        }
        AlertDialog dialog = b.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = edit.getText() != null ? edit.getText().toString() : "";
            if (verifier != null && !verifier.verify(pw)) {
                edit.setError("Неверный пароль");
                AndroidUtilities.shakeView(edit);
                return;
            }
            dialog.dismiss();
            onOk.onPassword(pw);
        }));
        dialog.show();
    }

    public static void resetWithDevicePassword(Activity activity, Runnable onSuccess) {
        resetWithDevicePassword(activity, onSuccess, null);
    }

    public static void resetWithDevicePassword(Activity activity, Runnable onSuccess, Runnable onFail) {
        if (activity == null) {
            return;
        }
        KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            new AlertDialog.Builder(activity)
                .setTitle("Сброс недоступен")
                .setMessage("На телефоне не задан пароль, PIN или графический ключ, поэтому подтвердить сброс нечем.")
                .setPositiveButton("OK", null)
                .show();
            if (onFail != null) {
                onFail.run();
            }
            return;
        }
        Intent intent = km.createConfirmDeviceCredentialIntent("Сброс пароля VRG", "Подтвердите пароль или PIN телефона");
        if (intent == null) {
            return;
        }
        pendingReset = onSuccess;
        pendingFail = onFail;
        activity.startActivityForResult(intent, REQ_DEVICE_CREDENTIAL);
    }

    public static boolean onActivityResult(int requestCode, int resultCode) {
        if (requestCode != REQ_DEVICE_CREDENTIAL) {
            return false;
        }
        Runnable r = pendingReset;
        Runnable f = pendingFail;
        pendingReset = null;
        pendingFail = null;
        if (resultCode == Activity.RESULT_OK) {
            if (r != null) {
                r.run();
            }
        } else if (f != null) {
            f.run();
        }
        return true;
    }
}

