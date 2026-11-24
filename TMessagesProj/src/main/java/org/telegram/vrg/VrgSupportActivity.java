package org.telegram.vrg;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class VrgSupportActivity extends BaseFragment {
    private static final int ID_BOOSTY = 1;
    private static final int ID_CASEKIND = 2;
    private static final int ID_WALLET_BASE = 100;

    private static final String[][] WALLETS = {
        { "USDT (TON)", "UQD7oMbSvYoazp-Q3pH8PkyEUesE-s0Qv_G0wfbXInRW_33l" },
        { "USDT (TRC20)", "TGQXu9ir4wo4wRopSLuCoybiEvrMYBjtHu" },
        { "USDT (BEP20)", "0x4B6Cd26bDe3f66e7708c2A1B66099D78fDb9e7C6" },
        { "USDT (SOL)", "14GRXZf511FFLNTrwgtUEgxVTkmvajrYqw9NTKT6bS7C" },
        { "BTC", "bc1qrkdqzlj2klcxpreg8g7yd5lw7wtxsl0vgfam8q" },
        { "GRAM (TON)", "UQD7oMbSvYoazp-Q3pH8PkyEUesE-s0Qv_G0wfbXInRW_33l" },
    };

    private UniversalRecyclerView listView;
    private View bannerView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Поддержать проект");
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

        FrameLayout banner = new FrameLayout(context);
        banner.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(8));
        android.widget.LinearLayout badgeRow = new android.widget.LinearLayout(context);
        badgeRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        String[] kinds = {"supporter", "luna_crown", "luna_moon", "luna_bolt"};
        for (String kind : kinds) {
            VrgBadges.BadgeView v = new VrgBadges.BadgeView(context, kind, 36, false);
            badgeRow.addView(v, new android.widget.LinearLayout.LayoutParams(AndroidUtilities.dp(64), AndroidUtilities.dp(64)));
        }
        banner.addView(badgeRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        bannerView = banner;

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(bannerView));
        items.add(UItem.asShadow(
            "Поддерживая VRG, вы помогаете с серверами и сборками и получаете значок с анимацией в профиле навсегда.\n\n" +
            "• От 3 $ (~245 ₽) — VRG Supporter #N: значок-сердце и доступ в закрытый чат. Значок менять нельзя.\n" +
            "• От 50 $ — VRG Luna #N: можно выбирать и менять значок (корона, луна, молния или сердце) и доступ в закрытый чат.\n\n" +
            "Оплатить можно рублями или криптой.\n\n" +
            "После оплаты напишите @casekind и приложите скрин перевода — значок и доступ в чат будут выданы, как только прочитает."
        ));

        items.add(UItem.asHeader("Платформа и связь"));
        items.add(UItem.asButton(ID_BOOSTY, R.drawable.msg_info, "Boosty", "boosty.to/vrg"));
        items.add(UItem.asButton(ID_CASEKIND, R.drawable.msg_channel, "Отправить скрин оплаты", "@casekind"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Криптовалюта (нажмите, чтобы скопировать)"));
        for (int i = 0; i < WALLETS.length; i++) {
            String label = WALLETS[i][0];
            String addr = WALLETS[i][1];
            String preview = addr.length() > 16
                ? (addr.substring(0, 8) + "..." + addr.substring(addr.length() - 6))
                : addr;
            items.add(UItem.asButton(ID_WALLET_BASE + i, label, preview));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_BOOSTY) {
            Browser.openUrl(getContext(), "https://boosty.to/vrg");
        } else if (item.id == ID_CASEKIND) {
            Browser.openUrl(getContext(), "https://t.me/casekind");
        } else if (item.id >= ID_WALLET_BASE) {
            int idx = item.id - ID_WALLET_BASE;
            if (idx >= 0 && idx < WALLETS.length) {
                String label = WALLETS[idx][0];
                String addr = WALLETS[idx][1];
                AndroidUtilities.addToClipboard(addr);
                BulletinFactory.of(this).createCopyBulletin("Адрес " + label + " скопирован").show();
            }
        }
    }
}

