package org.telegram.vrg;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

public final class VrgThemeSync implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment fragment;
    private final Runnable extra;

    private VrgThemeSync(BaseFragment fragment, Runnable extra) {
        this.fragment = fragment;
        this.extra = extra;
    }

    public static VrgThemeSync attach(BaseFragment fragment, Runnable extra) {
        VrgThemeSync sync = new VrgThemeSync(fragment, extra);
        NotificationCenter.getGlobalInstance().addObserver(sync, NotificationCenter.didSetNewTheme);
        return sync;
    }

    public void detach() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            AndroidUtilities.runOnUIThread(this::apply);
        }
    }

    public void apply() {
        try {
            ActionBar ab = fragment.getActionBar();
            if (ab != null) {
                ab.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
                ab.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
                ab.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
                ab.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            }
            View v = fragment.getFragmentView();
            if (v != null) {
                v.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            }
            if (extra != null) {
                extra.run();
            }
        } catch (Throwable ignore) {
        }
    }

    public static void rebuildList(RecyclerListView listView) {
        if (listView == null) {
            return;
        }
        androidx.recyclerview.widget.RecyclerView.Adapter adapter = listView.getAdapter();
        if (adapter != null) {
            listView.setAdapter(null);
            listView.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }
}
