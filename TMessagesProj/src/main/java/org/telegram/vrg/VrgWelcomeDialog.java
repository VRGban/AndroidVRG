package org.telegram.vrg;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public final class VrgWelcomeDialog {
    private static final int SLIDES = 4;

    private VrgWelcomeDialog() {
    }

    public static void showIfNeeded(Context context) {
        if (context == null || VrgConfig.isWelcomeShown()) {
            return;
        }
        show(context);
    }

    private static final class BigText extends View {
        private final String text;
        private final boolean outline;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BigText(Context context, String text, boolean outline) {
            super(context);
            this.text = text;
            this.outline = outline;
            paint.setTypeface(Typeface.create(AndroidUtilities.bold(), Typeface.BOLD));
            if (outline) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
            } else {
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(Color.WHITE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            float size = AndroidUtilities.dp(54);
            paint.setTextSize(size);
            while (paint.measureText(text) > w - AndroidUtilities.dp(8) && size > AndroidUtilities.dp(20)) {
                size -= AndroidUtilities.dp(2);
                paint.setTextSize(size);
            }
            setMeasuredDimension(w, (int) (size * 1.25f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(text, getWidth() / 2f - paint.measureText(text) / 2f, -fm.top + AndroidUtilities.dp(2), paint);
        }
    }

    private static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFF8C8C8C);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        t.setTypeface(AndroidUtilities.bold());
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static TextView muted(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFF8C8C8C);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(AndroidUtilities.dp(3), 1f);
        return t;
    }

    private static TextView card(Context c, String title, boolean selected) {
        TextView t = new TextView(c);
        t.setText(title + (selected ? "   ✓" : ""));
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        t.setTypeface(AndroidUtilities.bold());
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(18));
        bg.setColor(0xFF0B0B0C);
        bg.setStroke(AndroidUtilities.dp(selected ? 2 : 1), selected ? Color.WHITE : 0xFF2A2A2A);
        t.setBackground(bg);
        return t;
    }

    public static void show(Context context) {
        if (context == null) {
            return;
        }
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        final int[] slide = {0};
        final boolean[] ghost = {false};
        final boolean[] vrgTheme = {true};

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(60), AndroidUtilities.dp(24), AndroidUtilities.dp(120));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final TextView skip = new TextView(context);
        skip.setText("Пропустить");
        skip.setTextColor(0xFF8C8C8C);
        skip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        skip.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        root.addView(skip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 36, 8, 0));

        final LinearLayout dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(dots, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 10, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 110));

        final TextView button = new TextView(context);
        button.setTextColor(Color.BLACK);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.WHITE);
        btnBg.setCornerRadius(AndroidUtilities.dp(28));
        button.setBackground(btnBg);
        root.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM, 24, 0, 24, 36));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Math.max(insets.getSystemWindowInsetBottom(), 0);
            int top = Math.max(insets.getSystemWindowInsetTop(), 0);
            FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) button.getLayoutParams();
            bp.bottomMargin = AndroidUtilities.dp(36) + bottom;
            button.setLayoutParams(bp);
            FrameLayout.LayoutParams dp = (FrameLayout.LayoutParams) dots.getLayoutParams();
            dp.bottomMargin = AndroidUtilities.dp(110) + bottom;
            dots.setLayoutParams(dp);
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) skip.getLayoutParams();
            sp.topMargin = Math.max(AndroidUtilities.dp(36), top + AndroidUtilities.dp(8));
            skip.setLayoutParams(sp);
            content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(60) + top, AndroidUtilities.dp(24), AndroidUtilities.dp(120) + bottom);
            return insets;
        });

        final Runnable[] render = new Runnable[1];
        final Runnable finish = () -> {
            VrgConfig.setWelcomeShown(true);
            if (ghost[0]) {
                VrgConfig.setFullGhost(true);
            }
            if (vrgTheme[0]) {
                VrgThemeHelper.applyVrgDarkTheme();
            }
            dialog.dismiss();
        };
        skip.setOnClickListener(v -> finish.run());
        button.setOnClickListener(v -> {
            if (slide[0] == SLIDES - 1) {
                finish.run();
            } else {
                slide[0]++;
                render[0].run();
            }
        });

        render[0] = () -> {
            content.removeAllViews();
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(300).start();
            switch (slide[0]) {
                case 0: {
                    content.addView(new BigText(context, "Telegram,", false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    content.addView(new BigText(context, "как надо.", true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));
                    content.addView(muted(context, "VRG — клиент для Telegram. Открытый код, свой плеер, режим призрака."), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
                }
                case 1: {
                    content.addView(label(context, "01 — ОФОРМЛЕНИЕ"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
                    content.addView(new BigText(context, "Выберите вид.", false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));
                    TextView a = card(context, "Чёрно-белая VRG", vrgTheme[0]);
                    TextView b = card(context, "Как сейчас", !vrgTheme[0]);
                    a.setOnClickListener(v -> {
                        vrgTheme[0] = true;
                        render[0].run();
                    });
                    b.setOnClickListener(v -> {
                        vrgTheme[0] = false;
                        render[0].run();
                    });
                    content.addView(a, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84, 0, 0, 0, 12));
                    content.addView(b, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84));
                    break;
                }
                case 2: {
                    content.addView(label(context, "02 — ПРИЗРАК"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
                    content.addView(new BigText(context, "Вас не видно.", false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(18), AndroidUtilities.dp(22), AndroidUtilities.dp(18));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(AndroidUtilities.dp(18));
                    bg.setColor(0xFF0B0B0C);
                    bg.setStroke(AndroidUtilities.dp(ghost[0] ? 2 : 1), ghost[0] ? Color.WHITE : 0xFF2A2A2A);
                    row.setBackground(bg);
                    LinearLayout col = new LinearLayout(context);
                    col.setOrientation(LinearLayout.VERTICAL);
                    TextView t1 = new TextView(context);
                    t1.setText("Полный призрак");
                    t1.setTextColor(Color.WHITE);
                    t1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
                    t1.setTypeface(AndroidUtilities.bold());
                    TextView t2 = new TextView(context);
                    t2.setText("Не отмечать прочитанным, скрыть «печатает» и «в сети», смотреть истории незаметно.");
                    t2.setTextColor(0xFF8C8C8C);
                    t2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    col.addView(t1);
                    col.addView(t2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
                    row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0));
                    android.widget.Switch sw = new android.widget.Switch(context);
                    sw.setChecked(ghost[0]);
                    sw.setOnCheckedChangeListener((cb, checked) -> {
                        ghost[0] = checked;
                        bg.setStroke(AndroidUtilities.dp(checked ? 2 : 1), checked ? Color.WHITE : 0xFF2A2A2A);
                    });
                    row.addView(sw);
                    row.setOnClickListener(v -> sw.toggle());
                    content.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));
                    content.addView(muted(context, "Отдельные пункты можно включить позже в «Настройки VRG»."), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
                }
                default: {
                    content.addView(new BigText(context, "Готово.", true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));
                    content.addView(muted(context, "Всё меняется в «Настройки VRG». Новости — в @VRGclient."), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
                }
            }
            button.setText((slide[0] == 0 ? "Начать" : slide[0] == SLIDES - 1 ? "Поехали" : "Дальше") + "  →");
            skip.setVisibility(slide[0] < SLIDES - 1 ? View.VISIBLE : View.INVISIBLE);
            dots.removeAllViews();
            for (int i = 0; i < SLIDES; i++) {
                View d = new View(context);
                GradientDrawable dg = new GradientDrawable();
                dg.setShape(GradientDrawable.OVAL);
                dg.setColor(i == slide[0] ? Color.WHITE : 0xFF3A3A3A);
                d.setBackground(dg);
                dots.addView(d, LayoutHelper.createLinear(10, 10, 0, 0, i == SLIDES - 1 ? 0 : 12, 0));
            }
        };

        dialog.setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        }
        render[0].run();
        dialog.show();
        root.requestApplyInsets();
    }
}

