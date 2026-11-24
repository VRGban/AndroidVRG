package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class VrgVSlider extends View {
    public interface Listener {
        void onChanged(float value, boolean fromUser);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float min = -12f;
    private float max = 12f;
    private float value;
    private boolean dragging;
    private Listener listener;

    public VrgVSlider(Context context) {
        super(context);
        track.setColor(0x26FFFFFF);
        fill.setColor(0xFFFFFFFF);
        tick.setColor(0x66FFFFFF);
        tick.setStrokeWidth(AndroidUtilities.dp(1));
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = max;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setValue(float v) {
        value = Math.max(min, Math.min(max, v));
        invalidate();
    }

    public float getValue() {
        return value;
    }

    private float yForValue(float v) {
        float pad = AndroidUtilities.dp(14);
        float h = getHeight() - pad * 2;
        return pad + h * (1f - (v - min) / (max - min));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float w = AndroidUtilities.dp(dragging ? 6 : 4);
        float pad = AndroidUtilities.dp(14);
        rect.set(cx - w / 2, pad, cx + w / 2, getHeight() - pad);
        canvas.drawRoundRect(rect, w / 2, w / 2, track);
        float zeroY = yForValue(0f);
        float y = yForValue(value);
        rect.set(cx - w / 2, Math.min(zeroY, y), cx + w / 2, Math.max(zeroY, y));
        canvas.drawRect(rect, fill);
        canvas.drawLine(cx - AndroidUtilities.dp(9), zeroY, cx + AndroidUtilities.dp(9), zeroY, tick);
        canvas.drawCircle(cx, y, AndroidUtilities.dp(dragging ? 11 : 9), fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                update(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                update(e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                update(e.getY());
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void update(float y) {
        float pad = AndroidUtilities.dp(14);
        float h = getHeight() - pad * 2;
        float f = 1f - Math.max(0f, Math.min(1f, (y - pad) / h));
        float v = Math.round(min + f * (max - min));
        value = v;
        invalidate();
        if (listener != null) {
            listener.onChanged(v, true);
        }
    }
}

