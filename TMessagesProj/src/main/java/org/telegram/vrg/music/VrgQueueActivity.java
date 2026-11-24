package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class VrgQueueActivity extends BaseFragment implements VrgMusicPlayer.Listener {
    private static final int ID_CLEAR = 1;

    private final VrgMusicPlayer player = VrgMusicPlayer.getInstance();
    private final ArrayList<VrgMusicTypes.TrackInfo> items = new ArrayList<>();
    private RecyclerListView listView;
    private QueueAdapter adapter;
    private TextView nowLabel;
    private TextView emptyLabel;
    private boolean dragging;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Очередь");
        actionBar.createMenu().addItem(ID_CLEAR, R.drawable.msg_delete);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_CLEAR) {
                    player.clearUpcoming();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        nowLabel = new TextView(context);
        nowLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nowLabel.setTypeface(AndroidUtilities.bold());
        nowLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nowLabel.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        col.addView(nowLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setText("Зажмите трек и перетащите, чтобы поменять порядок. Свайп вправо или влево убирает трек. Нажатие запускает его.");
        hint.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        col.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new QueueAdapter();
        listView.setAdapter(adapter);
        col.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        emptyLabel = new TextView(context);
        emptyLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setText("Очередь пуста.\nДобавьте треки: свайп вправо по треку или меню «⋮» → «Играть следующим».");
        emptyLabel.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        root.addView(emptyLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        ItemTouchHelper touch = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int a = from.getAdapterPosition();
                int b = to.getAdapterPosition();
                if (a < 0 || b < 0 || a >= items.size() || b >= items.size()) {
                    return false;
                }
                dragging = true;
                VrgMusicTypes.TrackInfo moved = items.remove(a);
                items.add(b, moved);
                adapter.notifyItemMoved(a, b);
                player.moveUpcoming(a, b);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                dragging = false;
                reload();
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos >= 0 && pos < items.size()) {
                    items.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    player.removeUpcoming(pos);
                    updateEmpty();
                }
            }
        });
        touch.attachToRecyclerView(listView);

        player.addListener(this);
        reload();
        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        player.removeListener(this);
    }

    private void reload() {
        if (listView == null) {
            return;
        }
        ArrayList<VrgMusicTypes.TrackInfo> fresh = player.getUpcoming();
        boolean same = fresh.size() == items.size();
        if (same) {
            for (int i = 0; i < fresh.size(); i++) {
                if (!TextUtils.equals(fresh.get(i).id, items.get(i).id)) {
                    same = false;
                    break;
                }
            }
        }
        if (!same) {
            items.clear();
            items.addAll(fresh);
            adapter.notifyDataSetChanged();
        } else {
            adapter.notifyItemRangeChanged(0, items.size());
        }
        VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
        nowLabel.setText(cur != null
            ? "Сейчас играет: " + cur.title + (TextUtils.isEmpty(cur.artist) ? "" : " — " + cur.artist)
            : "Ничего не играет");
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = items.isEmpty();
        emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onQueueChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!dragging) {
                reload();
            }
        });
    }

    @Override
    public void onTrackChanged(VrgMusicTypes.TrackInfo track) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!dragging) {
                reload();
            }
        });
    }

    private class QueueAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            TextView title = new TextView(parent.getContext());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTag("title");
            row.addView(title);
            TextView sub = new TextView(parent.getContext());
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            sub.setTag("sub");
            row.addView(sub);
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LinearLayout row = (LinearLayout) holder.itemView;
            TextView title = row.findViewWithTag("title");
            TextView sub = row.findViewWithTag("sub");
            VrgMusicTypes.TrackInfo t = items.get(position);
            boolean manual = position < player.getManualAhead();
            title.setText((position + 1) + ".  " + (manual ? "[Далее]  " : "") + t.title);
            sub.setText(t.artist + " · " + VrgMusicTypes.formatTime(t.durationMs));
            row.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < items.size()) {
                    player.playUpcoming(pos);
                }
            });
        }
    }
}

