package org.telegram.vrg;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;

public final class VrgTitle {
    private VrgTitle() {
    }

    public static String firstUsername(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        if (user.username != null && !user.username.isEmpty()) {
            return user.username;
        }
        if (user.usernames != null) {
            for (TLRPC.TL_username u : user.usernames) {
                if (u != null && u.active && u.username != null && !u.username.isEmpty()) {
                    return u.username;
                }
            }
            for (TLRPC.TL_username u : user.usernames) {
                if (u != null && u.username != null && !u.username.isEmpty()) {
                    return u.username;
                }
            }
        }
        return null;
    }

    public static String mode() {
        return VrgConfig.prefs().getString("title_mode", "logo");
    }

    public static String modeLabel() {
        switch (mode()) {
            case "vrg":
                return "VRG";
            case "username":
                return "мой юзернейм";
            case "name":
                return "моё имя";
            case "custom":
                return "свой текст";
            default:
                return "логотип";
        }
    }

    public static String nextMode() {
        switch (mode()) {
            case "logo":
                return "vrg";
            case "vrg":
                return "username";
            case "username":
                return "name";
            case "name":
                return "custom";
            default:
                return "logo";
        }
    }

    public static CharSequence build(Context context, int account, View host) {
        String mode = mode();
        if ("logo".equals(mode)) {
            return null;
        }
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        String text;
        switch (mode) {
            case "vrg":
                text = "VRG";
                break;
            case "username":
                String firstUsername = firstUsername(me);
                text = firstUsername != null
                    ? ("@" + firstUsername)
                    : (me != null ? UserObject.getUserName(me) : "VRG");
                break;
            case "name":
                text = me != null ? UserObject.getUserName(me) : "VRG";
                break;
            case "custom":
                text = VrgConfig.prefs().getString("title_custom_text", "VRG");
                if (text == null || text.trim().isEmpty()) {
                    text = "VRG";
                }
                break;
            default:
                return null;
        }
        if (VrgConfig.prefs().getBoolean("title_badge", false) && me != null) {
            return VrgBadges.decorate(context, text, me.id, 20, host);
        }
        return text;
    }

    public static void applyAlign(final ActionBar bar) {
        if (bar == null) {
            return;
        }
        if (!VrgConfig.prefs().getBoolean("title_center", false) || "logo".equals(mode())) {
            bar.setAdditionalTextLeft(0);
            return;
        }
        final int[] last = {-1};
        final Runnable update = () -> {
            SimpleTextView tv = bar.getTitleTextView();
            if (tv == null || bar.getMeasuredWidth() <= 0) {
                return;
            }
            int left = (bar.getMeasuredWidth() - tv.getTextWidth()) / 2 - AndroidUtilities.dp(18);
            left = Math.max(0, left);
            if (left != last[0]) {
                last[0] = left;
                bar.setAdditionalTextLeft(left);
                bar.requestLayout();
            }
        };
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) {
                bar.post(update);
            }
        });
        bar.post(update);
    }
}
