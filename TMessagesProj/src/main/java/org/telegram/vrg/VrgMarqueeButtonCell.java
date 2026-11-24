package org.telegram.vrg;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class VrgMarqueeButtonCell extends FrameLayout {
    private final TextView titleView;
    private final TextView valueView;
    private final View dividerView;

    public VrgMarqueeButtonCell(Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setMinimumHeight(AndroidUtilities.dp(56));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setMaxLines(3);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        valueView = new TextView(context);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueView.setMaxLines(3);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setMaxWidth(AndroidUtilities.dp(150));
        valueView.setGravity(Gravity.END);
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        dividerView = new View(context);
        dividerView.setBackgroundColor(Theme.getColor(Theme.key_divider));
        addView(dividerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 20, 0, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int minH = AndroidUtilities.dp(56);
        if (getMeasuredHeight() < minH) {
            setMeasuredDimension(getMeasuredWidth(), minH);
        }
    }

    public void bind(CharSequence title, CharSequence value, boolean divider, Runnable onClick) {
        titleView.setText(title);
        if (value != null && value.length() > 0) {
            valueView.setVisibility(VISIBLE);
            valueView.setText(value);
        } else {
            valueView.setVisibility(GONE);
        }
        dividerView.setVisibility(divider ? VISIBLE : GONE);
        setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            }
        });
    }

    public static final class Factory extends UItem.UItemFactory<VrgMarqueeButtonCell> {
        static {
            setup(new Factory());
        }

        public static UItem of(int id, CharSequence title, CharSequence value, Runnable onClick) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.subtext = value;
            item.object = onClick;
            return item;
        }

        @Override
        public VrgMarqueeButtonCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new VrgMarqueeButtonCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            VrgMarqueeButtonCell cell = (VrgMarqueeButtonCell) view;
            Runnable onClick = item.object instanceof Runnable ? (Runnable) item.object : null;
            cell.bind(item.text, item.subtext, divider, onClick);
        }
    }
}

