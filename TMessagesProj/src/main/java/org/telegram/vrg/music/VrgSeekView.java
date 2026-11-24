package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class VrgSeekView extends View {
    public interface Listener {
        void onProgress(float fraction);
        void onStart();
        void onStop(float fraction);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress;
    private boolean dragging;
    private float thumbScale;
    private Listener listener;

    public VrgSeekView(Context context) {
        super(context);
        track.setColor(0x33FFFFFF);
        fill.setColor(0xFFFFFFFF);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public boolean isDragging() {
        return dragging;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(28));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float pad = AndroidUtilities.dp(8);
        float w = getWidth() - pad * 2;
        float cy = getHeight() / 2f;
        float h = AndroidUtilities.dp(dragging ? 6 : 4);
        rect.set(pad, cy - h / 2, pad + w, cy + h / 2);
        canvas.drawRoundRect(rect, h / 2, h / 2, track);
        float x = pad + w * progress;
        rect.set(pad, cy - h / 2, x, cy + h / 2);
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        float r = AndroidUtilities.dp(dragging ? 8 : 6);
        canvas.drawCircle(x, cy, r, fill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float pad = AndroidUtilities.dp(8);
        float w = getWidth() - pad * 2;
        float f = Math.max(0f, Math.min(1f, (e.getX() - pad) / w));
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                progress = f;
                if (listener != null) {
                    listener.onStart();
                    listener.onProgress(f);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = f;
                if (listener != null) {
                    listener.onProgress(f);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                progress = f;
                if (listener != null) {
                    listener.onStop(f);
                }
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }
}

