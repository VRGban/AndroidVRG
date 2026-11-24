package org.telegram.vrg;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VrgDeletedListActivity extends BaseFragment {
    private static final int ID_CLEAR = 1;
    private static final int ID_ENTRY_BASE = 100;

    private final long filterDialogId;
    private UniversalRecyclerView listView;
    private final List<VrgStore.DeletedLogEntry> entries = new ArrayList<>();

    public VrgDeletedListActivity() {
        this(0L);
    }

    public VrgDeletedListActivity(long filterDialogId) {
        this.filterDialogId = filterDialogId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Удалённые сообщения");
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

        reloadEntries();
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        return fragmentView;
    }

    private void reloadEntries() {
        entries.clear();
        entries.addAll(VrgStore.getInstance().getDeletedLogSync(currentAccount, filterDialogId, 150));
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (entries.isEmpty()) {
            items.add(UItem.asShadow("В этом чате пока нет сохранённых удалённых сообщений."));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        items.add(UItem.asHeader("Удалённые (" + entries.size() + ")"));
        for (int i = 0; i < entries.size(); i++) {
            VrgStore.DeletedLogEntry e = entries.get(i);
            String title = (filterDialogId != 0 ? "" : (resolvePeerTitle(e.dialogId) + " · ")) + fmt.format(new Date(e.deletedAt * 1000L));
            String text = TextUtils.isEmpty(e.preview) ? "[медиа / без текста]" : e.preview;
            items.add(VrgMarqueeButtonCell.Factory.of(ID_ENTRY_BASE + i, text, title, () -> openOrCopy(e)));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, "Очистить удалённые"));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CLEAR) {
            VrgStore.getInstance().clearDeletedLog(currentAccount, filterDialogId);
            entries.clear();
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Журнал очищен").show();
        }
    }

    private void openOrCopy(VrgStore.DeletedLogEntry e) {
        if (!TextUtils.isEmpty(e.preview)) {
            AndroidUtilities.addToClipboard(e.preview);
            BulletinFactory.of(this).createCopyBulletin("Текст скопирован").show();
        }
        if (e.dialogId != 0) {
            Bundle args = new Bundle();
            if (e.dialogId > 0) {
                args.putLong("user_id", e.dialogId);
            } else {
                args.putLong("chat_id", -e.dialogId);
            }
            args.putInt("message_id", e.msgId);
            presentFragment(new ChatActivity(args));
        }
    }

    private String resolvePeerTitle(long peerId) {
        if (peerId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
            if (user != null) {
                return (user.first_name != null ? user.first_name : "") + (user.last_name != null ? " " + user.last_name : "");
            }
        } else if (peerId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
            if (chat != null && chat.title != null) {
                return chat.title;
            }
        }
        return String.valueOf(peerId);
    }
}

