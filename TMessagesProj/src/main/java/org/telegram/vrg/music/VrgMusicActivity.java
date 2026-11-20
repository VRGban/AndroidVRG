package org.telegram.vrg.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VrgMusicActivity extends BaseFragment implements VrgMusicPlayer.Listener {
    private static final int TAB_SEARCH = 0;
    private static final int TAB_LIBRARY = 1;
    private static final int TAB_PLAYLISTS = 2;
    private static final int REQ_PICK_TXT = 9411;

    private EditText pendingImportEdit;
    private TextView pendingImportStatus;

    private final VrgMusicPlayer player = VrgMusicPlayer.getInstance();
    private final VrgSoundCloudClient client = VrgSoundCloudClient.getInstance();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    private int activeTab = TAB_SEARCH;
    private String openPlaylistId = null;

    private final ArrayList<VrgMusicTypes.TrackInfo> searchResults = new ArrayList<>();
    private final ArrayList<VrgMusicTypes.TrackInfo> displayedTracks = new ArrayList<>();
    private final ArrayList<VrgMusicTypes.Playlist> displayedPlaylists = new ArrayList<>();

    private EditText searchInput;
    private TextView tabSearch, tabLibrary, tabPlaylists;
    private LinearLayout subBar;
    private TextView statusLabel;
    private RecyclerListView listView;
    private ListAdapter adapter;

    private LinearLayout miniBar;
    private ImageView miniArt;
    private TextView miniTitle, miniArtist;
    private VrgGlyph.View miniPlayBtn, miniNextBtn;

    private FrameLayout nowPlayingOverlay;
    private LinearLayout nowPlayingBg;
    private ImageView npArt;
    private ScrollView npLyricsScroll;
    private LinearLayout npLyricsContainer;
    private TextView npTitle, npArtist, npElapsed, npTotal;
    private VrgSeekView npSeek;
    private VrgGlyph.View npShuffleBtn, npPrevBtn, npPlayBtn, npNextBtn, npRepeatBtn, npLikeBtn;
    private TextView npLyricsBtn, npEqBtn, npRelatedBtn, npCacheBtn, npSendBtn;
    private View npAddPlBtn;
    private android.animation.ValueAnimator npBgAnim;
    private int npBgTop = 0xFF16161A;
    private boolean npVisible = false;
    private boolean lyricsMode = false;
    private boolean userSeeking = false;
    private String loadedLyricsTrackId = "";
    private String currentPlainLyrics = "";
    private ArrayList<VrgMusicTypes.LyricLine> currentSyncedLyrics = new ArrayList<>();
    private final ArrayList<TextView> lyricViews = new ArrayList<>();
    private int lastActiveLyricIdx = -1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("VRG Music");
        actionBar.createMenu().addItem(1, R.drawable.msg_settings_old);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (npVisible) {
                        hideNowPlaying();
                    } else if (openPlaylistId != null) {
                        openPlaylistId = null;
                        refreshList();
                    } else {
                        finishFragment();
                    }
                } else if (id == 1) {
                    showEqDialog();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout mainCol = new LinearLayout(context);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainCol, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout searchWrap = new LinearLayout(context);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setGravity(Gravity.CENTER_VERTICAL);
        searchWrap.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        searchInput = new EditText(context);
        searchInput.setHint("Поиск треков…");
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchInput.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(AndroidUtilities.dp(12));
        searchBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        searchInput.setBackground(searchBg);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearchOrFilter();
                AndroidUtilities.hideKeyboard(searchInput);
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (activeTab == TAB_LIBRARY || openPlaylistId != null) {
                    refreshList();
                }
            }
        });
        searchWrap.addView(searchInput, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 8, 0));

        TextView searchBtn = makePillButton(context, "Найти", true);
        searchBtn.setOnClickListener(v -> {
            runSearchOrFilter();
            AndroidUtilities.hideKeyboard(searchInput);
        });
        searchWrap.addView(searchBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        mainCol.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        tabSearch = makeTabButton(context, "Поиск");
        tabLibrary = makeTabButton(context, "Моя музыка");
        tabPlaylists = makeTabButton(context, "Плейлисты");
        tabSearch.setOnClickListener(v -> switchTab(TAB_SEARCH));
        tabLibrary.setOnClickListener(v -> switchTab(TAB_LIBRARY));
        tabPlaylists.setOnClickListener(v -> switchTab(TAB_PLAYLISTS));
        tabsRow.addView(tabSearch, LayoutHelper.createLinear(0, 36, 1f, 0, 0, 6, 0));
        tabsRow.addView(tabLibrary, LayoutHelper.createLinear(0, 36, 1f, 0, 0, 6, 0));
        tabsRow.addView(tabPlaylists, LayoutHelper.createLinear(0, 36, 1f));
        mainCol.addView(tabsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subBar = new LinearLayout(context);
        subBar.setOrientation(LinearLayout.VERTICAL);
        subBar.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(2), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        mainCol.addView(subBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusLabel = new TextView(context);
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusLabel.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        statusLabel.setVisibility(View.GONE);
        mainCol.addView(statusLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        mainCol.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        miniBar = createMiniPlayerBar(context);
        mainCol.addView(miniBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 62, 12, 4, 12, 12));

        nowPlayingOverlay = createNowPlayingOverlay(context);
        nowPlayingOverlay.setVisibility(View.GONE);
        root.addView(nowPlayingOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        player.addListener(this);
        switchTab(player.getLibrary().isEmpty() ? TAB_SEARCH : TAB_LIBRARY);
        syncMiniPlayer();
        syncNowPlaying();

        fragmentView = root;
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        player.removeListener(this);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (npVisible) {
            if (invoked) hideNowPlaying();
            return false;
        }
        if (openPlaylistId != null) {
            if (invoked) {
                openPlaylistId = null;
                refreshList();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private TextView makeTabButton(Context ctx, String label) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        return tv;
    }

    private TextView makePillButton(Context ctx, String label, boolean primary) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(10));
        if (primary) {
            bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            tv.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else {
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        tv.setBackground(bg);
        return tv;
    }

    private void switchTab(int tab) {
        activeTab = tab;
        openPlaylistId = null;
        styleTab(tabSearch, tab == TAB_SEARCH);
        styleTab(tabLibrary, tab == TAB_LIBRARY);
        styleTab(tabPlaylists, tab == TAB_PLAYLISTS);
        searchInput.setHint(tab == TAB_SEARCH ? "Поиск треков…" : "Фильтр по названию или артисту…");
        refreshList();
    }

    private void styleTab(TextView tv, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(10));
        if (active) {
            bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            tv.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        } else {
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        tv.setBackground(bg);
    }

    private void runSearchOrFilter() {
        String q = searchInput.getText() != null ? searchInput.getText().toString().trim() : "";
        if (activeTab == TAB_SEARCH) {
            if (q.isEmpty()) return;
            setStatus("Поиск «" + q + "»…");
            client.searchTracks(q, tracks -> {
                searchResults.clear();
                searchResults.addAll(tracks);
                setStatus(tracks.isEmpty() ? "Ничего не найдено" : null);
                refreshList();
                player.prefetchYoutubeResults(tracks);
            }, err -> {
                setStatus(err);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, err).show();
            });
        } else {
            refreshList();
        }
    }

    private void setStatus(String text) {
        if (statusLabel == null) return;
        if (TextUtils.isEmpty(text)) {
            statusLabel.setVisibility(View.GONE);
        } else {
            statusLabel.setText(text);
            statusLabel.setVisibility(View.VISIBLE);
        }
    }

    private static String formatCacheSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " КБ";
        }
        return String.format(java.util.Locale.US, "%.1f МБ", bytes / 1048576.0);
    }

    private int offlineCount(java.util.List<VrgMusicTypes.TrackInfo> tracks) {
        int n = 0;
        for (VrgMusicTypes.TrackInfo t : tracks) {
            if (player.isTrackCached(t.id)) {
                n++;
            }
        }
        return n;
    }

    private void updateTabTitles() {
        int total = player.getLibrary().size();
        int plCount = player.getPlaylists().size();
        tabLibrary.setText(total > 0 ? "Моя музыка · " + total : "Моя музыка");
        tabPlaylists.setText(plCount > 0 ? "Плейлисты · " + plCount : "Плейлисты");
    }

    private void refreshList() {
        if (getContext() == null) return;
        updateTabTitles();
        subBar.removeAllViews();
        displayedTracks.clear();
        displayedPlaylists.clear();
        String filter = searchInput != null && searchInput.getText() != null
            ? searchInput.getText().toString().trim().toLowerCase()
            : "";

        if (activeTab == TAB_SEARCH) {
            subBar.setVisibility(View.GONE);
            displayedTracks.addAll(searchResults);
            if (displayedTracks.isEmpty() && statusLabel.getVisibility() != View.VISIBLE) {
                setStatus("Введите название трека или исполнителя для поиска");
            }
        } else if (activeTab == TAB_LIBRARY) {
            subBar.setVisibility(View.VISIBLE);
            ArrayList<VrgMusicTypes.TrackInfo> lib = player.getLibrary();
            subBar.addView(makeInfoLine("Треков: " + lib.size() + "   ·   Офлайн: " + offlineCount(lib) + " / " + lib.size()),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView impBtn = makePillButton(getContext(), "Импорт", false);
            impBtn.setOnClickListener(v -> showImportDialog(null));
            TextView dlBtn = makePillButton(getContext(), "Скачать все", false);
            dlBtn.setOnClickListener(v -> {
                player.cacheTracks(lib);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Загрузка медиатеки в оффлайн-кеш…").show();
            });
            addButtonRow(impBtn, dlBtn);
            TextView clearBtn = makePillButton(getContext(), "Очистить кеш · " + formatCacheSize(player.getCacheSizeFor(lib)), false);
            clearBtn.setOnClickListener(v -> confirmClearCache(
                "Вы действительно хотите обнулить кеш любимой музыки?", lib, "Кеш любимой музыки очищен"));
            addWideButton(clearBtn);

            for (VrgMusicTypes.TrackInfo t : lib) {
                if (filter.isEmpty() || matchesFilter(t, filter)) {
                    displayedTracks.add(t);
                }
            }
            setStatus(displayedTracks.isEmpty() ? "Медиатека пуста. Нажмите ♡ на треке или импортируйте плейлист." : null);
        } else if (activeTab == TAB_PLAYLISTS) {
            subBar.setVisibility(View.VISIBLE);
            if (openPlaylistId == null) {
                TextView newPl = makePillButton(getContext(), "+ Новый плейлист", true);
                newPl.setOnClickListener(v -> promptCreatePlaylist(null));
                subBar.addView(newPl, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38));
                displayedPlaylists.addAll(player.getPlaylists());
                setStatus(displayedPlaylists.isEmpty() ? "Плейлистов пока нет" : null);
            } else {
                VrgMusicTypes.Playlist pl = player.findPlaylist(openPlaylistId);
                if (pl == null) {
                    openPlaylistId = null;
                    refreshList();
                    return;
                }
                LinearLayout head = new LinearLayout(getContext());
                head.setOrientation(LinearLayout.HORIZONTAL);
                head.setGravity(Gravity.CENTER_VERTICAL);
                TextView back = makePillButton(getContext(), "←", false);
                back.setOnClickListener(v -> {
                    openPlaylistId = null;
                    refreshList();
                });
                head.addView(back, LayoutHelper.createLinear(40, 34, 0, 0, 10, 0));
                TextView title = new TextView(getContext());
                title.setText(pl.name);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                title.setTypeface(AndroidUtilities.bold());
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                head.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                subBar.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
                subBar.addView(makeInfoLine("Треков: " + pl.tracks.size() + "   ·   Офлайн: " + offlineCount(pl.tracks) + " / " + pl.tracks.size()),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                TextView impBtn = makePillButton(getContext(), "Импорт", false);
                impBtn.setOnClickListener(v -> showImportDialog(pl.id));
                TextView dlBtn = makePillButton(getContext(), "Скачать", false);
                dlBtn.setOnClickListener(v -> {
                    player.cacheTracks(pl.tracks);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Загрузка плейлиста в оффлайн…").show();
                });
                addButtonRow(impBtn, dlBtn);
                TextView clearBtn = makePillButton(getContext(), "Очистить кеш · " + formatCacheSize(player.getCacheSizeFor(pl.tracks)), false);
                clearBtn.setOnClickListener(v -> confirmClearCache(
                    "Вы действительно хотите обнулить кеш плейлиста «" + pl.name + "»?", pl.tracks, "Кеш плейлиста очищен"));
                addWideButton(clearBtn);

                for (VrgMusicTypes.TrackInfo t : pl.tracks) {
                    if (filter.isEmpty() || matchesFilter(t, filter)) {
                        displayedTracks.add(t);
                    }
                }
                setStatus(displayedTracks.isEmpty() ? "Плейлист пуст" : null);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private TextView makeInfoLine(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        return t;
    }

    private void addWideButton(TextView button) {
        button.setSingleLine(true);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subBar.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 0, 0, 4));
    }

    private void confirmClearCache(String title, java.util.List<VrgMusicTypes.TrackInfo> tracks, String doneText) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
            .setTitle("Очистить кеш")
            .setMessage(title + "\n\nВы больше не сможете слушать эту музыку оффлайн, если не скачаете её ещё раз.")
            .setPositiveButton("Обнулить", (d, w) -> {
                player.clearCacheFor(tracks);
                refreshList();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, doneText).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void addButtonRow(TextView... buttons) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSingleLine(true);
            buttons[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            buttons[i].setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
            row.addView(buttons[i], LayoutHelper.createLinear(0, 38, 1f, 0, 0, i == buttons.length - 1 ? 0 : 8, 0));
        }
        subBar.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));
    }

    private static boolean matchesFilter(VrgMusicTypes.TrackInfo t, String filter) {
        return (t.title != null && t.title.toLowerCase().contains(filter))
            || (t.artist != null && t.artist.toLowerCase().contains(filter));
    }

    private LinearLayout createMiniPlayerBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(AndroidUtilities.dp(18));
        barBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        barBg.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        bar.setBackground(barBg);
        bar.setElevation(AndroidUtilities.dp(4));
        bar.setOnClickListener(v -> showNowPlaying());

        miniArt = new ImageView(ctx);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArt.setBackgroundColor(0xFF222222);
        miniArt.setClipToOutline(true);
        miniArt.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(10));
            }
        });
        bar.addView(miniArt, LayoutHelper.createLinear(46, 46, 0, 0, 10, 0));

        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        miniTitle = new TextView(ctx);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        miniTitle.setMarqueeRepeatLimit(-1);
        miniTitle.setSelected(true);
        miniTitle.setTypeface(AndroidUtilities.bold());
        miniTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        miniTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        info.addView(miniTitle);

        miniArtist = new TextView(ctx);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);
        miniArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        miniArtist.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        info.addView(miniArtist);
        bar.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        miniPlayBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.PLAY, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 26);
        miniPlayBtn.setOnClickListener(v -> player.togglePlayPause());
        bar.addView(miniPlayBtn, LayoutHelper.createLinear(44, 44));

        miniNextBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.NEXT, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 24);
        miniNextBtn.setOnClickListener(v -> player.next());
        bar.addView(miniNextBtn, LayoutHelper.createLinear(44, 44));

        bar.setVisibility(View.GONE);
        return bar;
    }

    private void syncMiniPlayer() {
        if (miniBar == null) return;
        VrgMusicTypes.TrackInfo t = player.getCurrentTrack();
        if (t == null) {
            miniBar.setVisibility(View.GONE);
            return;
        }
        miniBar.setVisibility(View.VISIBLE);
        miniTitle.setText(t.title);
        miniArtist.setText(sourceLabel(t) + t.artist);
        boolean playing = player.getState() == VrgMusicTypes.PlaybackState.PLAYING;
        boolean loading = player.getState() == VrgMusicTypes.PlaybackState.LOADING;
        miniPlayBtn.setType(playing || loading ? VrgGlyph.Type.PAUSE : VrgGlyph.Type.PLAY);
        Bitmap bmp = client.getCachedArtwork(t.artworkUrl);
        if (bmp != null) {
            miniArt.setImageBitmap(bmp);
        } else {
            miniArt.setImageResource(R.drawable.msg_filled_data_music);
            client.fetchArtwork(t.artworkUrl, loaded -> {
                VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
                if (cur != null && TextUtils.equals(cur.artworkUrl, t.artworkUrl)) {
                    miniArt.setImageBitmap(loaded);
                    if (npArt != null) npArt.setImageBitmap(loaded);
                    applyNowPlayingAccent(loaded);
                }
            });
        }
    }

    private FrameLayout createNowPlayingOverlay(Context ctx) {
        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        nowPlayingBg = new LinearLayout(ctx);
        nowPlayingBg.setOrientation(LinearLayout.VERTICAL);
        nowPlayingBg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { npBgTop, 0xFF050506 }));
        nowPlayingBg.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(22));
        overlay.addView(nowPlayingBg, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        VrgGlyph.View closeBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.CHEVRON_DOWN, 0xFFFFFFFF, 28);
        closeBtn.setOnClickListener(v -> hideNowPlaying());
        top.addView(closeBtn, LayoutHelper.createLinear(44, 44));

        TextView topLabel = new TextView(ctx);
        topLabel.setText("VRG MUSIC");
        topLabel.setTextColor(0xB3FFFFFF);
        topLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        topLabel.setTypeface(AndroidUtilities.bold());
        topLabel.setLetterSpacing(0.18f);
        topLabel.setGravity(Gravity.CENTER);
        top.addView(topLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        npAddPlBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.PLAYLIST_ADD, 0xFFFFFFFF, 26);
        npAddPlBtn.setOnClickListener(v -> {
            VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
            if (cur != null) showAddToPlaylistDialog(cur);
        });
        top.addView(npAddPlBtn, LayoutHelper.createLinear(44, 44));
        nowPlayingBg.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        FrameLayout center = new FrameLayout(ctx);
        npArt = new ImageView(ctx);
        npArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        npArt.setBackgroundColor(0xFF1C1C20);
        npArt.setClipToOutline(true);
        npArt.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(28));
            }
        });
        npArt.setElevation(AndroidUtilities.dp(24));
        int artSize = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(64), AndroidUtilities.dp(340));
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(artSize, artSize, Gravity.CENTER);
        center.addView(npArt, artLp);

        npLyricsScroll = new ScrollView(ctx);
        npLyricsScroll.setVisibility(View.GONE);
        npLyricsScroll.setVerticalScrollBarEnabled(false);
        npLyricsContainer = new LinearLayout(ctx);
        npLyricsContainer.setOrientation(LinearLayout.VERTICAL);
        npLyricsContainer.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(60), AndroidUtilities.dp(4), AndroidUtilities.dp(120));
        npLyricsScroll.addView(npLyricsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        center.addView(npLyricsScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        nowPlayingBg.addView(center, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, 0, 0, 20));

        LinearLayout metaRow = new LinearLayout(ctx);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout metaCol = new LinearLayout(ctx);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        npTitle = new TextView(ctx);
        npTitle.setSingleLine(true);
        npTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        npTitle.setMarqueeRepeatLimit(-1);
        npTitle.setSelected(true);
        npTitle.setTextColor(0xFFFFFFFF);
        npTitle.setTypeface(AndroidUtilities.bold());
        npTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 23);
        metaCol.addView(npTitle);
        npArtist = new TextView(ctx);
        npArtist.setSingleLine(true);
        npArtist.setEllipsize(TextUtils.TruncateAt.END);
        npArtist.setTextColor(0xB3FFFFFF);
        npArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        metaCol.addView(npArtist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        metaRow.addView(metaCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        npLikeBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.HEART, 0xFFFFFFFF, 28);
        npLikeBtn.setOnClickListener(v -> {
            VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
            if (cur != null) {
                player.toggleLibrary(cur);
                syncNowPlaying();
            }
        });
        metaRow.addView(npLikeBtn, LayoutHelper.createLinear(48, 48));
        nowPlayingBg.addView(metaRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        npSeek = new VrgSeekView(ctx);
        npSeek.setListener(new VrgSeekView.Listener() {
            @Override public void onProgress(float fraction) {
                long dur = player.getDurationMs();
                npElapsed.setText(VrgMusicTypes.formatTime((long) (dur * fraction)));
            }
            @Override public void onStart() { userSeeking = true; }
            @Override public void onStop(float fraction) {
                userSeeking = false;
                long dur = player.getDurationMs();
                if (dur > 0) player.seek((long) (dur * fraction));
            }
        });
        nowPlayingBg.addView(npSeek, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout timeRow = new LinearLayout(ctx);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        npElapsed = new TextView(ctx);
        npElapsed.setText("0:00");
        npElapsed.setTextColor(0x99FFFFFF);
        npElapsed.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeRow.addView(npElapsed, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        npTotal = new TextView(ctx);
        npTotal.setText("0:00");
        npTotal.setTextColor(0x99FFFFFF);
        npTotal.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeRow.addView(npTotal, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        nowPlayingBg.addView(timeRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        LinearLayout ctrlRow = new LinearLayout(ctx);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER_VERTICAL);
        npShuffleBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.SHUFFLE, 0xFFFFFFFF, 26);
        npShuffleBtn.setOnClickListener(v -> {
            player.setShuffle(!player.isShuffle());
            syncNowPlaying();
        });
        npPrevBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.PREV, 0xFFFFFFFF, 34);
        npPrevBtn.setOnClickListener(v -> player.prev());
        npPlayBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.PLAY, 0xFF000000, 38);
        npPlayBtn.setOnClickListener(v -> player.togglePlayPause());
        GradientDrawable playCircle = new GradientDrawable();
        playCircle.setShape(GradientDrawable.OVAL);
        playCircle.setColor(0xFFFFFFFF);
        npPlayBtn.setBackground(playCircle);
        npNextBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.NEXT, 0xFFFFFFFF, 34);
        npNextBtn.setOnClickListener(v -> player.next());
        npRepeatBtn = new VrgGlyph.View(ctx).set(VrgGlyph.Type.REPEAT, 0xFFFFFFFF, 26);
        npRepeatBtn.setOnClickListener(v -> {
            player.cycleRepeatMode();
            syncNowPlaying();
        });
        ctrlRow.addView(npShuffleBtn, LayoutHelper.createLinear(0, 52, 1f));
        ctrlRow.addView(npPrevBtn, LayoutHelper.createLinear(0, 60, 1f));
        ctrlRow.addView(npPlayBtn, LayoutHelper.createLinear(76, 76));
        ctrlRow.addView(npNextBtn, LayoutHelper.createLinear(0, 60, 1f));
        ctrlRow.addView(npRepeatBtn, LayoutHelper.createLinear(0, 52, 1f));
        nowPlayingBg.addView(ctrlRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        LinearLayout utilRow = new LinearLayout(ctx);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        utilRow.setGravity(Gravity.CENTER);
        npLyricsBtn = makeUtilChip(ctx, "Текст", VrgGlyph.Type.LYRICS, v -> toggleLyricsMode());
        npEqBtn = makeUtilChip(ctx, "EQ", VrgGlyph.Type.EQ, v -> showEqDialog());
        npRelatedBtn = makeUtilChip(ctx, "Похожие", VrgGlyph.Type.RADIO, v -> loadRelatedForCurrent());
        npCacheBtn = makeUtilChip(ctx, "Скачать", VrgGlyph.Type.DOWNLOAD, v -> {
            VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
            if (cur != null) {
                player.cacheTrack(cur);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Загрузка трека в оффлайн…").show();
            }
        });
        npSendBtn = makeUtilChip(ctx, "В чат", VrgGlyph.Type.SEND, v -> {
            VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
            if (cur != null) pickChatAndSend(cur);
        });
        TextView[] chips = { npLyricsBtn, npEqBtn, npRelatedBtn, npCacheBtn, npSendBtn };
        for (int i = 0; i < chips.length; i++) {
            utilRow.addView(chips[i], LayoutHelper.createLinear(0, 62, 1f, 0, 0, i == chips.length - 1 ? 0 : 8, 0));
        }
        nowPlayingBg.addView(utilRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return overlay;
    }

    private TextView makeCtrlBtn(Context ctx, String text, int sizeDp, View.OnClickListener click) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(click);
        return tv;
    }

    private TextView makeUtilChip(Context ctx, String text, VrgGlyph.Type glyph, View.OnClickListener click) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8));
        setChipIcon(tv, glyph);
        tv.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setColor(0x24FFFFFF);
        tv.setBackground(bg);
        tv.setOnClickListener(click);
        return tv;
    }

    private void setChipIcon(TextView tv, VrgGlyph.Type glyph) {
        int px = AndroidUtilities.dp(22);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(tv.getResources(), VrgGlyph.bitmap(glyph, px, 0xFFFFFFFF));
        d.setBounds(0, 0, px, px);
        tv.setCompoundDrawables(null, d, null, null);
    }

    private void showNowPlaying() {
        if (player.getCurrentTrack() == null) return;
        npVisible = true;
        nowPlayingOverlay.setVisibility(View.VISIBLE);
        syncNowPlaying();
    }

    private void hideNowPlaying() {
        npVisible = false;
        nowPlayingOverlay.setVisibility(View.GONE);
    }

    private void toggleLyricsMode() {
        lyricsMode = !lyricsMode;
        npArt.setVisibility(lyricsMode ? View.GONE : View.VISIBLE);
        npLyricsScroll.setVisibility(lyricsMode ? View.VISIBLE : View.GONE);
        npLyricsBtn.setAlpha(lyricsMode ? 1.0f : 0.7f);
        if (lyricsMode) {
            loadLyricsIfNeeded();
        }
    }

    private void loadLyricsIfNeeded() {
        VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
        if (cur == null || TextUtils.equals(loadedLyricsTrackId, cur.id)) {
            return;
        }
        loadedLyricsTrackId = cur.id;
        currentPlainLyrics = "";
        currentSyncedLyrics.clear();
        renderLyricsStatus("Поиск текста песни…");
        client.fetchLyrics(cur, (trackId, plain, synced) -> {
            if (!TextUtils.equals(loadedLyricsTrackId, trackId)) return;
            currentPlainLyrics = plain;
            currentSyncedLyrics = VrgMusicTypes.parseSyncedLyrics(synced);
            lastActiveLyricIdx = -1;
            renderLyricsContent();
        });
    }

    private void renderLyricsStatus(String msg) {
        if (npLyricsContainer == null || getContext() == null) return;
        npLyricsContainer.removeAllViews();
        lyricViews.clear();
        TextView tv = new TextView(getContext());
        tv.setText(msg);
        tv.setTextColor(0xAAFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setGravity(Gravity.CENTER);
        npLyricsContainer.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void renderLyricsContent() {
        if (npLyricsContainer == null || getContext() == null) return;
        npLyricsContainer.removeAllViews();
        lyricViews.clear();
        if (!currentSyncedLyrics.isEmpty()) {
            for (int i = 0; i < currentSyncedLyrics.size(); i++) {
                VrgMusicTypes.LyricLine line = currentSyncedLyrics.get(i);
                TextView tv = new TextView(getContext());
                tv.setText(line.text);
                tv.setTextColor(0x59FFFFFF);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
                tv.setTypeface(AndroidUtilities.bold());
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10));
                tv.setOnClickListener(v -> player.seek(line.timeMs));
                npLyricsContainer.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                lyricViews.add(tv);
            }
            updateActiveLyric(player.getPositionMs());
        } else if (!TextUtils.isEmpty(currentPlainLyrics)) {
            TextView tv = new TextView(getContext());
            tv.setText(currentPlainLyrics);
            tv.setTextColor(0xDDFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            tv.setLineSpacing(AndroidUtilities.dp(6), 1.0f);
            npLyricsContainer.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            renderLyricsStatus("Текст песни не найден");
        }
    }

    private void updateActiveLyric(long posMs) {
        if (!lyricsMode || currentSyncedLyrics.isEmpty() || lyricViews.isEmpty()) return;
        int active = -1;
        for (int i = 0; i < currentSyncedLyrics.size(); i++) {
            if (currentSyncedLyrics.get(i).timeMs <= posMs + 250) {
                active = i;
            } else {
                break;
            }
        }
        if (active == lastActiveLyricIdx) return;
        lastActiveLyricIdx = active;
        for (int i = 0; i < lyricViews.size(); i++) {
            TextView tv = lyricViews.get(i);
            tv.setTextColor(i == active ? 0xFFFFFFFF : 0x59FFFFFF);
            tv.animate().scaleX(i == active ? 1.06f : 1.0f).scaleY(i == active ? 1.06f : 1.0f).setDuration(250).start();
        }
        if (active >= 0 && active < lyricViews.size()) {
            View target = lyricViews.get(active);
            int scrollY = Math.max(0, target.getTop() - npLyricsScroll.getHeight() / 3);
            npLyricsScroll.smoothScrollTo(0, scrollY);
        }
    }

    private void syncNowPlaying() {
        if (npTitle == null) return;
        VrgMusicTypes.TrackInfo t = player.getCurrentTrack();
        if (t == null) return;
        npTitle.setText(t.title);
        npArtist.setText(sourceLabel(t) + t.artist);
        boolean playing = player.getState() == VrgMusicTypes.PlaybackState.PLAYING;
        boolean loading = player.getState() == VrgMusicTypes.PlaybackState.LOADING;
        npPlayBtn.setType(playing || loading ? VrgGlyph.Type.PAUSE : VrgGlyph.Type.PLAY);
        float coverScale = playing || loading ? 1.0f : 0.9f;
        npArt.animate().scaleX(coverScale).scaleY(coverScale).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        npLikeBtn.setType(player.isInLibrary(t.id) ? VrgGlyph.Type.HEART_FILLED : VrgGlyph.Type.HEART);
        npLikeBtn.setColor(player.isInLibrary(t.id) ? 0xFFFF5C5C : 0xFFFFFFFF);
        npShuffleBtn.setAlpha(player.isShuffle() ? 1.0f : 0.45f);
        VrgMusicTypes.RepeatMode rm = player.getRepeatMode();
        npRepeatBtn.setType(rm == VrgMusicTypes.RepeatMode.ONE ? VrgGlyph.Type.REPEAT_ONE : VrgGlyph.Type.REPEAT);
        npRepeatBtn.setAlpha(rm == VrgMusicTypes.RepeatMode.NONE ? 0.45f : 1.0f);
        npEqBtn.setAlpha(player.isEqEnabled() || player.getBoost() > 0.01f ? 1.0f : 0.7f);
        boolean cachedNow = player.isTrackCached(t.id);
        npCacheBtn.setText(cachedNow ? "Оффлайн" : "Скачать");
        setChipIcon(npCacheBtn, cachedNow ? VrgGlyph.Type.CHECK : VrgGlyph.Type.DOWNLOAD);

        Bitmap bmp = client.getCachedArtwork(t.artworkUrl);
        if (bmp != null) {
            npArt.setImageBitmap(bmp);
            applyNowPlayingAccent(bmp);
        } else {
            npArt.setImageResource(R.drawable.msg_filled_data_music);
        }
        if (lyricsMode) {
            loadLyricsIfNeeded();
        }
    }

    private void applyNowPlayingAccent(Bitmap bmp) {
        if (nowPlayingBg == null || bmp == null) return;
        try {
            Bitmap small = Bitmap.createScaledBitmap(bmp, 12, 12, true);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < small.getHeight(); y++) {
                for (int x = 0; x < small.getWidth(); x++) {
                    int px = small.getPixel(x, y);
                    int mx = Math.max(Color.red(px), Math.max(Color.green(px), Color.blue(px)));
                    int mn = Math.min(Color.red(px), Math.min(Color.green(px), Color.blue(px)));
                    int weight = 1 + (mx - mn) / 32;
                    r += (long) Color.red(px) * weight;
                    g += (long) Color.green(px) * weight;
                    b += (long) Color.blue(px) * weight;
                    n += weight;
                }
            }
            if (n == 0) return;
            int ar = (int) (r / n), ag = (int) (g / n), ab = (int) (b / n);
            final int target = Color.rgb(
                Math.max(14, ar * 55 / 100),
                Math.max(14, ag * 55 / 100),
                Math.max(16, ab * 55 / 100));
            final int from = npBgTop;
            if (npBgAnim != null) npBgAnim.cancel();
            npBgAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            npBgAnim.setDuration(500);
            npBgAnim.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                npBgTop = (Integer) new android.animation.ArgbEvaluator().evaluate(f, from, target);
                GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { npBgTop, Color.rgb(Color.red(npBgTop) / 3, Color.green(npBgTop) / 3, Color.blue(npBgTop) / 3), 0xFF050506 });
                nowPlayingBg.setBackground(gd);
            });
            npBgAnim.start();
        } catch (Throwable ignore) {
        }
    }

    private void loadRelatedForCurrent() {
        VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
        if (cur == null) return;
        hideNowPlaying();
        switchTab(TAB_SEARCH);
        setStatus("Подбор похожих треков…");
        client.fetchRelatedTracks(cur.id, tracks -> {
            searchResults.clear();
            searchResults.addAll(tracks);
            setStatus(tracks.isEmpty() ? "Похожие треки не найдены" : ("Похожие на «" + cur.title + "»"));
            refreshList();
        });
    }

    private void showEqDialog() {
        if (getParentActivity() == null) return;
        final Context ctx = getParentActivity();
        final android.app.Dialog dialog = new android.app.Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        float rad = AndroidUtilities.dp(28);
        bg.setCornerRadii(new float[] { rad, rad, rad, rad, 0, 0, 0, 0 });
        bg.setColor(0xFF111113);
        root.setBackground(bg);
        root.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(10), AndroidUtilities.dp(22), AndroidUtilities.dp(22) + AndroidUtilities.navigationBarHeight);

        View handle = new View(ctx);
        GradientDrawable hb = new GradientDrawable();
        hb.setCornerRadius(AndroidUtilities.dp(3));
        hb.setColor(0x33FFFFFF);
        handle.setBackground(hb);
        root.addView(handle, LayoutHelper.createLinear(40, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("Эквалайзер");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        head.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        Switch eqSwitch = new Switch(ctx);
        eqSwitch.setChecked(player.isEqEnabled());
        eqSwitch.setOnCheckedChangeListener((b2, checked) -> player.setEqEnabled(checked));
        head.addView(eqSwitch);
        root.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        final VrgVSlider[] sliders = new VrgVSlider[5];
        final TextView[] valLabels = new TextView[5];
        final TextView[] presetChips = new TextView[VrgMusicTypes.DEFAULT_EQ_PRESETS.length];

        final Runnable[] refreshPresetState = new Runnable[1];
        refreshPresetState[0] = () -> {
            float[] g = player.getEqGains();
            for (int i = 0; i < presetChips.length; i++) {
                float[] pg = VrgMusicTypes.DEFAULT_EQ_PRESETS[i].gainsDb;
                boolean same = player.isEqEnabled();
                for (int k = 0; k < 5 && same; k++) {
                    if (Math.abs(pg[k] - g[k]) > 0.01f) same = false;
                }
                GradientDrawable cb = new GradientDrawable();
                cb.setCornerRadius(AndroidUtilities.dp(20));
                cb.setColor(same ? 0xFFFFFFFF : 0x24FFFFFF);
                presetChips[i].setBackground(cb);
                presetChips[i].setTextColor(same ? 0xFF000000 : 0xFFFFFFFF);
            }
        };

        HorizontalScrollView presetScroll = new HorizontalScrollView(ctx);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(ctx);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < VrgMusicTypes.DEFAULT_EQ_PRESETS.length; i++) {
            final VrgMusicTypes.EqPreset preset = VrgMusicTypes.DEFAULT_EQ_PRESETS[i];
            TextView chip = new TextView(ctx);
            chip.setText(preset.name);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            chip.setOnClickListener(v -> {
                player.setEqEnabled(true);
                eqSwitch.setChecked(true);
                player.setEqGains(preset.gainsDb);
                for (int k = 0; k < 5; k++) {
                    sliders[k].setValue(preset.gainsDb[k]);
                    valLabels[k].setText(String.format("%+.0f", preset.gainsDb[k]));
                }
                refreshPresetState[0].run();
            });
            presetChips[i] = chip;
            presetRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 8, 0));
        }
        presetScroll.addView(presetRow);
        root.addView(presetScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        LinearLayout bands = new LinearLayout(ctx);
        bands.setOrientation(LinearLayout.HORIZONTAL);
        float[] gains = player.getEqGains();
        for (int i = 0; i < 5; i++) {
            final int band = i;
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView val = new TextView(ctx);
            val.setText(String.format("%+.0f", gains[i]));
            val.setTextColor(0xB3FFFFFF);
            val.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            val.setTypeface(AndroidUtilities.bold());
            valLabels[i] = val;
            col.addView(val, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

            VrgVSlider vs = new VrgVSlider(ctx);
            vs.setRange(-12f, 12f);
            vs.setValue(gains[i]);
            vs.setListener((v, fromUser) -> {
                val.setText(String.format("%+.0f", v));
                player.setEqBand(band, v);
                refreshPresetState[0].run();
            });
            sliders[i] = vs;
            col.addView(vs, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 170));

            TextView lbl = new TextView(ctx);
            lbl.setText(VrgMusicTypes.EQ_LABELS[i].replace(" Гц", "").replace(" кГц", "к"));
            lbl.setTextColor(0x99FFFFFF);
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            lbl.setGravity(Gravity.CENTER);
            col.addView(lbl, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            bands.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        }
        root.addView(bands, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));
        refreshPresetState[0].run();

        final TextView boostTitle = new TextView(ctx);
        boostTitle.setText("Усиление громкости · " + Math.round(player.getBoost() * 100) + "%");
        boostTitle.setTextColor(0xFFFFFFFF);
        boostTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        boostTitle.setTypeface(AndroidUtilities.bold());
        root.addView(boostTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));
        VrgSeekView boost = new VrgSeekView(ctx);
        boost.setProgress(player.getBoost());
        boost.setListener(new VrgSeekView.Listener() {
            @Override public void onProgress(float f) {
                int pct = Math.round(f * 100);
                boostTitle.setText("Усиление громкости · " + pct + "%");
                player.setBoost(pct / 100f);
            }
            @Override public void onStart() {}
            @Override public void onStop(float f) {}
        });
        root.addView(boost, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        TextView done = new TextView(ctx);
        done.setText("Готово");
        done.setTextColor(0xFF000000);
        done.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        done.setTypeface(AndroidUtilities.bold());
        done.setGravity(Gravity.CENTER);
        GradientDrawable db = new GradientDrawable();
        db.setCornerRadius(AndroidUtilities.dp(26));
        db.setColor(0xFFFFFFFF);
        done.setBackground(db);
        done.setOnClickListener(v -> dialog.dismiss());
        root.addView(done, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        dialog.setContentView(scroll);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
            w.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }

    private void promptCreatePlaylist(VrgMusicTypes.TrackInfo autoAdd) {
        if (getParentActivity() == null) return;
        FrameLayout wrap = new FrameLayout(getParentActivity());
        EditText edit = new EditText(getParentActivity());
        edit.setHint("Название плейлиста");
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 24, 8, 24, 8));

        new AlertDialog.Builder(getParentActivity())
            .setTitle("Новый плейлист")
            .setView(wrap)
            .setPositiveButton("Создать", (d, w) -> {
                String name = edit.getText() != null ? edit.getText().toString().trim() : "";
                if (name.isEmpty()) return;
                VrgMusicTypes.Playlist p = player.createPlaylist(name);
                if (autoAdd != null) {
                    player.addTrackToPlaylist(p.id, autoAdd);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Добавлено в «" + p.name + "»").show();
                }
                refreshList();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showAddToPlaylistDialog(VrgMusicTypes.TrackInfo track) {
        if (getParentActivity() == null) return;
        ArrayList<VrgMusicTypes.Playlist> pls = player.getPlaylists();
        CharSequence[] items = new CharSequence[pls.size() + 1];
        items[0] = "+ Новый плейлист…";
        for (int i = 0; i < pls.size(); i++) {
            items[i + 1] = pls.get(i).name + " (" + pls.get(i).tracks.size() + ")";
        }
        new AlertDialog.Builder(getParentActivity())
            .setTitle("Добавить в плейлист")
            .setItems(items, (d, which) -> {
                if (which == 0) {
                    promptCreatePlaylist(track);
                } else if (which - 1 < pls.size()) {
                    VrgMusicTypes.Playlist p = pls.get(which - 1);
                    player.addTrackToPlaylist(p.id, track);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Добавлено в «" + p.name + "»").show();
                }
            })
            .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_TXT && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null && getParentActivity() != null) {
            try (java.io.InputStream is = getParentActivity().getContentResolver().openInputStream(data.getData())) {
                if (is != null) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) >= 0) {
                        bos.write(buf, 0, n);
                    }
                    String text = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    if (pendingImportEdit != null) {
                        pendingImportEdit.setText(text);
                    }
                    if (pendingImportStatus != null) {
                        pendingImportStatus.setText("Файл загружен. Нажмите «Импортировать».");
                    }
                }
            } catch (Throwable e) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Не удалось прочитать файл").show();
            }
        }
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        String low = s.toLowerCase();
        for (int i = 0; i < low.length(); i++) {
            char c = low.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private boolean isDuplicateTrack(String targetPlaylistId, VrgMusicTypes.TrackInfo cand) {
        List<VrgMusicTypes.TrackInfo> target;
        if (targetPlaylistId == null) {
            target = player.getLibrary();
        } else {
            VrgMusicTypes.Playlist pl = player.findPlaylist(targetPlaylistId);
            target = pl != null ? pl.tracks : new ArrayList<>();
        }
        String candFull = normalizeKey(cand.artist + cand.title);
        for (int i = 0; i < target.size(); i++) {
            VrgMusicTypes.TrackInfo ex = target.get(i);
            if (!TextUtils.isEmpty(ex.id) && ex.id.equals(cand.id)) return true;
            if (!candFull.isEmpty() && candFull.equals(normalizeKey(ex.artist + ex.title))) return true;
        }
        return false;
    }

    private void showImportDialog(String targetPlaylistId) {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        String targetName = "Моя музыка";
        if (targetPlaylistId != null) {
            VrgMusicTypes.Playlist pl = player.findPlaylist(targetPlaylistId);
            targetName = pl != null ? pl.name : "Плейлист";
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(4));

        LinearLayout fileRow = new LinearLayout(ctx);
        fileRow.setOrientation(LinearLayout.HORIZONTAL);
        fileRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView pickFileBtn = makePillButton(ctx, "Выбрать .txt файл…", false);
        pickFileBtn.setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[] { "text/plain", "text/csv", "audio/x-mpegurl", "*/*" });
                startActivityForResult(intent, REQ_PICK_TXT);
            } catch (Throwable e) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Не удалось открыть выбор файла").show();
            }
        });
        fileRow.addView(pickFileBtn, LayoutHelper.createLinear(0, 38, 1f, 0, 0, 8, 0));

        TextView infoBtn = makePillButton(ctx, "i", false);
        infoBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(ctx)
                .setTitle("Как импортировать")
                .setMessage("1. Текстом: вставь строки вида «Исполнитель - Название» (по одному треку на строку).\n\n2. Файлом: выбери .txt / .csv / .m3u список.\n\n3. Ссылкой: вставь ссылку на публичный плейлист или альбом.")
                .setPositiveButton("Закрыть", null)
                .show();
        });
        fileRow.addView(infoBtn, LayoutHelper.createLinear(38, 38));
        root.addView(fileRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditText edit = new EditText(ctx);
        edit.setHint("Список треков (по строкам) или ссылка");
        edit.setMinLines(4);
        edit.setMaxLines(8);
        edit.setGravity(Gravity.TOP | Gravity.LEFT);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        root.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView statusLbl = new TextView(ctx);
        statusLbl.setText("Дубликаты в этом списке будут автоматически пропущены.");
        statusLbl.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusLbl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        root.addView(statusLbl, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        pendingImportEdit = edit;
        pendingImportStatus = statusLbl;

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("Импорт в «" + targetName + "»")
            .setView(root)
            .setPositiveButton("Импортировать", null)
            .setNegativeButton("Отмена", null)
            .create();
        dialog.setOnDismissListener(d -> {
            pendingImportEdit = null;
            pendingImportStatus = null;
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String rawText = edit.getText() != null ? edit.getText().toString().trim() : "";
                if (rawText.isEmpty()) {
                    statusLbl.setText("Вставьте список, ссылку или выберите .txt файл.");
                    return;
                }
                if (!rawText.contains("\n") && rawText.contains("open.spotify.com/")) {
                    statusLbl.setText("Загружаю плейлист…");
                    client.fetchSpotifyTracks(rawText, (queries, err) -> {
                        if (!TextUtils.isEmpty(err) || queries.isEmpty()) {
                            statusLbl.setText(TextUtils.isEmpty(err) ? "Треки не найдены" : err);
                            return;
                        }
                        runBatchImport(queries, targetPlaylistId, statusLbl, dialog);
                    });
                } else if (!rawText.contains("\n") && (rawText.contains("youtube.com/playlist") || (rawText.contains("youtube.com/watch") && rawText.contains("list=")))) {
                    statusLbl.setText("Загружаю плейлист YouTube…");
                    importExecutor.execute(() -> {
                        final String[] err = new String[1];
                        final ArrayList<VrgMusicTypes.TrackInfo> tracks = VrgYoutube.playlistSync(rawText, err);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (tracks.isEmpty()) {
                                statusLbl.setText(TextUtils.isEmpty(err[0]) ? "Треки не найдены" : err[0]);
                                return;
                            }
                            int added = 0, skipped = 0;
                            for (VrgMusicTypes.TrackInfo t : tracks) {
                                if (isDuplicateTrack(targetPlaylistId, t)) {
                                    skipped++;
                                } else {
                                    if (targetPlaylistId != null) player.addTrackToPlaylist(targetPlaylistId, t);
                                    else player.addToLibrary(t);
                                    added++;
                                }
                            }
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Готово! Добавлено: " + added + " · Дубликатов пропущено: " + skipped).show();
                            refreshList();
                            dialog.dismiss();
                        });
                    });
                } else if (!rawText.contains("\n") && rawText.contains("soundcloud.com/")) {
                    statusLbl.setText("Загружаю плейлист…");
                    client.resolvePlaylistUrl(rawText, (tracks, err) -> {
                        if (!TextUtils.isEmpty(err) || tracks.isEmpty()) {
                            statusLbl.setText(TextUtils.isEmpty(err) ? "Треки не найдены" : err);
                            return;
                        }
                        int added = 0, skipped = 0;
                        for (VrgMusicTypes.TrackInfo t : tracks) {
                            if (isDuplicateTrack(targetPlaylistId, t)) {
                                skipped++;
                            } else {
                                if (targetPlaylistId != null) player.addTrackToPlaylist(targetPlaylistId, t);
                                else player.addToLibrary(t);
                                added++;
                            }
                        }
                        String summary = "Готово! Добавлено: " + added + " · Дубликатов пропущено: " + skipped;
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, summary).show();
                        refreshList();
                        dialog.dismiss();
                    });
                } else {
                    ArrayList<String> lines = new ArrayList<>();
                    for (String line : rawText.split("\n")) {
                        String clean = line.trim();
                        if (clean.isEmpty() || clean.startsWith("#")) continue;
                        clean = clean.replaceFirst("^\\d+[.)\\s-]+", "").trim();
                        if (!clean.isEmpty()) {
                            lines.add(clean);
                        }
                    }
                    if (lines.isEmpty()) {
                        statusLbl.setText("Не найдено ни одной строки для импорта.");
                        return;
                    }
                    runBatchImport(lines, targetPlaylistId, statusLbl, dialog);
                }
            });
        });
        dialog.show();
    }

    private static String sourceLabel(VrgMusicTypes.TrackInfo t) {
        return t != null && t.isYoutube() ? "YouTube · " : "SoundCloud · ";
    }

    private void runBatchImport(ArrayList<String> queries, String targetPlaylistId, TextView statusLbl, AlertDialog dialog) {
        final int total = queries.size();
        importExecutor.execute(() -> {
            int added = 0, skipped = 0, notFound = 0;
            for (int i = 0; i < total; i++) {
                final int idx = i + 1;
                final String q = queries.get(i);
                AndroidUtilities.runOnUIThread(() -> {
                    if (statusLbl != null) {
                        statusLbl.setText("Поиск " + idx + " / " + total + ": " + q);
                    }
                });
                VrgMusicTypes.TrackInfo found = client.searchBestTrackSync(q);
                if (found == null || !found.isValid()) {
                    notFound++;
                } else {
                    final VrgMusicTypes.TrackInfo t = found;
                    final boolean[] isDup = new boolean[1];
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (isDuplicateTrack(targetPlaylistId, t)) {
                            isDup[0] = true;
                        } else {
                            if (targetPlaylistId != null) player.addTrackToPlaylist(targetPlaylistId, t);
                            else player.addToLibrary(t);
                        }
                        latch.countDown();
                    });
                    try { latch.await(); } catch (InterruptedException ignore) {}
                    if (isDup[0]) skipped++;
                    else added++;
                }
            }
            final String summary = "Готово! Добавлено: " + added + " · Дубликатов: " + skipped + " · Не найдено: " + notFound;
            AndroidUtilities.runOnUIThread(() -> {
                if (statusLbl != null) statusLbl.setText(summary);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, summary).show();
                refreshList();
                try { dialog.dismiss(); } catch (Throwable ignore) {}
            });
        });
    }

    private void pickChatAndSend(VrgMusicTypes.TrackInfo track) {
        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", org.telegram.ui.DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("resetDelegate", false);
        org.telegram.ui.DialogsActivity fragment = new org.telegram.ui.DialogsActivity(args);
        fragment.setDelegate((f, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            android.util.Log.d("VRG_SEND", "delegate dids=" + (dids == null ? -1 : dids.size()));
            if (dids == null || dids.isEmpty()) {
                return true;
            }
            final ArrayList<Long> targets = new ArrayList<>();
            for (int i = 0; i < dids.size(); i++) {
                targets.add(dids.get(i).dialogId);
            }
            f.finishFragment();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Подготовка трека…").show();
            sendTrackToDialogs(track, targets);
            return true;
        });
        presentFragment(fragment);
    }

    private void sendTrackToDialogs(VrgMusicTypes.TrackInfo track, ArrayList<Long> targets) {
        android.util.Log.d("VRG_SEND", "send start track=" + track.id + " targets=" + targets.size());
        importExecutor.execute(() -> {
            android.util.Log.d("VRG_SEND", "executor running");
            byte[] bytes = player.readCachedBytes(track.id);
            if (bytes == null) {
                String url = client.resolveStreamUrlSync(track);
                if (!TextUtils.isEmpty(url)) {
                    bytes = url.contains(".m3u8") ? client.downloadHlsSync(url) : client.downloadBytesSync(url);
                }
            }
            android.util.Log.d("VRG_SEND", "bytes=" + (bytes == null ? -1 : bytes.length));
            if (bytes == null || bytes.length < 1024) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Не удалось получить трек для отправки").show());
                return;
            }
            byte[] cover = null;
            Bitmap coverBmp = client.getCachedArtwork(track.artworkUrl);
            if (coverBmp != null) {
                java.io.ByteArrayOutputStream cos = new java.io.ByteArrayOutputStream();
                coverBmp.compress(Bitmap.CompressFormat.JPEG, 88, cos);
                cover = cos.toByteArray();
            }
            bytes = VrgId3.withTags(bytes, track.title, track.artist, cover);
            String name = (track.artist.isEmpty() ? "" : track.artist + " - ") + track.title;
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            java.io.File base = ApplicationLoader.applicationContext.getExternalCacheDir();
            if (base == null) {
                base = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            }
            if (base == null) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Нет доступа к хранилищу для отправки").show());
                return;
            }
            java.io.File dir = new java.io.File(base, "vrg_music_send");
            dir.mkdirs();
            java.io.File[] old = dir.listFiles();
            if (old != null) {
                for (java.io.File of : old) {
                    if (System.currentTimeMillis() - of.lastModified() > 15 * 60 * 1000L) {
                        of.delete();
                    }
                }
            }
            final java.io.File out = new java.io.File(dir, name + ".mp3");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(bytes);
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Не удалось подготовить файл").show());
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                android.util.Log.d("VRG_SEND", "prepareSending file=" + out.length());
                for (long did : targets) {
                    SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(currentAccount), out.getAbsolutePath(), out.getAbsolutePath(), null, null, "audio/mpeg", did, null, null, null, null, null, true, 0, null, null, false);
                }
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Трек отправлен").show();
            });
        });
    }

    private void showTrackMenu(VrgMusicTypes.TrackInfo track) {
        if (getParentActivity() == null) return;
        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();

        items.add(player.isInLibrary(track.id) ? "Убрать из медиатеки" : "Добавить в медиатеку");
        actions.add(() -> {
            player.toggleLibrary(track);
            refreshList();
        });
        items.add("Добавить в плейлист");
        actions.add(() -> showAddToPlaylistDialog(track));
        items.add(player.isTrackCached(track.id) ? "✓ Уже в оффлайн-кеше" : "Скачать оффлайн");
        actions.add(() -> {
            player.cacheTrack(track);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Загрузка в оффлайн-кеш…").show();
        });
        items.add("Отправить в чат…");
        actions.add(() -> pickChatAndSend(track));
        items.add("Похожие треки");
        actions.add(() -> {
            switchTab(TAB_SEARCH);
            setStatus("Подбор похожих треков…");
            client.fetchRelatedTracks(track.id, list -> {
                searchResults.clear();
                searchResults.addAll(list);
                setStatus(list.isEmpty() ? "Не найдено" : ("Похожие на «" + track.title + "»"));
                refreshList();
            });
        });
        if (!TextUtils.isEmpty(track.permalinkUrl)) {
            items.add("Скопировать ссылку");
            actions.add(() -> {
                AndroidUtilities.addToClipboard(track.permalinkUrl);
                BulletinFactory.of(this).createCopyBulletin("Ссылка скопирована").show();
            });
        }
        if (openPlaylistId != null) {
            final String plId = openPlaylistId;
            items.add("Выше в плейлисте");
            actions.add(() -> {
                player.moveTrackInPlaylist(plId, track.id, -1);
                refreshList();
            });
            items.add("Ниже в плейлисте");
            actions.add(() -> {
                player.moveTrackInPlaylist(plId, track.id, 1);
                refreshList();
            });
            items.add("Убрать из плейлиста");
            actions.add(() -> {
                player.removeTrackFromPlaylist(plId, track.id);
                refreshList();
            });
        }

        new AlertDialog.Builder(getParentActivity())
            .setTitle(track.title)
            .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                if (which >= 0 && which < actions.size()) actions.get(which).run();
            })
            .show();
    }

    @Override
    public void onTrackChanged(VrgMusicTypes.TrackInfo track) {
        loadedLyricsTrackId = "";
        if (track != null && track.isYoutube() && !org.telegram.vrg.VrgConfig.prefs().getBoolean("yt_experimental_seen", false) && getParentActivity() != null) {
            org.telegram.vrg.VrgConfig.prefs().edit().putBoolean("yt_experimental_seen", true).apply();
            new AlertDialog.Builder(getParentActivity())
                .setTitle("YouTube: экспериментальная функция")
                .setMessage("Воспроизведение с YouTube в VRG экспериментальное. Треки могут загружаться долго (до 30–50 секунд) или не играть совсем. SoundCloud работает стабильно. Источник можно выбрать в настройках VRG → Плеер.")
                .setPositiveButton("Понятно", null)
                .show();
        }
        syncMiniPlayer();
        syncNowPlaying();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onStateChanged(VrgMusicTypes.PlaybackState state) {
        syncMiniPlayer();
        syncNowPlaying();
    }

    @Override
    public void onPositionChanged(long positionMs, long durationMs) {
        if (!userSeeking && npSeek != null && durationMs > 0) {
            npSeek.setProgress(positionMs / (float) durationMs);
            npElapsed.setText(VrgMusicTypes.formatTime(positionMs));
            npTotal.setText(VrgMusicTypes.formatTime(durationMs));
        }
        updateActiveLyric(positionMs);
    }

    @Override
    public void onLibraryChanged() {
        syncNowPlaying();
        if (activeTab == TAB_LIBRARY) refreshList();
        else if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onPlaylistsChanged() {
        if (activeTab == TAB_PLAYLISTS) refreshList();
    }

    @Override
    public void onTrackCached(String trackId) {
        syncNowPlaying();
        if (adapter != null) adapter.notifyDataSetChanged();
        if (activeTab != TAB_SEARCH && !cacheRefreshPending) {
            cacheRefreshPending = true;
            AndroidUtilities.runOnUIThread(() -> {
                cacheRefreshPending = false;
                if (getParentActivity() != null) {
                    refreshList();
                }
            }, 700);
        }
    }

    private boolean cacheRefreshPending;

    @Override
    public void onEqChanged() {
        syncNowPlaying();
    }

    @Override
    public void onInfo(String message) {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, message).setDuration(4000).show();
    }

    @Override
    public void onError(String message) {
        if (VrgYoutube.isAuthMessage(message)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "YouTube просит войти", message, "Открыть настройки",
                () -> presentFragment(new org.telegram.vrg.VrgSectionActivity(org.telegram.vrg.VrgSectionActivity.SECTION_PLAYER)))
                .setDuration(12000).show();
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, message, 6).setDuration(8000).show();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            if (activeTab == TAB_PLAYLISTS && openPlaylistId == null) {
                return displayedPlaylists.size();
            }
            return displayedTracks.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setBackground(Theme.getSelectorDrawable(false));

            ImageView art = new ImageView(parent.getContext());
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            art.setTag("art");
            art.setClipToOutline(true);
            art.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(10));
                }
            });
            row.addView(art, LayoutHelper.createLinear(52, 52, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(parent.getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(parent.getContext());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTag("title");
            col.addView(title);

            TextView sub = new TextView(parent.getContext());
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            sub.setTag("sub");
            col.addView(sub);
            row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));

            VrgGlyph.View like = new VrgGlyph.View(parent.getContext()).set(VrgGlyph.Type.HEART, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 22);
            like.setTag("like");
            row.addView(like, LayoutHelper.createLinear(40, 44));

            TextView more = new TextView(parent.getContext());
            more.setText("⋮");
            more.setGravity(Gravity.CENTER);
            more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            more.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            more.setTag("more");
            row.addView(more, LayoutHelper.createLinear(34, 40));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LinearLayout row = (LinearLayout) holder.itemView;
            ImageView art = row.findViewWithTag("art");
            TextView title = row.findViewWithTag("title");
            TextView sub = row.findViewWithTag("sub");
            VrgGlyph.View like = row.findViewWithTag("like");
            TextView more = row.findViewWithTag("more");

            if (activeTab == TAB_PLAYLISTS && openPlaylistId == null) {
                VrgMusicTypes.Playlist pl = displayedPlaylists.get(position);
                art.setImageResource(R.drawable.msg_filled_data_music);
                title.setText(pl.name);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                sub.setText("Треков: " + pl.tracks.size() + "   ·   Офлайн: " + offlineCount(pl.tracks) + " / " + pl.tracks.size());
                like.setVisibility(View.GONE);
                more.setVisibility(View.VISIBLE);
                row.setOnClickListener(v -> {
                    openPlaylistId = pl.id;
                    refreshList();
                });
                more.setOnClickListener(v -> {
                    new AlertDialog.Builder(getParentActivity())
                        .setTitle(pl.name)
                        .setItems(new CharSequence[] { "Играть плейлист", "Скачать оффлайн", "Удалить плейлист" }, (d, w) -> {
                            if (w == 0 && !pl.tracks.isEmpty()) player.setQueue(pl.tracks, 0);
                            else if (w == 1) player.cacheTracks(pl.tracks);
                            else if (w == 2) player.deletePlaylist(pl.id);
                        })
                        .show();
                });
                return;
            }

            VrgMusicTypes.TrackInfo track = displayedTracks.get(position);
            VrgMusicTypes.TrackInfo cur = player.getCurrentTrack();
            boolean isCurrent = cur != null && TextUtils.equals(cur.id, track.id);
            title.setText(track.title);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setText(isCurrent ? "▶  " + track.title : track.title);

            String cachedTag = player.isTrackCached(track.id) ? " · ✓ оффлайн" : "";
            sub.setText((track.isYoutube() ? "[YT] " : "[SC] ") + track.artist + " · " + VrgMusicTypes.formatTime(track.durationMs) + cachedTag);

            like.setVisibility(View.VISIBLE);
            boolean liked = player.isInLibrary(track.id);
            like.setType(liked ? VrgGlyph.Type.HEART_FILLED : VrgGlyph.Type.HEART);
            like.setColor(liked ? 0xFFFF5C5C : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            like.setOnClickListener(v -> {
                player.toggleLibrary(track);
                notifyItemChanged(position);
            });

            more.setVisibility(View.VISIBLE);
            more.setOnClickListener(v -> showTrackMenu(track));

            Bitmap bmp = client.getCachedArtwork(track.artworkUrl);
            if (bmp != null) {
                art.setImageBitmap(bmp);
            } else {
                art.setImageResource(R.drawable.msg_filled_data_music);
                client.fetchArtwork(track.artworkUrl, loaded -> {
                    if (holder.getAdapterPosition() == position) {
                        art.setImageBitmap(loaded);
                    }
                });
            }

            row.setOnClickListener(v -> {
                player.setQueue(new ArrayList<>(displayedTracks), position);
            });
        }
    }
}
