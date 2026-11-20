package org.telegram.vrg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VrgBadges {
    private static final String BADGES_API = "https://vrgclient.app/api/badges?ids=";
    private static final String BOT_USERNAME = "VRGagent";
    private static final long STALE_MS = 300_000L;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<Long, Entry> map = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> checked = new ConcurrentHashMap<>();
    private static final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private static final HashMap<String, Bitmap> bitmaps = new HashMap<>();
    private static volatile boolean cacheLoaded = false;
    private static boolean fetching = false;
    private static boolean scheduled = false;

    public static final String[] LUNA_ICONS = {"crown", "moon", "bolt", "heart"};

    public static final class Entry {
        public final String type;
        public final int num;
        public final String icon;

        public Entry(String type, int num) {
            this(type, num, "");
        }

        public Entry(String type, int num, String icon) {
            this.type = type;
            this.num = num;
            this.icon = icon == null ? "" : icon;
        }

        public boolean isDev() {
            return "dev".equals(type);
        }

        public boolean isSupporter() {
            return "supporter".equals(type);
        }

        public boolean isLuna() {
            return "luna".equals(type);
        }

        public String lunaIcon() {
            for (String i : LUNA_ICONS) {
                if (i.equals(icon)) {
                    return i;
                }
            }
            return "crown";
        }

        public String kind() {
            if (isLuna()) {
                return "luna_" + lunaIcon();
            }
            return type;
        }

        public String displayLabel() {
            if (isDev()) {
                return num > 0 ? ("VRG Developer #" + num) : "VRG Developer";
            }
            if (isSupporter()) {
                return num > 0 ? ("VRG Supporter #" + num) : "VRG Supporter";
            }
            if (isLuna()) {
                return num > 0 ? ("VRG Luna #" + num) : "VRG Luna";
            }
            return "";
        }
    }

    private VrgBadges() {
    }

    public static void ensureLoaded() {
        if (!cacheLoaded) {
            cacheLoaded = true;
            loadCache();
        }
    }

    public static Entry lookup(long userId) {
        ensureLoaded();
        if (userId <= 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long c = checked.get(userId);
        if (c == null || now - c > STALE_MS) {
            synchronized (pending) {
                pending.add(userId);
                if (!scheduled) {
                    scheduled = true;
                    mainHandler.postDelayed(VrgBadges::startFetch, 200);
                }
            }
        }
        return map.get(userId);
    }

    private static void startFetch() {
        final ArrayList<Long> ids = new ArrayList<>();
        synchronized (pending) {
            scheduled = false;
            if (fetching || pending.isEmpty()) {
                return;
            }
            Iterator<Long> it = pending.iterator();
            while (it.hasNext() && ids.size() < 50) {
                ids.add(it.next());
                it.remove();
            }
            fetching = true;
        }
        executor.execute(() -> {
            boolean ok = false;
            HashMap<Long, Entry> parsed = new HashMap<>();
            try {
                StringBuilder q = new StringBuilder(BADGES_API);
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) {
                        q.append(',');
                    }
                    q.append(ids.get(i));
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(q.toString()).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    parseJson(sb.toString(), parsed);
                    ok = true;
                }
                conn.disconnect();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                long now = System.currentTimeMillis();
                boolean changed = false;
                for (Long id : ids) {
                    if (!success) {
                        checked.put(id, now - STALE_MS + 60_000L);
                        continue;
                    }
                    checked.put(id, now);
                    Entry found = parsed.get(id);
                    Entry had = map.get(id);
                    if (found != null) {
                        if (had == null || !had.type.equals(found.type) || had.num != found.num || !had.icon.equals(found.icon)) {
                            map.put(id, found);
                            changed = true;
                        }
                    } else if (had != null) {
                        map.remove(id);
                        changed = true;
                    }
                }
                synchronized (pending) {
                    fetching = false;
                    if (!pending.isEmpty() && !scheduled) {
                        scheduled = true;
                        mainHandler.postDelayed(VrgBadges::startFetch, 100);
                    }
                }
                if (changed) {
                    saveCache();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, 0);
                }
            });
        });
    }

    public static void refresh(long userId) {
        checked.remove(userId);
        lookup(userId);
    }

    public static void setLocalIcon(long userId, String icon) {
        Entry e = map.get(userId);
        if (e == null || !e.isLuna()) {
            return;
        }
        map.put(userId, new Entry(e.type, e.num, icon));
        saveCache();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, 0);
    }

    private static int resFor(String kind, boolean white) {
        switch (kind) {
            case "dev":
                return white ? R.drawable.vrg_badge_dev_white : R.drawable.vrg_badge_dev_black;
            case "supporter":
            case "luna_heart":
                return white ? R.drawable.vrg_badge_supporter_white : R.drawable.vrg_badge_supporter_black;
            case "luna_crown":
                return white ? R.drawable.vrg_badge_luna_crown_white : R.drawable.vrg_badge_luna_crown_black;
            case "luna_moon":
                return white ? R.drawable.vrg_badge_luna_moon_white : R.drawable.vrg_badge_luna_moon_black;
            case "luna_bolt":
                return white ? R.drawable.vrg_badge_luna_bolt_white : R.drawable.vrg_badge_luna_bolt_black;
            default:
                return 0;
        }
    }

    public static Drawable getDrawable(Context context, String kind, boolean white) {
        return getDrawable(context, kind, white, 18);
    }

    public static Drawable getDrawable(Context context, Entry entry, boolean white, int heightDp) {
        return entry == null ? null : getDrawable(context, entry.kind(), white, heightDp);
    }

    public static Drawable getDrawable(Context context, String kind, boolean white, int heightDp) {
        if (context == null || kind == null) {
            return null;
        }
        int resId = resFor(kind, white);
        if (resId == 0) {
            return null;
        }
        int px = AndroidUtilities.dp(heightDp);
        try {
            String key = resId + ":" + px;
            Bitmap scaled = bitmaps.get(key);
            if (scaled == null) {
                Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), resId);
                if (bmp == null) {
                    return null;
                }
                scaled = Bitmap.createScaledBitmap(bmp, px, px, true);
                bitmaps.put(key, scaled);
            }
            return new VrgBadgeDrawable(scaled, kind, px, px);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean isDarkTheme() {
        int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        int r = (bg >> 16) & 0xFF;
        int g = (bg >> 8) & 0xFF;
        int b = bg & 0xFF;
        return (r + g + b) / 3 < 128;
    }

    public static final class BadgeView extends View {
        private final Drawable drawable;
        private final boolean ring;

        public BadgeView(Context context, String kind, int sizeDp, boolean ring) {
            super(context);
            this.ring = ring;
            drawable = getDrawable(context, kind, isDarkTheme(), sizeDp);
            if (drawable != null) {
                drawable.setCallback(this);
            }
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return who == drawable || super.verifyDrawable(who);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawable == null) {
                return;
            }
            int w = drawable.getIntrinsicWidth();
            int h = drawable.getIntrinsicHeight();
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            if (ring) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(2));
                p.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                float pad = AndroidUtilities.dp(8);
                canvas.drawRoundRect(x - pad, y - pad, x + w + pad, y + h + pad, AndroidUtilities.dp(14), AndroidUtilities.dp(14), p);
            }
            canvas.save();
            canvas.translate(x, y);
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    public static void showInfoDialog(Context context, Entry entry) {
        showInfoDialog(context, entry, null, null, false, 0);
    }

    public static void showInfoDialog(Context context, Entry entry, String peerName) {
        showInfoDialog(context, entry, peerName, null, false, 0);
    }

    private static TextView text(Context context, CharSequence value, int sp, int colorKey, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sp);
        t.setTextColor(Theme.getColor(colorKey));
        if (bold) {
            t.setTypeface(AndroidUtilities.bold());
        }
        return t;
    }

    private static View tierRow(Context context, String[] kinds, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout icons = new LinearLayout(context);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        for (String kind : kinds) {
            BadgeView v = new BadgeView(context, kind, 24, false);
            icons.addView(v, new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36)));
        }
        row.addView(icons, new LinearLayout.LayoutParams(AndroidUtilities.dp(36 * 4 + 4), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(context, title, 15, Theme.key_dialogTextBlack, true));
        TextView sub = text(context, subtitle, 13, Theme.key_dialogTextGray2, false);
        col.addView(sub);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    public static void showInfoDialog(Context context, Entry entry, String peerName, BaseFragment fragment, boolean own, long userId) {
        if (context == null || entry == null) {
            return;
        }
        String baseDesc;
        if (entry.isDev()) {
            baseDesc = "Этот пользователь создал VRG Client.";
        } else if (entry.isLuna()) {
            baseDesc = "Этот пользователь поддержал развитие VRG Client и может сам выбирать себе значок.";
        } else {
            baseDesc = "Этот пользователь поддержал развитие VRG Client.";
        }
        String desc = (peerName != null && !peerName.isEmpty()) ? (peerName + "\n" + baseDesc) : baseDesc;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        final BadgeView hero = new BadgeView(context, entry.kind(), 68, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(AndroidUtilities.dp(120), AndroidUtilities.dp(112));
        hp.bottomMargin = AndroidUtilities.dp(8);
        root.addView(hero, hp);

        TextView d = text(context, desc, 15, Theme.key_dialogTextBlack, false);
        d.setGravity(Gravity.CENTER);
        root.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (own && entry.isLuna() && userId > 0) {
            TextView label = text(context, "Твой значок: нажми на другой, чтобы поменять", 14, Theme.key_dialogTextGray2, false);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = AndroidUtilities.dp(22);
            root.addView(label, lp);

            final LinearLayout pickRow = new LinearLayout(context);
            pickRow.setOrientation(LinearLayout.HORIZONTAL);
            pickRow.setGravity(Gravity.CENTER);
            final String[] current = {entry.lunaIcon()};
            final BadgeView[] views = new BadgeView[LUNA_ICONS.length];
            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                pickRow.removeAllViews();
                for (int i = 0; i < LUNA_ICONS.length; i++) {
                    final String icon = LUNA_ICONS[i];
                    BadgeView v = new BadgeView(context, "luna_" + icon, 32, icon.equals(current[0]));
                    views[i] = v;
                    v.setOnClickListener(x -> {
                        if (!icon.equals(current[0])) {
                            current[0] = icon;
                            changeIcon(userId, icon);
                            rebuild[0].run();
                        }
                    });
                    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64));
                    vp.leftMargin = AndroidUtilities.dp(4);
                    vp.rightMargin = AndroidUtilities.dp(4);
                    pickRow.addView(v, vp);
                }
            };
            rebuild[0].run();
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(76));
            rp.topMargin = AndroidUtilities.dp(6);
            root.addView(pickRow, rp);
        }

        TextView how = text(context, "Как получить такой же значок", 14, Theme.key_dialogTextGray2, false);
        LinearLayout.LayoutParams howLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        howLp.topMargin = AndroidUtilities.dp(22);
        howLp.bottomMargin = AndroidUtilities.dp(10);
        root.addView(how, howLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = AndroidUtilities.dp(14);
        root.addView(tierRow(context, new String[]{"supporter"}, "Supporter — от 3 $", "Один значок навсегда, менять его нельзя."), rowLp);
        root.addView(tierRow(context, new String[]{"luna_crown", "luna_moon", "luna_bolt", "luna_heart"}, "Luna — от 50 $", "Можно менять значок: корона, луна, молния или сердце."), rowLp);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(entry.displayLabel())
            .setView(scroll)
            .setNegativeButton("Закрыть", null);
        if (fragment != null) {
            builder.setPositiveButton("Поддержать VRG", (dialog, which) -> fragment.presentFragment(new VrgSupportActivity()));
        }
        builder.show();
    }

    private static void changeIcon(long userId, String icon) {
        setLocalIcon(userId, icon);
        final int account = UserConfig.selectedAccount;
        final Runnable done = () -> mainHandler.postDelayed(() -> refresh(userId), 4500);
        MessagesController mc = MessagesController.getInstance(account);
        TLObject cached = mc.getUserOrChat(BOT_USERNAME);
        if (cached instanceof TLRPC.User) {
            sendCommand(account, (TLRPC.User) cached, icon);
            done.run();
            return;
        }
        mc.getUserNameResolver().resolve(BOT_USERNAME, id -> {
            if (id == null) {
                return;
            }
            TLRPC.User bot = mc.getUser(id);
            if (bot != null) {
                sendCommand(account, bot, icon);
                done.run();
            }
        });
    }

    private static void sendCommand(int account, TLRPC.User bot, String icon) {
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
            "/vrgicon " + icon, bot.id, null, null, null, false, null, null, null,
            false, 0, 0, null, false);
        SendMessagesHelper.getInstance(account).sendMessage(params);
    }

    private static File cacheFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "vrg_badges_cache2.json");
    }

    private static void loadCache() {
        File f = cacheFile();
        if (!f.exists()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(f);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            HashMap<Long, Entry> parsed = new HashMap<>();
            parseJson(sb.toString(), parsed);
            map.putAll(parsed);
        } catch (Throwable ignore) {
        }
    }

    private static void saveCache() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<Long, Entry> e : map.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("type", e.getValue().type);
                o.put("num", e.getValue().num);
                if (!e.getValue().icon.isEmpty()) {
                    o.put("icon", e.getValue().icon);
                }
                root.put(String.valueOf(e.getKey()), o);
            }
            try (FileOutputStream fos = new FileOutputStream(cacheFile())) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignore) {
        }
    }

    private static void parseJson(String raw, Map<Long, Entry> out) {
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    long id = Long.parseLong(key);
                    if (id <= 0) {
                        continue;
                    }
                    Object val = root.get(key);
                    String type = "";
                    int num = 0;
                    String icon = "";
                    if (val instanceof JSONObject) {
                        JSONObject obj = (JSONObject) val;
                        type = obj.optString("type", "").trim().toLowerCase();
                        num = obj.optInt("num", 0);
                        icon = obj.optString("icon", "").trim().toLowerCase();
                    } else if (val instanceof String) {
                        type = ((String) val).trim().toLowerCase();
                    }
                    if ("dev".equals(type) || "supporter".equals(type) || "luna".equals(type)) {
                        out.put(id, new Entry(type, num, icon));
                    }
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static int inlineWidth(Entry entry, int heightDp) {
        if (entry == null) {
            return 0;
        }
        return AndroidUtilities.dp(heightDp) + AndroidUtilities.dp(4);
    }

    public static CharSequence decorate(Context context, CharSequence name, long userId) {
        return decorate(context, name, userId, 15, null);
    }

    public static CharSequence decorate(Context context, CharSequence name, long userId, int heightDp) {
        return decorate(context, name, userId, heightDp, null);
    }

    public static CharSequence decorate(Context context, CharSequence name, long userId, int heightDp, final View host) {
        if (context == null || name == null || userId <= 0) {
            return name;
        }
        final Entry entry = lookup(userId);
        if (entry == null) {
            return name;
        }
        final Drawable drawable = getDrawable(context, entry, isDarkTheme(), heightDp);
        if (drawable == null) {
            return name;
        }
        final int width = drawable.getIntrinsicWidth();
        final int gap = AndroidUtilities.dp(4);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(name);
        int start = sb.length();
        sb.append(" ");
        sb.setSpan(new android.text.style.ReplacementSpan() {
            @Override
            public int getSize(Paint paint, CharSequence text, int s, int e, Paint.FontMetricsInt fm) {
                return width + gap;
            }

            @Override
            public void draw(Canvas canvas, CharSequence text, int s, int e, float x, int top, int y, int bottom, Paint paint) {
                int h = drawable.getIntrinsicHeight();
                float cy = (top + bottom) / 2f;
                canvas.save();
                canvas.translate(x + gap, cy - h / 2f);
                drawable.draw(canvas);
                canvas.restore();
                VrgBadgeDrawable.touch(host);
            }
        }, start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
