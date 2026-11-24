package org.telegram.vrg;

import android.content.Context;

import org.telegram.ui.ActionBar.AlertDialog;

public final class VrgConfirm {
    private static boolean approved = false;

    private VrgConfirm() {
    }

    public static boolean confirmSticker(Context context, Runnable proceed) {
        if (approved || context == null || !VrgConfig.isConfirmSticker()) {
            return false;
        }
        showConfirm(context, "Отправить этот стикер?", "Отправить", proceed);
        return true;
    }

    public static boolean confirmGif(Context context, Runnable proceed) {
        if (approved || context == null || !VrgConfig.isConfirmGif()) {
            return false;
        }
        showConfirm(context, "Отправить эту GIF?", "Отправить", proceed);
        return true;
    }

    public static boolean confirmCall(Context context, String peerName, boolean video, Runnable proceed) {
        if (approved || context == null || !VrgConfig.isConfirmCall()) {
            return false;
        }
        String text = (video ? "Начать видеозвонок" : "Позвонить")
            + (peerName != null && !peerName.isEmpty() ? (" " + peerName) : "") + "?";
        showConfirm(context, text, "Позвонить", proceed);
        return true;
    }

    private static void showConfirm(Context context, String text, String confirmText, Runnable proceed) {
        new AlertDialog.Builder(context)
            .setTitle("Подтверждение")
            .setMessage(text)
            .setPositiveButton(confirmText, (d, w) -> {
                approved = true;
                try {
                    if (proceed != null) {
                        proceed.run();
                    }
                } finally {
                    approved = false;
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}

