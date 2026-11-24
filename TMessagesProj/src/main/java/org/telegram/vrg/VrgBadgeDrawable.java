package org.telegram.vrg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class VrgBadgeDrawable extends Drawable {
    private static final long FRAME_MS = 40;
    private static final WeakHashMap<View, Long> hosts = new WeakHashMap<>();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean running;

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            Iterator<Map.Entry<View, Long>> it = hosts.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<View, Long> e = it.next();
                View v = e.getKey();
                if (v == null || now - e.getValue() > 300) {
                    it.remove();
                } else {
                    v.invalidate();
                }
            }
            if (hosts.isEmpty()) {
                running = false;
            } else {
                handler.postDelayed(this, FRAME_MS);
            }
        }
    };

    public static void touch(View host) {
        if (host == null) {
            return;
        }
        hosts.put(host, SystemClock.uptimeMillis());
        if (!running) {
            running = true;
            handler.postDelayed(tick, FRAME_MS);
        }
    }

    private final Bitmap bitmap;
    private final int baseColor;
    private final int width;
    private final int height;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable invalidator = this::invalidateSelf;
    private RadialGradient glow;

    public VrgBadgeDrawable(Bitmap bitmap, String kind, int widthPx, int heightPx) {
        this.bitmap = bitmap;
        this.width = widthPx;
        this.height = heightPx;
        if ("dev".equals(kind)) {
            baseColor = Color.rgb(110, 168, 255);
        } else if ("supporter".equals(kind) || "luna_heart".equals(kind)) {
            baseColor = Color.rgb(255, 122, 168);
        } else {
            baseColor = Color.rgb(255, 204, 85);
        }
        setBounds(0, 0, widthPx, heightPx);
        float radius = Math.max(widthPx, heightPx) * 0.85f;
        glow = new RadialGradient(widthPx / 2f, heightPx / 2f, radius,
            baseColor, baseColor & 0x00FFFFFF, Shader.TileMode.CLAMP);
        glowPaint.setShader(glow);
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float t = (SystemClock.uptimeMillis() % 100000L) / 1000f;
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float radius = Math.max(b.width(), b.height()) * 0.85f;

        int save = canvas.save();
        canvas.translate(b.left, b.top);
        glowPaint.setAlpha((int) (46 + 26 * Math.sin(t * 2.1)));
        canvas.drawCircle(b.width() / 2f, b.height() / 2f, radius, glowPaint);
        canvas.restoreToCount(save);

        float rx = b.width() * 0.5f + 4f * (b.height() / 15f);
        float ry = b.height() * 0.5f + 4f * (b.height() / 15f);
        for (int i = 0; i < 6; i++) {
            double angle = t * (0.8 + 0.13 * i) + i * 1.0472;
            double wobble = 1.0 + 0.18 * Math.sin(t * 1.7 + i * 2.1);
            float px = (float) (cx + Math.cos(angle) * rx * wobble);
            float py = (float) (cy + Math.sin(angle) * ry * wobble * ((i % 2) == 1 ? 1.0 : -1.0));
            float size = (float) (0.9 + 0.5 * (0.5 + 0.5 * Math.sin(t * 3.0 + i * 1.7))) * Math.max(1f, b.height() / 15f);
            int alpha = (int) (110 + 110 * Math.sin(t * 3.3 + i * 1.3));
            dotPaint.setColor(baseColor);
            dotPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
            canvas.drawCircle(px, py, size, dotPaint);
        }

        canvas.drawBitmap(bitmap, null, b, iconPaint);

        if (getCallback() != null) {
            unscheduleSelf(invalidator);
            scheduleSelf(invalidator, SystemClock.uptimeMillis() + FRAME_MS);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        iconPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        iconPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

