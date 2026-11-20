package org.telegram.vrg;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class VrgFakeProfileActivity extends BaseFragment {
    private static final int ID_PHONE = 1;
    private static final int ID_USERNAME = 2;
    private static final int ID_BIO = 3;
    private static final int ID_RESET = 4;
    private static final int ID_GIFTS = 5;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Фейк-профиль");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return fragmentView;
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(
            NotificationCenter.updateInterfaces,
            MessagesController.UPDATE_MASK_ALL
        );
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Локальная подмена своего профиля"));
        String phone = VrgFakeProfile.getPhone();
        String username = VrgFakeProfile.getUsername();
        String bio = VrgFakeProfile.getBio();
        items.add(UItem.asButton(ID_PHONE, "Фейковый номер телефона", TextUtils.isEmpty(phone) ? "оригинал" : phone));
        items.add(UItem.asButton(ID_USERNAME, "Фейковый юзернейм", TextUtils.isEmpty(username) ? "оригинал" : ("@" + username)));
        items.add(UItem.asButton(ID_BIO, "Фейковое «О себе»", TextUtils.isEmpty(bio) ? "оригинал" : (bio.length() > 20 ? bio.substring(0, 20) + "…" : bio)));
        int giftCount = VrgFakeProfile.getGiftSlugs().size();
        items.add(UItem.asButton(ID_GIFTS, "Фейковые NFT-подарки", giftCount == 0 ? "нет" : String.valueOf(giftCount)));
        items.add(UItem.asShadow("Отображается локально только на вашем экране при просмотре собственного профиля (например, для скриншотов)."));
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_delete, "Сбросить фейк-профиль"));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_PHONE:
                promptEdit("Фейковый номер", "+888 0000 0000 (пусто = оригинал)", VrgFakeProfile.getPhone(), v -> {
                    VrgFakeProfile.setPhone(v);
                    refresh();
                });
                break;
            case ID_USERNAME:
                promptEdit("Фейковый юзернейм", "username (пусто = оригинал)", VrgFakeProfile.getUsername(), v -> {
                    VrgFakeProfile.setUsername(v);
                    refresh();
                });
                break;
            case ID_BIO:
                promptEdit("Фейковое «О себе»", "Текст био (пусто = оригинал)", VrgFakeProfile.getBio(), v -> {
                    VrgFakeProfile.setBio(v);
                    refresh();
                });
                break;
            case ID_GIFTS:
                promptEdit("NFT-подарки", "Ссылки t.me/nft/… или слаги, по одному в строке", VrgFakeProfile.getGiftsText(), v -> {
                    VrgFakeProfile.setGiftsText(v);
                    refresh();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Откройте профиль заново, чтобы увидеть подарки").show();
                });
                break;
            case ID_RESET:
                VrgFakeProfile.reset();
                refresh();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Фейк-профиль сброшен").show();
                break;
        }
    }

    private interface Callback {
        void onSave(String val);
    }

    private void promptEdit(String title, String hint, String current, Callback cb) {
        if (getParentActivity() == null) {
            return;
        }
        FrameLayout wrap = new FrameLayout(getParentActivity());
        EditText edit = new EditText(getParentActivity());
        edit.setHint(hint);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setText(current != null ? current : "");
        edit.setSelection(edit.getText().length());
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 24, 8, 24, 8));

        new AlertDialog.Builder(getParentActivity())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Сохранить", (d, w) -> cb.onSave(edit.getText() != null ? edit.getText().toString() : ""))
            .setNegativeButton("Отмена", null)
            .show();
    }
}
