package org.telegram.vrg;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class VrgConfig {
    private static final String PREFS_NAME = "vrg_config";

    private static volatile SharedPreferences prefs;

    private VrgConfig() {
    }

    public static SharedPreferences prefs() {
        if (prefs == null) {
            synchronized (VrgConfig.class) {
                if (prefs == null) {
                    prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                }
            }
        }
        return prefs;
    }

    public static boolean isGhostRead() {
        return prefs().getBoolean("ghost_read", false);
    }

    public static void setGhostRead(boolean value) {
        prefs().edit().putBoolean("ghost_read", value).apply();
    }

    public static boolean isGhostTyping() {
        return prefs().getBoolean("ghost_typing", false);
    }

    public static void setGhostTyping(boolean value) {
        prefs().edit().putBoolean("ghost_typing", value).apply();
    }

    public static boolean isGhostOnline() {
        return prefs().getBoolean("ghost_online", false);
    }

    public static void setGhostOnline(boolean value) {
        prefs().edit().putBoolean("ghost_online", value).apply();
    }

    public static boolean isGhostStories() {
        return prefs().getBoolean("ghost_stories", false);
    }

    public static void setGhostStories(boolean value) {
        prefs().edit().putBoolean("ghost_stories", value).apply();
    }

    public static boolean isGhostOfflineAfterSend() {
        return prefs().getBoolean("ghost_offline_send", false);
    }

    public static void setGhostOfflineAfterSend(boolean value) {
        prefs().edit().putBoolean("ghost_offline_send", value).apply();
    }

    public static boolean isGhostSilentSend() {
        return prefs().getBoolean("ghost_silent_send", false);
    }

    public static void setGhostSilentSend(boolean value) {
        prefs().edit().putBoolean("ghost_silent_send", value).apply();
    }

    public static boolean isGhostVoiceContents() {
        return prefs().getBoolean("ghost_voice_contents", false);
    }

    public static void setGhostVoiceContents(boolean value) {
        prefs().edit().putBoolean("ghost_voice_contents", value).apply();
    }

    public static boolean isGhostReadOnSend() {
        return prefs().getBoolean("ghost_read_on_send", false);
    }

    public static void setGhostReadOnSend(boolean value) {
        prefs().edit().putBoolean("ghost_read_on_send", value).apply();
    }

    public static boolean isFullGhost() {
        return isGhostRead()
            && isGhostTyping()
            && isGhostOnline()
            && isGhostOfflineAfterSend()
            && isGhostStories();
    }

    public static void setFullGhost(boolean enabled) {
        prefs().edit()
            .putBoolean("ghost_read", enabled)
            .putBoolean("ghost_typing", enabled)
            .putBoolean("ghost_online", enabled)
            .putBoolean("ghost_offline_send", enabled)
            .putBoolean("ghost_stories", enabled)
            .apply();
    }

    public static boolean isKeepDeleted() {
        return prefs().getBoolean("keep_deleted", true);
    }

    public static void setKeepDeleted(boolean value) {
        prefs().edit().putBoolean("keep_deleted", value).apply();
    }

    public static boolean isBackgroundAntiDelete() {
        return prefs().getBoolean("bg_anti_delete", true);
    }

    public static void setBackgroundAntiDelete(boolean value) {
        prefs().edit().putBoolean("bg_anti_delete", value).apply();
    }

    public static String getDeletedMark() {
        String mark = prefs().getString("deleted_mark", "icon");
        if ("text".equals(mark) || "none".equals(mark)) {
            return mark;
        }
        return "icon";
    }

    public static void setDeletedMark(String mark) {
        prefs().edit().putString("deleted_mark", mark).apply();
    }

    public static int getDeletedDim() {
        return prefs().getInt("deleted_dim", 0);
    }

    public static void setDeletedDim(int dim) {
        prefs().edit().putInt("deleted_dim", Math.max(0, Math.min(2, dim))).apply();
    }

    public static float getDeletedOpacity() {
        int dim = getDeletedDim();
        if (dim >= 2) {
            return 0.5f;
        }
        if (dim == 1) {
            return 0.75f;
        }
        return 1.0f;
    }

    public static boolean isKeepEdited() {
        return prefs().getBoolean("keep_edited", true);
    }

    public static void setKeepEdited(boolean value) {
        prefs().edit().putBoolean("keep_edited", value).apply();
    }

    public static boolean isBypassNoforwards() {
        return prefs().getBoolean("bypass_noforwards", true);
    }

    public static void setBypassNoforwards(boolean value) {
        prefs().edit().putBoolean("bypass_noforwards", value).apply();
    }

    public static boolean isDisableAds() {
        return prefs().getBoolean("disable_ads", true);
    }

    public static void setDisableAds(boolean value) {
        prefs().edit().putBoolean("disable_ads", value).apply();
    }

    public static boolean isShowPeerId() {
        return prefs().getBoolean("show_peer_id", true);
    }

    public static void setShowPeerId(boolean value) {
        prefs().edit().putBoolean("show_peer_id", value).apply();
    }

    public static boolean isKeepTtl() {
        return prefs().getBoolean("keep_ttl", true);
    }

    public static void setKeepTtl(boolean value) {
        prefs().edit().putBoolean("keep_ttl", value).apply();
    }

    public static boolean isWelcomeShown() {
        return prefs().getBoolean("vrg_welcome_shown", false);
    }

    public static void setWelcomeShown(boolean value) {
        prefs().edit().putBoolean("vrg_welcome_shown", value).apply();
    }

    public static boolean isVrgDarkApplied() {
        return prefs().getBoolean("vrg_dark_applied", false);
    }

    public static void setVrgDarkApplied(boolean value) {
        prefs().edit().putBoolean("vrg_dark_applied", value).apply();
    }

    public static boolean isLocalPremium() {
        return prefs().getBoolean("premium_badge", false);
    }

    public static void setLocalPremium(boolean value) {
        prefs().edit().putBoolean("premium_badge", value).apply();
    }

    public static boolean isHideSimilarChannels() {
        return prefs().getBoolean("hide_similar_channels", true);
    }

    public static void setHideSimilarChannels(boolean value) {
        prefs().edit().putBoolean("hide_similar_channels", value).apply();
    }

    public static String getDeletedMarkColor() {
        String val = prefs().getString("deleted_mark_color", "default");
        return val != null ? val : "default";
    }

    public static void setDeletedMarkColor(String hex) {
        prefs().edit().putString("deleted_mark_color", hex != null ? hex : "default").apply();
    }

    public static int getDeletedMarkColorInt() {
        String hex = getDeletedMarkColor();
        if (hex == null || hex.isEmpty() || "default".equals(hex)) {
            return 0;
        }
        try {
            return android.graphics.Color.parseColor(hex);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static boolean isHideStories() {
        return prefs().getBoolean("hide_stories", false);
    }

    public static void setHideStories(boolean value) {
        prefs().edit().putBoolean("hide_stories", value).apply();
    }

    public static boolean isConfirmCall() {
        return prefs().getBoolean("confirm_call", false);
    }

    public static void setConfirmCall(boolean value) {
        prefs().edit().putBoolean("confirm_call", value).apply();
    }

    public static boolean isConfirmSticker() {
        return prefs().getBoolean("confirm_sticker", false);
    }

    public static void setConfirmSticker(boolean value) {
        prefs().edit().putBoolean("confirm_sticker", value).apply();
    }

    public static boolean isConfirmGif() {
        return prefs().getBoolean("confirm_gif", false);
    }

    public static void setConfirmGif(boolean value) {
        prefs().edit().putBoolean("confirm_gif", value).apply();
    }

    public static boolean isHideServiceMessages() {
        return prefs().getBoolean("hide_service_messages", false);
    }

    public static void setHideServiceMessages(boolean value) {
        prefs().edit().putBoolean("hide_service_messages", value).apply();
    }

    public static boolean isHideComposeGifts() {
        return prefs().getBoolean("hide_compose_gifts", false);
    }

    public static void setHideComposeGifts(boolean value) {
        prefs().edit().putBoolean("hide_compose_gifts", value).apply();
    }

    public static boolean isHideComposeScheduled() {
        return prefs().getBoolean("hide_compose_scheduled", false);
    }

    public static void setHideComposeScheduled(boolean value) {
        prefs().edit().putBoolean("hide_compose_scheduled", value).apply();
    }

    public static boolean isHideComposeSilent() {
        return prefs().getBoolean("hide_compose_silent", false);
    }

    public static void setHideComposeSilent(boolean value) {
        prefs().edit().putBoolean("hide_compose_silent", value).apply();
    }

    public static boolean isHideComposeBot() {
        return prefs().getBoolean("hide_compose_bot", false);
    }

    public static void setHideComposeBot(boolean value) {
        prefs().edit().putBoolean("hide_compose_bot", value).apply();
    }

    public static boolean isShowSeconds() {
        return prefs().getBoolean("show_seconds", false);
    }

    public static void setShowSeconds(boolean value) {
        prefs().edit().putBoolean("show_seconds", value).apply();
    }

    public static boolean isMenuShowVrgPlayer() {
        return prefs().getBoolean("menu_vrg_player", true);
    }

    public static void setMenuShowVrgPlayer(boolean value) {
        prefs().edit().putBoolean("menu_vrg_player", value).apply();
    }

    public static boolean isMenuShowVrgSettings() {
        return prefs().getBoolean("menu_vrg_settings", true);
    }

    public static void setMenuShowVrgSettings(boolean value) {
        prefs().edit().putBoolean("menu_vrg_settings", value).apply();
    }

    public static boolean isMenuShowNewGroup() {
        return prefs().getBoolean("menu_new_group", true);
    }

    public static void setMenuShowNewGroup(boolean value) {
        prefs().edit().putBoolean("menu_new_group", value).apply();
    }

    public static boolean isMenuShowSaved() {
        return prefs().getBoolean("menu_saved", true);
    }

    public static void setMenuShowSaved(boolean value) {
        prefs().edit().putBoolean("menu_saved", value).apply();
    }

    public static boolean isMenuShowWallet() {
        return prefs().getBoolean("menu_wallet", true);
    }

    public static void setMenuShowWallet(boolean value) {
        prefs().edit().putBoolean("menu_wallet", value).apply();
    }

    public static boolean menuItem(String name, boolean def) {
        return prefs().getBoolean("menu_" + name, def);
    }

    public static void setMenuItem(String name, boolean value) {
        prefs().edit().putBoolean("menu_" + name, value).apply();
    }

    public static boolean isGalleryCamera() {
        return prefs().getBoolean("gallery_camera", false);
    }

    public static void setGalleryCamera(boolean value) {
        prefs().edit().putBoolean("gallery_camera", value).apply();
    }

    public static int maxSelect() {
        return prefs().getBoolean("select_unlimited", true) ? 100000 : 100;
    }
}
