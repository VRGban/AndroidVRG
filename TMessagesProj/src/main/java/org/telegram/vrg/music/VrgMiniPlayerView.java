package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.vrg.VrgConfig;

public class VrgMiniPlayerView extends LinearLayout implements VrgMusicPlayer.Listener {
    private final BaseFragment fragment;
    private final VrgMusicPlayer player = VrgMusicPlayer.getInstance();
    private final TextView title;
    private final TextView artist;
    private final TextView playBtn;

    public VrgMiniPlayerView(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(18));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        setBackground(bg);
        setElevation(AndroidUtilities.dp(6));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = new TextView(context);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        artist = new TextView(context);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        artist.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView prev = button(context, "⏮");
        prev.setOnClickListener(v -> player.prev());
        playBtn = button(context, "▶");
        playBtn.setOnClickListener(v -> player.togglePlayPause());
        TextView next = button(context, "⏭");
        next.setOnClickListener(v -> player.next());
        addView(prev, LayoutHelper.createLinear(40, 40));
        addView(playBtn, LayoutHelper.createLinear(40, 40));
        addView(next, LayoutHelper.createLinear(40, 40));

        setOnClickListener(v -> {
            if (fragment != null) {
                fragment.presentFragment(new VrgMusicActivity());
            }
        });
        sync();
    }

    private TextView button(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        return t;
    }

    private void sync() {
        VrgMusicTypes.TrackInfo t = player.getCurrentTrack();
        boolean allowed = VrgConfig.prefs().getBoolean("player_minibar", true);
        if (t == null || !allowed || player.getState() == VrgMusicTypes.PlaybackState.STOPPED && !player.isPlayerActive()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        title.setText(t.title);
        artist.setText((t.isYoutube() ? "YouTube · " : "SoundCloud · ") + t.artist);
        VrgMusicTypes.PlaybackState st = player.getState();
        playBtn.setText(st == VrgMusicTypes.PlaybackState.LOADING ? "…" : (st == VrgMusicTypes.PlaybackState.PLAYING ? "⏸" : "▶"));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        player.addListener(this);
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        player.removeListener(this);
    }

    @Override
    public void onTrackChanged(VrgMusicTypes.TrackInfo track) {
        sync();
    }

    @Override
    public void onStateChanged(VrgMusicTypes.PlaybackState state) {
        sync();
    }
}

