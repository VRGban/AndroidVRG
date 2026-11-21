package org.telegram.vrg;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class VrgSettingsActivity extends BaseFragment {
    private static final int ID_CAT_PLUS = 1;
    private static final int ID_CAT_GHOST = 2;
    private static final int ID_CAT_DELETED = 3;
    private static final int ID_CAT_LOOK = 4;
    private static final int ID_CAT_MISC = 5;
    private static final int ID_CAT_SECURITY = 6;
    private static final int ID_CAT_PLAYER = 7;
    private static final int ID_CAT_VOICE = 8;
    private static final int ID_CAT_PLAYER_SET = 9;
    private static final int ID_CAT_SIDEMENU = 20;
    private static final int ID_CAT_FAKE = 21;
    private static final int ID_CAT_AUTOREPLY = 22;

    private static final int ID_LINK_CHANNEL = 10;
    private static final int ID_LINK_SITE = 11;
    private static final int ID_LINK_SUPPORT = 12;
    private static final int ID_LINK_UPDATE = 13;
    private static final int ID_LINK_GITHUB = 14;

    private UniversalRecyclerView listView;
    private View headerView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("VRG");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        headerView = createHeaderView(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        themeSync = VrgThemeSync.attach(this, () -> {
            headerView = createHeaderView(context);
            VrgThemeSync.rebuildList(listView);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(false);
            }
        });
        return fragmentView;
    }

    private VrgThemeSync themeSync;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (themeSync != null) {
            themeSync.detach();
            themeSync = null;
        }
    }

    private View createHeaderView(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(
            AndroidUtilities.dp(20),
            AndroidUtilities.dp(22),
            AndroidUtilities.dp(20),
            AndroidUtilities.dp(18)
        );
        box.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.vrg_logo);
        logo.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), android.graphics.PorterDuff.Mode.SRC_IN));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, LayoutHelper.createLinear(76, 76, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView title = new TextView(context);
        title.setText("VRG");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        TextView subtitle = new TextView(context);
        subtitle.setText("Модифицированный Telegram с запрещённым функционалом.\nv" + VrgUpdater.CURRENT_VERSION);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setGravity(Gravity.CENTER);
        box.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        return box;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(headerView));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Категории"));
        items.add(UItem.asButton(ID_CAT_PLAYER, R.drawable.msg_filled_data_music, "VRG Music"));
        items.add(UItem.asButton(ID_CAT_PLUS, R.drawable.menu_premium_main, "VRG+"));
        items.add(UItem.asButton(ID_CAT_GHOST, R.drawable.msg_stealth_locked, "Призрак", getGhostSummary()));
        items.add(UItem.asButton(ID_CAT_SIDEMENU, R.drawable.msg_settings, "Боковое меню"));
        items.add(UItem.asButton(ID_CAT_DELETED, R.drawable.msg_delete, "Удалённые сообщения"));
        items.add(UItem.asButton(ID_CAT_LOOK, R.drawable.msg_palette, "Оформление"));
        items.add(UItem.asButton(ID_CAT_PLAYER_SET, R.drawable.msg_settings, "Плеер и YouTube"));
        items.add(UItem.asButton(ID_CAT_VOICE, R.drawable.msg_voicechat, "Изменение голоса", VrgVoiceChanger.isEnabled() ? VrgVoiceChanger.presetTitle(VrgVoiceChanger.getPresetId()) : "выкл"));
        items.add(UItem.asButton(ID_CAT_FAKE, R.drawable.msg_edit, "Фейк-профиль", VrgFakeProfile.isEnabled() ? "активен" : "выкл"));
        items.add(UItem.asButton(ID_CAT_AUTOREPLY, R.drawable.msg_message, "Автоответчик", VrgAutoReply.isEnabled() ? "вкл" : "выкл"));
        items.add(UItem.asButton(ID_CAT_MISC, R.drawable.msg_info, "Удобство"));
        items.add(UItem.asButton(ID_CAT_SECURITY, R.drawable.msg_secret, "Защита и пароли"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Ссылки"));
        items.add(UItem.asButton(ID_LINK_CHANNEL, R.drawable.msg_channel, "Канал", "@VRGclient"));
        items.add(UItem.asButton(ID_LINK_SITE, R.drawable.msg_link, "Сайт", "vrgclient.app"));
        items.add(UItem.asButton(ID_LINK_GITHUB, R.drawable.msg_link, "GitHub", "VRGban"));
        items.add(UItem.asButton(ID_LINK_SUPPORT, R.drawable.msg_saved, "Поддержать проект", "Boosty / Крипта"));
        items.add(UItem.asButton(ID_LINK_UPDATE, R.drawable.msg_info, "Проверить обновления", "v" + VrgUpdater.CURRENT_VERSION));
        items.add(UItem.asShadow("VRG Client · v" + VrgUpdater.CURRENT_VERSION));
    }

    private String getGhostSummary() {
        if (VrgConfig.isFullGhost()) {
            return "полный";
        }
        if (VrgConfig.isGhostRead() || VrgConfig.isGhostTyping() || VrgConfig.isGhostOnline() || VrgConfig.isGhostStories()) {
            return "частично";
        }
        return "выкл";
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_CAT_PLAYER:
                presentFragment(new org.telegram.vrg.music.VrgMusicActivity());
                break;
            case ID_CAT_PLAYER_SET:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_PLAYER));
                break;
            case ID_CAT_PLUS:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_PLUS));
                break;
            case ID_CAT_GHOST:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_GHOST));
                break;
            case ID_CAT_DELETED:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_DELETED));
                break;
            case ID_CAT_LOOK:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_LOOK));
                break;
            case ID_CAT_MISC:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_MISC));
                break;
            case ID_CAT_VOICE:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_VOICE));
                break;
            case ID_CAT_SIDEMENU:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_SIDEMENU));
                break;
            case ID_CAT_FAKE:
                presentFragment(new VrgFakeProfileActivity());
                break;
            case ID_CAT_AUTOREPLY:
                presentFragment(new VrgAutoReplyActivity());
                break;
            case ID_CAT_SECURITY:
                presentFragment(new VrgSectionActivity(VrgSectionActivity.SECTION_SECURITY));
                break;
            case ID_LINK_CHANNEL:
                Browser.openUrl(getContext(), "https://t.me/VRGclient");
                break;
            case ID_LINK_SITE:
                Browser.openUrl(getContext(), "https://vrgclient.app");
                break;
            case ID_LINK_GITHUB:
                Browser.openUrl(getContext(), "https://github.com/VRGban");
                break;
            case ID_LINK_SUPPORT:
                presentFragment(new VrgSupportActivity());
                break;
            case ID_LINK_UPDATE:
                VrgUpdater.check(this);
                break;
        }
    }
}
