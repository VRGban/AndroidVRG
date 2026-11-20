package org.telegram.vrg;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.HashMap;

public final class VrgFakeProfile {
    private VrgFakeProfile() {
    }

    public static boolean isEnabled() {
        return !TextUtils.isEmpty(getPhone()) || !TextUtils.isEmpty(getUsername()) || !TextUtils.isEmpty(getBio());
    }

    public static String getPhone() {
        return VrgConfig.prefs().getString("fake_profile_phone", "").trim();
    }

    public static void setPhone(String phone) {
        VrgConfig.prefs().edit().putString("fake_profile_phone", phone != null ? phone.trim() : "").apply();
    }

    public static String getUsername() {
        String u = VrgConfig.prefs().getString("fake_profile_username", "").trim();
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        return u;
    }

    public static void setUsername(String username) {
        String u = username != null ? username.trim() : "";
        if (u.startsWith("@")) {
            u = u.substring(1);
        }
        VrgConfig.prefs().edit().putString("fake_profile_username", u).apply();
    }

    public static String getBio() {
        return VrgConfig.prefs().getString("fake_profile_bio", "").trim();
    }

    public static void setBio(String bio) {
        VrgConfig.prefs().edit().putString("fake_profile_bio", bio != null ? bio.trim() : "").apply();
    }

    public static void reset() {
        VrgConfig.prefs().edit()
            .remove("fake_profile_phone")
            .remove("fake_profile_username")
            .remove("fake_profile_bio")
            .remove("fake_profile_gifts")
            .apply();
    }

    private static final HashMap<String, TL_stars.StarGift> giftCache = new HashMap<>();

    public static ArrayList<String> getGiftSlugs() {
        ArrayList<String> out = new ArrayList<>();
        String raw = VrgConfig.prefs().getString("fake_profile_gifts", "");
        for (String line : raw.split("\\n")) {
            String slug = parseSlug(line);
            if (!slug.isEmpty() && !out.contains(slug)) {
                out.add(slug);
            }
        }
        return out;
    }

    public static String getGiftsText() {
        return VrgConfig.prefs().getString("fake_profile_gifts", "");
    }

    public static void setGiftsText(String text) {
        VrgConfig.prefs().edit().putString("fake_profile_gifts", text != null ? text.trim() : "").apply();
    }

    private static String parseSlug(String line) {
        String v = line == null ? "" : line.trim();
        int i = v.lastIndexOf('/');
        if (i >= 0) {
            v = v.substring(i + 1);
        }
        int q = v.indexOf('?');
        if (q >= 0) {
            v = v.substring(0, q);
        }
        return v.trim();
    }

    public static void injectGifts(int account, StarsController.GiftsList list) {
        ArrayList<String> slugs = getGiftSlugs();
        if (slugs.isEmpty()) {
            return;
        }
        for (int i = slugs.size() - 1; i >= 0; i--) {
            final String slug = slugs.get(i);
            TL_stars.StarGift cached = giftCache.get(slug);
            if (cached != null) {
                prepend(list, cached, slug);
                continue;
            }
            TL_stars.getUniqueStarGift req = new TL_stars.getUniqueStarGift();
            req.slug = slug;
            ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_stars.TL_payments_uniqueStarGift) {
                    TL_stars.StarGift gift = ((TL_stars.TL_payments_uniqueStarGift) res).gift;
                    if (gift != null) {
                        giftCache.put(slug, gift);
                        prepend(list, gift, slug);
                    }
                }
            }));
        }
    }

    private static void prepend(StarsController.GiftsList list, TL_stars.StarGift gift, String slug) {
        for (TL_stars.SavedStarGift g : list.gifts) {
            if (g.gift instanceof TL_stars.TL_starGiftUnique && slug.equals(((TL_stars.TL_starGiftUnique) g.gift).slug)) {
                return;
            }
        }
        TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
        saved.gift = gift;
        saved.date = (int) (System.currentTimeMillis() / 1000L);
        saved.pinned_to_top = true;
        saved.saved_id = -Math.abs((long) slug.hashCode()) - 1;
        list.gifts.add(0, saved);
        list.notifyUpdate();
    }
}
