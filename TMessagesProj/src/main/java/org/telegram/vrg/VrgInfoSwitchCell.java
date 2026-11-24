package org.telegram.vrg;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class VrgInfoSwitchCell extends FrameLayout {
    private final TextView titleView;
    private final TextView subView;
    private final ImageView infoButton;
    private final Switch switchView;
    private final View dividerView;

    public VrgInfoSwitchCell(Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setMinimumHeight(AndroidUtilities.dp(56));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setMaxLines(3);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subView = new TextView(context);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        subView.setVisibility(GONE);
        textColumn.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        row.addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        infoButton = new ImageView(context);
        infoButton.setVisibility(GONE);

        switchView = new Switch(context);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        row.addView(switchView, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL));

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

    public void bind(CharSequence title, String infoText, boolean checked, boolean divider, Runnable onToggle) {
        titleView.setText(title);
        switchView.setChecked(checked, false);
        dividerView.setVisibility(divider ? VISIBLE : GONE);
        if (infoText != null && !infoText.isEmpty()) {
            subView.setVisibility(VISIBLE);
            subView.setText(infoText);
        } else {
            subView.setVisibility(GONE);
        }
        infoButton.setVisibility(GONE);
        infoButton.setOnClickListener(null);
        setOnClickListener(v -> {
            if (onToggle != null) {
                onToggle.run();
            }
        });
    }

    public static final class Factory extends UItem.UItemFactory<VrgInfoSwitchCell> {
        static {
            setup(new Factory());
        }

        public static UItem of(int id, CharSequence title, String info, boolean checked, Runnable onToggle) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.text = title;
            item.subtext = info;
            item.checked = checked;
            item.object = onToggle;
            return item;
        }

        @Override
        public VrgInfoSwitchCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new VrgInfoSwitchCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            VrgInfoSwitchCell cell = (VrgInfoSwitchCell) view;
            Runnable onToggle = item.object instanceof Runnable ? (Runnable) item.object : null;
            cell.bind(item.text, item.subtext != null ? item.subtext.toString() : null, item.checked, divider, onToggle);
        }
    }
}

