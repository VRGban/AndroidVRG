package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public final class VrgGlyph {
    public enum Type {
        PLAY, PAUSE, PREV, NEXT, SHUFFLE, REPEAT, REPEAT_ONE, VOLUME, VOLUME_MUTED,
        PLUS, CHECK, NOTE, DOWNLOAD, PLAYLIST_ADD, TRASH, HEART, HEART_FILLED, LYRICS, EQ, CHEVRON_DOWN, SEND, RADIO
    }

    private VrgGlyph() {
    }

    public static void draw(Canvas canvas, Type type, float x, float y, float size, int color) {
        canvas.save();
        canvas.translate(x, y);
        float scale = size / 24f;
        canvas.scale(scale, scale);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(1.6f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        Path p = new Path();
        switch (type) {
            case PLAY:
                p.moveTo(7.5f, 5.2f);
                p.lineTo(19.2f, 12f);
                p.lineTo(7.5f, 18.8f);
                p.close();
                canvas.drawPath(p, fill);
                break;
            case PAUSE:
                p.addRoundRect(new RectF(7f, 5.5f, 10.2f, 18.5f), 1.5f, 1.5f, Path.Direction.CW);
                p.addRoundRect(new RectF(13.8f, 5.5f, 17f, 18.5f), 1.5f, 1.5f, Path.Direction.CW);
                canvas.drawPath(p, fill);
                break;
            case PREV:
                p.moveTo(14.5f, 6.2f);
                p.lineTo(8.5f, 12f);
                p.lineTo(14.5f, 17.8f);
                stroke.setStrokeWidth(3f);
                canvas.drawPath(p, stroke);
                break;
            case NEXT:
                p.moveTo(9.5f, 6.2f);
                p.lineTo(15.5f, 12f);
                p.lineTo(9.5f, 17.8f);
                stroke.setStrokeWidth(3f);
                canvas.drawPath(p, stroke);
                break;
            case SHUFFLE:
                p.moveTo(3.5f, 7f);
                p.lineTo(7.5f, 7f);
                p.cubicTo(12f, 7f, 12f, 17f, 16.5f, 17f);
                p.lineTo(20.5f, 17f);
                p.moveTo(3.5f, 17f);
                p.lineTo(7.5f, 17f);
                p.cubicTo(9.4f, 17f, 10.4f, 15.6f, 11.2f, 14f);
                p.moveTo(12.8f, 10f);
                p.cubicTo(13.6f, 8.4f, 14.6f, 7f, 16.5f, 7f);
                p.lineTo(20.5f, 7f);
                p.moveTo(18f, 4.5f);
                p.lineTo(20.5f, 7f);
                p.lineTo(18f, 9.5f);
                p.moveTo(18f, 14.5f);
                p.lineTo(20.5f, 17f);
                p.lineTo(18f, 19.5f);
                canvas.drawPath(p, stroke);
                break;
            case REPEAT:
            case REPEAT_ONE:
                p.moveTo(4.5f, 11.5f);
                p.lineTo(4.5f, 9.5f);
                p.quadTo(4.5f, 6.8f, 7.2f, 6.8f);
                p.lineTo(19f, 6.8f);
                p.moveTo(16.8f, 4.5f);
                p.lineTo(19.2f, 6.8f);
                p.lineTo(16.8f, 9.1f);
                p.moveTo(19.5f, 12.5f);
                p.lineTo(19.5f, 14.5f);
                p.quadTo(19.5f, 17.2f, 16.8f, 17.2f);
                p.lineTo(5f, 17.2f);
                p.moveTo(7.2f, 14.9f);
                p.lineTo(4.8f, 17.2f);
                p.lineTo(7.2f, 19.5f);
                canvas.drawPath(p, stroke);
                if (type == Type.REPEAT_ONE) {
                    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
                    text.setColor(color);
                    text.setTextSize(7f);
                    text.setTypeface(Typeface.DEFAULT_BOLD);
                    text.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("1", 12f, 14f, text);
                }
                break;
            case VOLUME:
            case VOLUME_MUTED: {
                Path speaker = new Path();
                speaker.moveTo(4f, 9.5f);
                speaker.lineTo(7.5f, 9.5f);
                speaker.lineTo(12f, 5.8f);
                speaker.lineTo(12f, 18.2f);
                speaker.lineTo(7.5f, 14.5f);
                speaker.lineTo(4f, 14.5f);
                speaker.close();
                canvas.drawPath(speaker, stroke);
                if (type == Type.VOLUME) {
                    p.moveTo(15.2f, 9.2f);
                    p.quadTo(17.2f, 12f, 15.2f, 14.8f);
                    p.moveTo(17.8f, 6.8f);
                    p.quadTo(21.2f, 12f, 17.8f, 17.2f);
                } else {
                    p.moveTo(15.8f, 9.5f);
                    p.lineTo(20.2f, 14.5f);
                    p.moveTo(20.2f, 9.5f);
                    p.lineTo(15.8f, 14.5f);
                }
                canvas.drawPath(p, stroke);
                break;
            }
            case PLUS:
                p.moveTo(12f, 5.5f);
                p.lineTo(12f, 18.5f);
                p.moveTo(5.5f, 12f);
                p.lineTo(18.5f, 12f);
                canvas.drawPath(p, stroke);
                break;
            case CHECK:
                p.moveTo(5.5f, 12.5f);
                p.lineTo(10f, 17f);
                p.lineTo(18.5f, 7.5f);
                canvas.drawPath(p, stroke);
                break;
            case NOTE: {
                p.addOval(new RectF(5.5f, 14.5f, 10.5f, 18.5f), Path.Direction.CW);
                p.addOval(new RectF(13.5f, 12.5f, 18.5f, 16.5f), Path.Direction.CW);
                canvas.drawPath(p, fill);
                Path stems = new Path();
                stems.moveTo(10.2f, 16.5f);
                stems.lineTo(10.2f, 6.2f);
                stems.lineTo(18.2f, 4.8f);
                stems.lineTo(18.2f, 14.5f);
                canvas.drawPath(stems, stroke);
                break;
            }
            case DOWNLOAD:
                p.moveTo(12f, 4.5f);
                p.lineTo(12f, 15f);
                p.moveTo(8f, 11f);
                p.lineTo(12f, 15f);
                p.lineTo(16f, 11f);
                p.moveTo(5.5f, 18.5f);
                p.lineTo(18.5f, 18.5f);
                canvas.drawPath(p, stroke);
                break;
            case PLAYLIST_ADD:
                p.moveTo(4.5f, 7f);
                p.lineTo(16f, 7f);
                p.moveTo(4.5f, 12f);
                p.lineTo(16f, 12f);
                p.moveTo(4.5f, 17f);
                p.lineTo(11.5f, 17f);
                p.moveTo(17f, 14f);
                p.lineTo(17f, 20f);
                p.moveTo(14f, 17f);
                p.lineTo(20f, 17f);
                canvas.drawPath(p, stroke);
                break;
            case TRASH:
                p.moveTo(7f, 7f);
                p.lineTo(17f, 17f);
                p.moveTo(17f, 7f);
                p.lineTo(7f, 17f);
                canvas.drawPath(p, stroke);
                break;
            case HEART:
            case HEART_FILLED:
                p.moveTo(12f, 19.2f);
                p.cubicTo(4.5f, 14.2f, 3.8f, 9.2f, 6.2f, 6.5f);
                p.cubicTo(8.0f, 4.5f, 10.8f, 5.2f, 12f, 7.4f);
                p.cubicTo(13.2f, 5.2f, 16.0f, 4.5f, 17.8f, 6.5f);
                p.cubicTo(20.2f, 9.2f, 19.5f, 14.2f, 12f, 19.2f);
                canvas.drawPath(p, type == Type.HEART_FILLED ? fill : stroke);
                break;
            case LYRICS:
                p.addRoundRect(new RectF(4.5f, 4.5f, 19.5f, 19.5f), 3f, 3f, Path.Direction.CW);
                p.moveTo(8f, 9f);
                p.lineTo(16f, 9f);
                p.moveTo(8f, 12.5f);
                p.lineTo(14f, 12.5f);
                p.moveTo(8f, 16f);
                p.lineTo(11.5f, 16f);
                canvas.drawPath(p, stroke);
                break;
            case CHEVRON_DOWN:
                p.moveTo(5.5f, 9f);
                p.lineTo(12f, 15.5f);
                p.lineTo(18.5f, 9f);
                stroke.setStrokeWidth(2.2f);
                canvas.drawPath(p, stroke);
                break;
            case SEND:
                p.moveTo(4f, 12f);
                p.lineTo(20f, 4.5f);
                p.lineTo(15f, 19.5f);
                p.lineTo(11.5f, 13f);
                p.close();
                p.moveTo(11.5f, 13f);
                p.lineTo(20f, 4.5f);
                canvas.drawPath(p, stroke);
                break;
            case RADIO:
                p.addCircle(12f, 12f, 2.2f, Path.Direction.CW);
                canvas.drawPath(p, fill);
                Path waves = new Path();
                waves.addArc(new RectF(6.5f, 6.5f, 17.5f, 17.5f), -50f, 100f);
                waves.addArc(new RectF(6.5f, 6.5f, 17.5f, 17.5f), 130f, 100f);
                waves.addArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -50f, 100f);
                waves.addArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 130f, 100f);
                canvas.drawPath(waves, stroke);
                break;
            case EQ: {
                p.moveTo(6f, 5f);
                p.lineTo(6f, 19f);
                p.moveTo(12f, 5f);
                p.lineTo(12f, 19f);
                p.moveTo(18f, 5f);
                p.lineTo(18f, 19f);
                canvas.drawPath(p, stroke);
                canvas.drawCircle(6f, 9f, 2.2f, fill);
                canvas.drawCircle(12f, 15f, 2.2f, fill);
                canvas.drawCircle(18f, 10.5f, 2.2f, fill);
                break;
            }
        }
        canvas.restore();
    }

    public static Bitmap bitmap(Type type, int sizePx, int color) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        draw(new Canvas(bmp), type, 0, 0, sizePx, color);
        return bmp;
    }

    public static class View extends android.view.View {
        private Type type = Type.PLAY;
        private int color = 0xFFFFFFFF;
        private float glyphSizeDp = 24;

        public View(Context context) {
            super(context);
        }

        public View(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public View set(Type type, int color, float glyphSizeDp) {
            this.type = type;
            this.color = color;
            this.glyphSizeDp = glyphSizeDp;
            invalidate();
            return this;
        }

        public void setType(Type type) {
            if (this.type != type) {
                this.type = type;
                invalidate();
            }
        }

        public void setColor(int color) {
            if (this.color != color) {
                this.color = color;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = glyphSizeDp * getResources().getDisplayMetrics().density;
            VrgGlyph.draw(canvas, type, (getWidth() - size) / 2f, (getHeight() - size) / 2f, size, color);
        }
    }
}
