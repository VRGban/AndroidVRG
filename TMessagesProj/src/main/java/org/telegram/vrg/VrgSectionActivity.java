package org.telegram.vrg;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Map;

public class VrgSectionActivity extends BaseFragment {
    public static final int SECTION_GHOST = 1;
    public static final int SECTION_DELETED = 2;
    public static final int SECTION_LOOK = 3;
    public static final int SECTION_PLUS = 4;
    public static final int SECTION_MISC = 5;
    public static final int SECTION_SECURITY = 6;
    public static final int SECTION_VOICE = 7;
    public static final int SECTION_PLAYER = 8;
    public static final int SECTION_SIDEMENU = 9;

    private static final int ID_FULL_GHOST = 100;
    private static final int ID_GHOST_READ = 101;
    private static final int ID_GHOST_TYPING = 102;
    private static final int ID_GHOST_ONLINE = 103;
    private static final int ID_GHOST_OFFLINE_SEND = 104;
    private static final int ID_GHOST_STORIES = 105;
    private static final int ID_GHOST_VOICE_CONTENTS = 106;
    private static final int ID_GHOST_READ_ON_SEND = 107;
    private static final int ID_GHOST_SILENT_SEND = 108;
    private static final int ID_GHOST_OVERRIDE_BASE = 1000;

    private static final int ID_KEEP_DELETED = 201;
    private static final int ID_KEEP_TTL = 202;
    private static final int ID_KEEP_EDITED = 203;
    private static final int ID_BG_ANTI_DELETE = 204;
    private static final int ID_BATTERY_OPT = 205;
    private static final int ID_AUTOSTART = 206;
    private static final int ID_DELETED_MARK = 207;
    private static final int ID_DELETED_DIM = 208;
    private static final int ID_DELETED_LOG = 209;
    private static final int ID_DELETED_MARK_COLOR = 210;

    private static final int ID_APPLY_DARK_THEME = 301;
    private static final int ID_APPLY_LIGHT_THEME = 302;
    private static final int ID_OTHER_THEMES = 399;
    private static final int ID_DENSITY = 398;
    private static final int ID_ROUND_QUALITY = 397;
    private static final int ID_TIME_SHIFT = 395;
    private static final int ID_CLEAR_CACHE = 396;
    private static final int ID_HIDE_CONTACTS = 390;
    private static final int ID_TITLE_MODE = 391;
    private static final int ID_TITLE_CUSTOM = 392;
    private static final int ID_TITLE_CENTER = 393;
    private static final int ID_TITLE_BADGE = 394;
    private static final int ID_HIDE_SIMILAR_CHANNELS = 303;
    private static final int ID_BUBBLE_RADIUS = 304;
    private static final int ID_SHOW_WELCOME = 305;
    private static final int ID_HIDE_STORIES = 306;
    private static final int ID_HIDE_SERVICE_MESSAGES = 307;
    private static final int ID_HIDE_COMPOSE_GIFTS = 308;
    private static final int ID_HIDE_COMPOSE_SCHEDULED = 309;
    private static final int ID_HIDE_COMPOSE_SILENT = 310;
    private static final int ID_HIDE_COMPOSE_BOT = 311;
    private static final int ID_SHOW_SECONDS = 312;
    private static final int ID_PLAYER_MINIBAR = 318;
    private static final int ID_FONT = 319;
    private static final int ID_SELECT_UNLIMITED = 323;
    private static final int ID_GALLERY_CAMERA = 812;
    private static final int ID_PLAYER_RADIO = 322;
    private static final int ID_SEARCH_SOURCES = 810;
    private static final int ID_YT_LOGIN = 811;
    private static final int ID_ROUND_CAMERA = 321;
    private static final int ID_MASTER = 340;
    private static final int ID_ACC_PASSWORDS = 341;
    private static final int ID_HIDE_ALL_CHATS = 320;
    private static final int REQ_PICK_FONT = 9741;
    private static final int ID_MENU_EXTRA_BASE = 330;
    private static final int ID_MENU_VRG_PLAYER = 313;
    private static final int ID_MENU_VRG_SETTINGS = 314;
    private static final int ID_MENU_NEW_GROUP = 315;
    private static final int ID_MENU_SAVED = 316;
    private static final int ID_MENU_WALLET = 317;

    private static final int ID_BYPASS_NOFORWARDS = 401;
    private static final int ID_DISABLE_ADS = 402;
    private static final int ID_LOCAL_PREMIUM = 403;
    private static final int ID_AUTOREPLY = 404;
    private static final int ID_FAKE_PROFILE = 405;

    private static final int ID_SHOW_PEER_ID = 501;
    private static final int ID_CONFIRM_CALL = 502;
    private static final int ID_CONFIRM_STICKER = 503;
    private static final int ID_CONFIRM_GIF = 504;
    private static final int ID_BACKUP_EXPORT = 505;
    private static final int ID_BACKUP_IMPORT = 506;
    private static final int ID_TEMPLATES = 507;

    private static final int ID_VOICE_ENABLED = 701;
    private static final int ID_VOICE_ROBOT = 702;
    private static final int ID_VOICE_RESET = 703;
    private static final int ID_VOICE_PRESET_BASE = 720;

    private static final int ID_LOCK_ALL_NOW = 601;
    private static final int ID_LOCKED_CHAT_BASE = 2000;

    private final int section;
    private UniversalRecyclerView listView;
    private final ArrayList<Long> overridePeerIds = new ArrayList<>();
    private final ArrayList<VrgChatLock.LockedEntry> lockedChats = new ArrayList<>();

    public VrgSectionActivity(int section) {
        this.section = section;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getSectionTitle());
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

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        themeSync = VrgThemeSync.attach(this, () -> VrgThemeSync.rebuildList(listView));
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

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (listView == null || listView.adapter == null) {
            return;
        }
        final androidx.recyclerview.widget.RecyclerView.LayoutManager lm = listView.getLayoutManager();
        int pos = -1;
        int off = 0;
        if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager) {
            androidx.recyclerview.widget.LinearLayoutManager l = (androidx.recyclerview.widget.LinearLayoutManager) lm;
            pos = l.findFirstVisibleItemPosition();
            View v = pos >= 0 ? l.findViewByPosition(pos) : null;
            off = v != null ? v.getTop() - listView.getPaddingTop() : 0;
        }
        listView.adapter.update(true);
        if (pos >= 0) {
            final int fPos = pos;
            final int fOff = off;
            final androidx.recyclerview.widget.LinearLayoutManager l = (androidx.recyclerview.widget.LinearLayoutManager) lm;
            l.scrollToPositionWithOffset(fPos, fOff);
            listView.post(() -> l.scrollToPositionWithOffset(fPos, fOff));
        }
    }

    private String getSectionTitle() {
        switch (section) {
            case SECTION_GHOST:
                return "Призрак";
            case SECTION_DELETED:
                return "Удалённые сообщения";
            case SECTION_LOOK:
                return "Оформление";
            case SECTION_PLUS:
                return "VRG+";
            case SECTION_MISC:
                return "Удобство";
            case SECTION_SECURITY:
                return "Защита и пароли";
            case SECTION_VOICE:
                return "Изменение голоса";
            case SECTION_PLAYER:
                return "Плеер";
            case SECTION_SIDEMENU:
                return "Боковое меню";
            default:
                return "VRG";
        }
    }

    private UItem sw(int id, String title, String info, boolean checked, Runnable toggle) {
        return VrgInfoSwitchCell.Factory.of(id, title, info, checked, () -> {
            toggle.run();
            refresh();
        });
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switch (section) {
            case SECTION_GHOST:
                fillGhostItems(items);
                break;
            case SECTION_DELETED:
                fillDeletedItems(items);
                break;
            case SECTION_LOOK:
                fillLookItems(items);
                break;
            case SECTION_PLUS:
                fillPlusItems(items);
                break;
            case SECTION_MISC:
                fillMiscItems(items);
                break;
            case SECTION_SECURITY:
                fillSecurityItems(items);
                break;
            case SECTION_VOICE:
                fillVoiceItems(items);
                break;
            case SECTION_PLAYER:
                fillPlayerItems(items);
                break;
            case SECTION_SIDEMENU:
                fillSideMenuItems(items);
                break;
        }
    }

    private void fillVoiceItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Голосовые сообщения"));
        items.add(sw(
            ID_VOICE_ENABLED,
            "Включить изменение голоса (ГС)",
            "Эффект применяется к голосовым сообщениям при записи. Видеокружочки не затрагиваются.",
            VrgVoiceChanger.isEnabled(),
            () -> VrgVoiceChanger.setEnabled(!VrgVoiceChanger.isEnabled())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Пресет"));
        String current = VrgVoiceChanger.getPresetId();
        for (int i = 0; i < VrgVoiceChanger.PRESETS.length; i++) {
            VrgVoiceChanger.Preset pr = VrgVoiceChanger.PRESETS[i];
            items.add(UItem.asButton(ID_VOICE_PRESET_BASE + i, pr.title + (pr.id.equals(current) ? "  ✓" : ""), pr.description));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Тон (высота голоса)"));
        items.add(UItem.asIntSlideView(1, -12, VrgVoiceChanger.getPitch(), 12,
            v -> v + " пт",
            v -> { VrgVoiceChanger.setPitch(v); markVoiceCustom(); }));
        items.add(UItem.asHeader("Бас (низкие частоты)"));
        items.add(UItem.asIntSlideView(1, -6, VrgVoiceChanger.getBass(), 12,
            v -> v + " дБ",
            v -> { VrgVoiceChanger.setBass(v); markVoiceCustom(); }));
        items.add(UItem.asHeader("Верха (чёткость)"));
        items.add(UItem.asIntSlideView(1, -6, VrgVoiceChanger.getTreble(), 12,
            v -> v + " дБ",
            v -> { VrgVoiceChanger.setTreble(v); markVoiceCustom(); }));
        items.add(UItem.asHeader("Эхо"));
        items.add(UItem.asIntSlideView(1, 0, VrgVoiceChanger.getEcho(), 100,
            v -> v + "%",
            v -> { VrgVoiceChanger.setEcho(v); markVoiceCustom(); }));
        items.add(UItem.asShadow(null));

        items.add(sw(
            ID_VOICE_ROBOT,
            "Эффект робота / рации",
            null,
            VrgVoiceChanger.isRobot(),
            () -> { VrgVoiceChanger.setRobot(!VrgVoiceChanger.isRobot()); markVoiceCustom(); }
        ));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_VOICE_RESET, "Сбросить на «Мужской» по умолчанию"));
        items.add(UItem.asShadow(null));
    }

    private void markVoiceCustom() {
        if (!"custom".equals(VrgVoiceChanger.getPresetId())) {
            org.telegram.vrg.VrgConfig.prefs().edit().putString("voice_changer_preset", "custom").apply();
        }
    }

    private void fillGhostItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Режим"));
        items.add(sw(
            ID_FULL_GHOST,
            "Полный призрак",
            "Включает или выключает весь набор скрытности сразу: нечиталку, скрытие набора, скрытие онлайна, уход в офлайн после отправки и скрытый просмотр историй.",
            VrgConfig.isFullGhost(),
            () -> VrgConfig.setFullGhost(!VrgConfig.isFullGhost())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Скрытность"));
        items.add(sw(
            ID_GHOST_READ,
            "Не отмечать сообщения прочитанными",
            "VRG не сообщает Telegram, что вы прочитали сообщения: у собеседника останется одна галочка. Сообщение отметится прочитанным, только если включено чтение при ответе.",
            VrgConfig.isGhostRead(),
            () -> VrgConfig.setGhostRead(!VrgConfig.isGhostRead())
        ));
        items.add(sw(
            ID_GHOST_TYPING,
            "Не показывать «печатает…»",
            "Собеседники не увидят «печатает…», «записывает голосовое» и подобные статусы, пока вы пишете.",
            VrgConfig.isGhostTyping(),
            () -> VrgConfig.setGhostTyping(!VrgConfig.isGhostTyping())
        ));
        items.add(sw(
            ID_GHOST_ONLINE,
            "Не показывать, что я в сети",
            "VRG не сообщает, что вы в сети: в вашем профиле у других будет «был(а) недавно».",
            VrgConfig.isGhostOnline(),
            () -> VrgConfig.setGhostOnline(!VrgConfig.isGhostOnline())
        ));
        items.add(sw(
            ID_GHOST_OFFLINE_SEND,
            "Уходить в офлайн после отправки",
            "Сразу после отправки сообщения VRG переводит вас в офлайн.",
            VrgConfig.isGhostOfflineAfterSend(),
            () -> VrgConfig.setGhostOfflineAfterSend(!VrgConfig.isGhostOfflineAfterSend())
        ));
        items.add(sw(
            ID_GHOST_STORIES,
            "Смотреть истории незаметно",
            "Вы смотрите истории, но не попадаете в список просмотревших.",
            VrgConfig.isGhostStories(),
            () -> VrgConfig.setGhostStories(!VrgConfig.isGhostStories())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Дополнительно"));
        items.add(sw(
            ID_GHOST_VOICE_CONTENTS,
            "Не палить прослушивание ГС / кружочков",
            "Даже если основная нечиталка выключена, VRG не сообщает серверу, что вы прослушали голосовое или посмотрели кружочек.",
            VrgConfig.isGhostVoiceContents(),
            () -> VrgConfig.setGhostVoiceContents(!VrgConfig.isGhostVoiceContents())
        ));
        items.add(sw(
            ID_GHOST_READ_ON_SEND,
            "Читать чат при ручном ответе",
            "Если нечиталка включена, при отправке сообщения вручную чат автоматически отметится прочитанным на сервере.",
            VrgConfig.isGhostReadOnSend(),
            () -> VrgConfig.setGhostReadOnSend(!VrgConfig.isGhostReadOnSend())
        ));
        items.add(sw(
            ID_GHOST_SILENT_SEND,
            "Отправлять все сообщения без звука",
            "Все ваши исходящие сообщения будут уходить без уведомления у собеседника (silent = true).",
            VrgConfig.isGhostSilentSend(),
            () -> VrgConfig.setGhostSilentSend(!VrgConfig.isGhostSilentSend())
        ));

        overridePeerIds.clear();
        Map<String, ?> all = VrgConfig.prefs().getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("ghost_chat_") && entry.getValue() instanceof Integer) {
                int mode = (Integer) entry.getValue();
                if (mode == VrgGhost.OVERRIDE_FORCE_READ || mode == VrgGhost.OVERRIDE_FORCE_UNREAD) {
                    try {
                        overridePeerIds.add(Long.parseLong(entry.getKey().substring("ghost_chat_".length())));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }
        if (!overridePeerIds.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Исключения по чатам (нажмите чтобы убрать)"));
            for (int i = 0; i < overridePeerIds.size(); i++) {
                long peerId = overridePeerIds.get(i);
                int mode = VrgGhost.getReadOverride(peerId);
                String title = resolvePeerTitle(peerId);
                String val = mode == VrgGhost.OVERRIDE_FORCE_READ ? "Всегда читать" : "Никогда не читать";
                items.add(UItem.asButton(ID_GHOST_OVERRIDE_BASE + i, title, val));
            }
        }
    }

    private void fillDeletedItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Кэш"));
        items.add(UItem.asButton(ID_CLEAR_CACHE, "Очистить кэш"));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Режим шпиона"));
        items.add(sw(
            ID_KEEP_DELETED,
            "Сохранять удалённые сообщения",
            "Когда вы или собеседник удаляете сообщение, оно остаётся у вас в чате с пометкой «удалено». Сообщения сохраняются навсегда между перезапусками VRG.",
            VrgConfig.isKeepDeleted(),
            () -> VrgConfig.setKeepDeleted(!VrgConfig.isKeepDeleted())
        ));
        items.add(sw(
            ID_KEEP_TTL,
            "Сохранять одноразовые медиа",
            "Одноразовые фото, видео, кружочки и голосовые сообщения не сгорают после просмотра и остаются в чате.",
            VrgConfig.isKeepTtl(),
            () -> VrgConfig.setKeepTtl(!VrgConfig.isKeepTtl())
        ));
        items.add(sw(
            ID_KEEP_EDITED,
            "Сохранять историю правок",
            "Записывает старые версии отредактированных сообщений на диск, чтобы их можно было посмотреть через меню сообщения даже после перезапуска VRG.",
            VrgConfig.isKeepEdited(),
            () -> VrgConfig.setKeepEdited(!VrgConfig.isKeepEdited())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Работа в фоне"));
        items.add(sw(
            ID_BG_ANTI_DELETE,
            "Фоновое сохранение сообщений",
            "Держит соединение Telegram активным в фоне через тихое уведомление, чтобы удалённые сообщения сохранялись, пока приложение свёрнуто. Если телефон «усыпляет» приложение, отключите оптимизацию батареи ниже.",
            VrgConfig.isBackgroundAntiDelete(),
            () -> {
                VrgConfig.setBackgroundAntiDelete(!VrgConfig.isBackgroundAntiDelete());
                VrgBackgroundSync.syncServiceState();
            }
        ));
        boolean batteryExempt = getContext() != null && VrgBackgroundSync.isIgnoringBatteryOptimizations(getContext());
        items.add(VrgMarqueeButtonCell.Factory.of(
            ID_BATTERY_OPT,
            "Оптимизация батареи",
            batteryExempt ? "Отключена (ОК)" : "Разрешить фон",
            () -> {
                if (getContext() != null) {
                    VrgBackgroundSync.requestIgnoreBatteryOptimizations(getContext());
                }
            }
        ));
        items.add(VrgMarqueeButtonCell.Factory.of(
            ID_AUTOSTART,
            "Автозапуск и фон (Xiaomi / Huawei / Honor / Oppo / Vivo)",
            "Открыть",
            () -> {
                if (getContext() != null) {
                    VrgBackgroundSync.openVendorAutostartSettings(getContext());
                }
            }
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Вид"));
        items.add(UItem.asButton(ID_DELETED_MARK, "Пометка", formatDeletedMark(VrgConfig.getDeletedMark())));
        items.add(UItem.asButton(ID_DELETED_MARK_COLOR, "Цвет значка удалённых", formatDeletedMarkColor(VrgConfig.getDeletedMarkColor())));
        items.add(UItem.asButton(ID_DELETED_DIM, "Затемнение удалённых", formatDeletedDim(VrgConfig.getDeletedDim())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("История"));
        items.add(UItem.asButton(ID_DELETED_LOG, "Открыть журнал удалённых", "просмотр"));
    }

    private void fillPlayerItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Плеер"));
        items.add(sw(
            ID_PLAYER_MINIBAR,
            "Показывать мини-плеер в чатах",
            "Небольшая панель с названием трека и кнопками поверх списка чатов.",
            VrgConfig.prefs().getBoolean("player_minibar", true),
            () -> VrgConfig.prefs().edit().putBoolean("player_minibar", !VrgConfig.prefs().getBoolean("player_minibar", true)).apply()
        ));
        items.add(sw(
            ID_PLAYER_RADIO,
            "Радио (похожие треки)",
            "Когда плейлист закончился и есть интернет, плеер сам подбирает похожие треки и играет дальше.",
            VrgConfig.prefs().getBoolean("player_radio", true),
            () -> VrgConfig.prefs().edit().putBoolean("player_radio", !VrgConfig.prefs().getBoolean("player_radio", true)).apply()
        ));
        items.add(UItem.asButton(ID_SEARCH_SOURCES, "Источник музыки", sourcesLabel()));
        items.add(UItem.asButton(ID_YT_LOGIN, "Вход в YouTube", org.telegram.vrg.music.VrgYoutube.isLoggedIn() ? "выполнен" : "не выполнен"));
        items.add(UItem.asShadow("Нажми «Источник музыки», чтобы переключать: оба, SoundCloud или YouTube. «Вход в YouTube» нужен, если YouTube-трек не играет: войди в Google. Пароль VRG не видит, приложение берёт только куки сессии, они хранятся на телефоне."));

    }

    private void applyTitleChange() {
    }

    private String densityLabel() {
        int level = VrgConfig.prefs().getInt("ui_density", 1);
        return level == 0 ? "компактно" : (level == 2 ? "свободно" : "обычно");
    }

    private void applyDensity(int level) {
        int size = level == 0 ? 15 : (level == 2 ? 17 : 16);
        org.telegram.messenger.SharedConfig.fontSize = size;
        org.telegram.messenger.SharedConfig.fontSizeIsDefault = false;
        org.telegram.messenger.ApplicationLoader.applicationContext
            .getSharedPreferences("mainconfig", android.app.Activity.MODE_PRIVATE)
            .edit().putInt("fons_size", size).commit();
        org.telegram.ui.ActionBar.Theme.createCommonMessageResources();
        org.telegram.messenger.SharedConfig.setUseThreeLinesLayout(level == 2);
    }

    private void fillLookItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Тема"));
        items.add(UItem.asButton(ID_APPLY_DARK_THEME, "Чёрно-белая тема VRG", "применить"));
        items.add(UItem.asButton(ID_APPLY_LIGHT_THEME, "Светлая тема", "применить"));
        items.add(UItem.asButton(ID_OTHER_THEMES, "Другие темы", "свои и стандартные"));
        items.add(UItem.asShadow(null));

        items.add(sw(
            ID_HIDE_ALL_CHATS,
            "Скрыть папку «Все чаты»",
            "Убирает вкладку «Все чаты» над списком, если у вас есть другие папки. Перезапустите приложение.",
            VrgConfig.prefs().getBoolean("hide_all_chats", false),
            () -> VrgConfig.prefs().edit().putBoolean("hide_all_chats", !VrgConfig.prefs().getBoolean("hide_all_chats", false)).apply()
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Кружочки"));
        items.add(sw(
            ID_ROUND_CAMERA,
            "Запоминать камеру кружочка",
            "Если переключились на заднюю камеру, следующий кружочек сразу откроется с неё.",
            VrgConfig.prefs().getBoolean("round_remember_camera", true),
            () -> VrgConfig.prefs().edit().putBoolean("round_remember_camera", !VrgConfig.prefs().getBoolean("round_remember_camera", true)).apply()
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Шрифт"));
        items.add(UItem.asButton(ID_FONT, "Шрифт приложения", VrgFont.modeTitle()));
        items.add(UItem.asShadow("Inter по умолчанию. Шрифт применяется после перезапуска приложения."));

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Чаты и каналы"));
        items.add(sw(
            ID_GALLERY_CAMERA,
            "Камера в галерее вложений",
            "Показывает плитку камеры и запрос доступа в окне выбора фото и файлов. Выключено: в галерее только твои фото и файлы.",
            VrgConfig.isGalleryCamera(),
            () -> VrgConfig.setGalleryCamera(!VrgConfig.isGalleryCamera())
        ));
        items.add(sw(
            ID_SELECT_UNLIMITED,
            "Без лимита при выделении сообщений",
            "Позволяет выделять, копировать, пересылать и удалять больше 100 сообщений за раз.",
            VrgConfig.prefs().getBoolean("select_unlimited", true),
            () -> VrgConfig.prefs().edit().putBoolean("select_unlimited", !VrgConfig.prefs().getBoolean("select_unlimited", true)).apply()
        ));
        items.add(sw(
            ID_HIDE_STORIES,
            "Скрыть истории",
            "Убирает панель историй (Stories) вверху списка чатов и кольца историй вокруг аватаров.",
            VrgConfig.isHideStories(),
            () -> {
                VrgConfig.setHideStories(!VrgConfig.isHideStories());
                org.telegram.messenger.NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(org.telegram.messenger.NotificationCenter.storiesUpdated);
            }
        ));
        items.add(sw(
            ID_HIDE_SIMILAR_CHANNELS,
            "Скрывать похожие каналы",
            "Убирает блок рекомендаций похожих каналов под постами и при подписке.",
            VrgConfig.isHideSimilarChannels(),
            () -> VrgConfig.setHideSimilarChannels(!VrgConfig.isHideSimilarChannels())
        ));
        items.add(sw(
            ID_HIDE_SERVICE_MESSAGES,
            "Скрывать системные сообщения в группах",
            "Скрывает в группах сообщения о входе/выходе участников, смене фото, названия и закрепах.",
            VrgConfig.isHideServiceMessages(),
            () -> VrgConfig.setHideServiceMessages(!VrgConfig.isHideServiceMessages())
        ));
        items.add(sw(
            ID_SHOW_SECONDS,
            "Секунды у времени сообщений",
            "Показывает время сообщений с секундами (14:50:08).",
            VrgConfig.isShowSeconds(),
            () -> VrgConfig.setShowSeconds(!VrgConfig.isShowSeconds())
        ));
        items.add(UItem.asButton(ID_DENSITY, "Плотность интерфейса", densityLabel()));
        items.add(sw(
            ID_HIDE_CONTACTS,
            "Скрыть вкладку «Контакты»",
            "Убирает кнопку «Контакты» из нижней панели. Перезапустите приложение.",
            VrgConfig.prefs().getBoolean("hide_contacts_tab", false),
            () -> VrgConfig.prefs().edit().putBoolean("hide_contacts_tab", !VrgConfig.prefs().getBoolean("hide_contacts_tab", false)).apply()
        ));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Название в шапке"));
        items.add(UItem.asButton(ID_TITLE_MODE, "Что показывать вместо «Telegram»", VrgTitle.modeLabel()));
        if ("custom".equals(VrgTitle.mode())) {
            items.add(UItem.asButton(ID_TITLE_CUSTOM, "Свой текст", VrgConfig.prefs().getString("title_custom_text", "VRG")));
        }
        items.add(sw(
            ID_TITLE_CENTER,
            "Название по центру",
            "Ставит название в шапке по центру (работает, когда выбрано не «логотип»).",
            VrgConfig.prefs().getBoolean("title_center", false),
            () -> { VrgConfig.prefs().edit().putBoolean("title_center", !VrgConfig.prefs().getBoolean("title_center", false)).apply(); applyTitleChange(); }
        ));
        items.add(sw(
            ID_TITLE_BADGE,
            "Показывать мой значок VRG",
            "Рисует ваш значок VRG рядом с названием в шапке.",
            VrgConfig.prefs().getBoolean("title_badge", false),
            () -> { VrgConfig.prefs().edit().putBoolean("title_badge", !VrgConfig.prefs().getBoolean("title_badge", false)).apply(); applyTitleChange(); }
        ));
        items.add(UItem.asButton(ID_BUBBLE_RADIUS, "Скругление пузырей сообщений", org.telegram.messenger.SharedConfig.bubbleRadius + " dp"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Поле ввода сообщений"));
        items.add(sw(
            ID_HIDE_COMPOSE_GIFTS,
            "Скрыть кнопку подарков",
            "Убирает кнопку отправки подарка из поля ввода.",
            VrgConfig.isHideComposeGifts(),
            () -> VrgConfig.setHideComposeGifts(!VrgConfig.isHideComposeGifts())
        ));
        items.add(sw(
            ID_HIDE_COMPOSE_SCHEDULED,
            "Скрыть кнопку отложенных сообщений",
            "Убирает кнопку отложенных сообщений из поля ввода.",
            VrgConfig.isHideComposeScheduled(),
            () -> VrgConfig.setHideComposeScheduled(!VrgConfig.isHideComposeScheduled())
        ));
        items.add(sw(
            ID_HIDE_COMPOSE_SILENT,
            "Скрыть кнопку беззвучной отправки",
            "Убирает кнопку беззвучной отправки в каналах.",
            VrgConfig.isHideComposeSilent(),
            () -> VrgConfig.setHideComposeSilent(!VrgConfig.isHideComposeSilent())
        ));
        items.add(sw(
            ID_HIDE_COMPOSE_BOT,
            "Скрыть кнопку команд бота (/)",
            "Убирает кнопку команд и меню бота из поля ввода.",
            VrgConfig.isHideComposeBot(),
            () -> VrgConfig.setHideComposeBot(!VrgConfig.isHideComposeBot())
        ));
        items.add(UItem.asShadow(null));


        items.add(UItem.asHeader("Приветствие"));
        items.add(UItem.asButton(ID_SHOW_WELCOME, "Экран «Добро пожаловать в VRG»", "показать"));
    }

    private void fillSideMenuItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Главное меню (⋮)"));
        items.add(sw(
            ID_MENU_VRG_PLAYER,
            "Показывать «VRG Music»",
            "Быстрый вход в VRG Music из главного меню ⋮.",
            VrgConfig.isMenuShowVrgPlayer(),
            () -> VrgConfig.setMenuShowVrgPlayer(!VrgConfig.isMenuShowVrgPlayer())
        ));
        items.add(sw(
            ID_MENU_VRG_SETTINGS,
            "Показывать «Настройки VRG»",
            "Быстрый вход в настройки VRG прямо из главного меню ⋮.",
            VrgConfig.isMenuShowVrgSettings(),
            () -> VrgConfig.setMenuShowVrgSettings(!VrgConfig.isMenuShowVrgSettings())
        ));
        items.add(sw(
            ID_MENU_NEW_GROUP,
            "Показывать «Новая группа»",
            "Показывает или скрывает пункт создания новой группы в меню ⋮.",
            VrgConfig.isMenuShowNewGroup(),
            () -> VrgConfig.setMenuShowNewGroup(!VrgConfig.isMenuShowNewGroup())
        ));
        items.add(sw(
            ID_MENU_SAVED,
            "Показывать «Избранное»",
            "Показывает или скрывает пункт «Избранное» в меню ⋮.",
            VrgConfig.isMenuShowSaved(),
            () -> VrgConfig.setMenuShowSaved(!VrgConfig.isMenuShowSaved())
        ));
        items.add(sw(
            ID_MENU_WALLET,
            "Показывать «Кошелёк» и мини-ботов",
            "Показывает или скрывает пункты кошелька и закреплённых мини-приложений в меню ⋮.",
            VrgConfig.isMenuShowWallet(),
            () -> VrgConfig.setMenuShowWallet(!VrgConfig.isMenuShowWallet())
        ));
        String[][] extra = {
            {"profile", "Мой профиль", "true"},
            {"fake_profile", "Мой фейк-профиль", "true"},
            {"channel", "Создать канал", "true"},
            {"contacts", "Контакты", "false"},
            {"calls", "Звонки", "false"},
            {"qrscan", "Сканировать QR", "false"},
            {"ghost", "Режим призрака", "true"},
            {"readall", "Прочитать все", "true"},
            {"addaccount", "Добавить аккаунт", "true"},
            {"night", "Ночной режим", "false"},
        };
        for (int i = 0; i < extra.length; i++) {
            final String key = extra[i][0];
            final boolean def = Boolean.parseBoolean(extra[i][2]);
            items.add(sw(
                ID_MENU_EXTRA_BASE + i,
                "Показывать «" + extra[i][1] + "»",
                null,
                VrgConfig.menuItem(key, def),
                () -> VrgConfig.setMenuItem(key, !VrgConfig.menuItem(key, def))
            ));
        }
        items.add(UItem.asShadow(null));
    }

    private void fillPlusItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Функции VRG+"));
        items.add(UItem.asButton(ID_AUTOREPLY, R.drawable.msg_message, "Автоответчик", VrgAutoReply.isEnabled() ? "вкл" : "выкл"));
        items.add(UItem.asButton(ID_FAKE_PROFILE, R.drawable.msg_edit, "Фейк-профиль", VrgFakeProfile.isEnabled() ? "активен" : "выкл"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Время"));
        items.add(UItem.asButton(ID_TIME_SHIFT, "Время в клиенте", VrgTime.label(VrgTime.shiftMinutes())));
        items.add(UItem.asShadow("Сдвигает время на вашем экране: у сообщений, в списке чатов, в датах. Другие люди видят настоящее время."));

        items.add(UItem.asHeader("Другое"));
        items.add(sw(
            ID_BYPASS_NOFORWARDS,
            "Разрешить копирование и пересылку",
            "Снимает запрет на копирование текста, пересылку и скачивание медиа в каналах и закрытых чатах.",
            VrgConfig.isBypassNoforwards(),
            () -> VrgConfig.setBypassNoforwards(!VrgConfig.isBypassNoforwards())
        ));
        items.add(sw(
            ID_DISABLE_ADS,
            "Отключить рекламу",
            "Скрывает спонсорские сообщения в каналах и ботах. Применяется к новым открытым чатам.",
            VrgConfig.isDisableAds(),
            () -> VrgConfig.setDisableAds(!VrgConfig.isDisableAds())
        ));
        items.add(sw(
            ID_LOCAL_PREMIUM,
            "Локальный Telegram Premium",
            "Активирует значок Telegram Premium на вашем экране и локальные клиентские возможности интерфейса.",
            VrgConfig.isLocalPremium(),
            () -> VrgConfig.setLocalPremium(!VrgConfig.isLocalPremium())
        ));
    }

    private void fillMiscItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Кружки"));
        items.add(UItem.asButton(ID_ROUND_QUALITY, "Качество кружков", VrgRound.label(VrgRound.level())));
        items.add(UItem.asShadow("Чем выше качество, тем чётче видео, но больше файл, нагрев и расход батареи. Действует на новые кружки."));
        items.add(UItem.asHeader("Профиль"));
        items.add(sw(
            ID_SHOW_PEER_ID,
            "Показывать ID и DC в профилях",
            "Добавляет числовой ID, дата-центр и примерную дату регистрации в информацию о пользователе, группе и канале.",
            VrgConfig.isShowPeerId(),
            () -> VrgConfig.setShowPeerId(!VrgConfig.isShowPeerId())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Подтверждения"));
        items.add(sw(
            ID_CONFIRM_CALL,
            "Подтверждать звонок",
            "Спрашивает подтверждение перед началом аудио- или видеозвонка, чтобы не позвонить случайно.",
            VrgConfig.isConfirmCall(),
            () -> VrgConfig.setConfirmCall(!VrgConfig.isConfirmCall())
        ));
        items.add(sw(
            ID_CONFIRM_STICKER,
            "Подтверждать отправку стикера",
            "Показывает окно подтверждения перед отправкой стикера.",
            VrgConfig.isConfirmSticker(),
            () -> VrgConfig.setConfirmSticker(!VrgConfig.isConfirmSticker())
        ));
        items.add(sw(
            ID_CONFIRM_GIF,
            "Подтверждать отправку GIF",
            "Показывает окно подтверждения перед отправкой GIF.",
            VrgConfig.isConfirmGif(),
            () -> VrgConfig.setConfirmGif(!VrgConfig.isConfirmGif())
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Шаблоны и бэкап"));
        items.add(UItem.asButton(ID_TEMPLATES, R.drawable.msg_edit, "Быстрые шаблоны", String.valueOf(VrgTemplates.load().size())));
        items.add(UItem.asButton(ID_BACKUP_EXPORT, R.drawable.msg_shareout, "Экспорт настроек VRG"));
        items.add(UItem.asButton(ID_BACKUP_IMPORT, R.drawable.msg_download, "Импорт настроек VRG"));
    }

    private void fillSecurityItems(ArrayList<UItem> items) {
        items.add(UItem.asHeader("Мастер-пароль"));
        items.add(UItem.asButton(ID_MASTER, "Мастер-пароль VRG", VrgSecurity.hasMaster() ? "установлен" : "не задан"));
        items.add(UItem.asButton(ID_ACC_PASSWORDS, "Пароли на смену аккаунтов"));
        items.add(UItem.asShadow("Если забыли пароль, в окне ввода нажмите «Забыли пароль?» и подтвердите пароль или PIN телефона."));
        items.add(UItem.asHeader("Запароленные чаты"));
        items.add(UItem.asButton(ID_LOCK_ALL_NOW, R.drawable.msg_secret, "Заблокировать все чаты сейчас"));
        lockedChats.clear();
        lockedChats.addAll(VrgChatLock.listLocked());
        if (lockedChats.isEmpty()) {
            items.add(UItem.asShadow("Запароленных чатов нет. Поставить пароль можно в чате: ⋮ → VRG → Поставить пароль на чат."));
        } else {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Нажмите на чат, чтобы снять пароль"));
            for (int i = 0; i < lockedChats.size(); i++) {
                VrgChatLock.LockedEntry e = lockedChats.get(i);
                items.add(UItem.asButton(ID_LOCKED_CHAT_BASE + i, e.title, "Снять"));
            }
        }
    }

    private void requireMasterOrRun(Runnable run) {
        if (!VrgSecurity.hasMaster()) {
            run.run();
            return;
        }
        VrgSecurity.promptPassword(getParentActivity(), "Мастер-пароль VRG", "Введите мастер-пароль",
            VrgSecurity::verifyMaster,
            pw -> run.run(),
            () -> VrgSecurity.resetWithDevicePassword(getParentActivity(), () -> {
                VrgSecurity.setMaster("");
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Мастер-пароль снят").show();
                refresh();
            }));
    }

    private void showAccountPasswords() {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<Long> ids = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            org.telegram.messenger.UserConfig cfg = org.telegram.messenger.UserConfig.getInstance(a);
            if (cfg.isClientActivated()) {
                long uid = cfg.getClientUserId();
                TLRPC.User u = cfg.getCurrentUser();
                String name = u != null ? org.telegram.messenger.UserObject.getUserName(u) : String.valueOf(uid);
                ids.add(uid);
                names.add(name + (VrgSecurity.hasAccountLock(uid) ? "  🔒" : ""));
            }
        }
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
            .setTitle("Пароли аккаунтов")
            .setItems(names.toArray(new CharSequence[0]), (d, which) -> {
                final long uid = ids.get(which);
                final String name = names.get(which).replace("  🔒", "");
                Runnable askNew = () -> VrgSecurity.promptPassword(getParentActivity(), "Пароль для " + name, "Новый пароль (пусто = снять)", null,
                    pw -> {
                        VrgSecurity.setAccountLock(uid, pw);
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, pw.isEmpty() ? "Пароль снят с аккаунта" : "Пароль установлен на аккаунт").show();
                    }, null);
                if (VrgSecurity.hasAccountLock(uid) && !VrgSecurity.hasMaster()) {
                    VrgSecurity.promptPassword(getParentActivity(), "Текущий пароль для " + name, "Введите текущий пароль",
                        pw -> VrgSecurity.verifyAccount(uid, pw),
                        pw -> askNew.run(),
                        () -> VrgSecurity.resetWithDevicePassword(getParentActivity(), () -> {
                            VrgSecurity.clearAccountLock(uid);
                            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Пароль снят с аккаунта").show();
                        }));
                } else {
                    askNew.run();
                }
            })
            .setNegativeButton("Закрыть", null)
            .show();
    }

    private void showFontDialog() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] opts = new CharSequence[]{"Inter (по умолчанию)", "Системный", "Выбрать файл шрифта…"};
        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
            .setTitle("Шрифт приложения")
            .setItems(opts, (d, which) -> {
                if (which == 0) {
                    VrgFont.setMode(VrgFont.MODE_INTER);
                    fontChanged();
                } else if (which == 1) {
                    VrgFont.setMode(VrgFont.MODE_SYSTEM);
                    fontChanged();
                } else {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    startActivityForResult(android.content.Intent.createChooser(intent, "Файл шрифта (.ttf / .otf)"), REQ_PICK_FONT);
                }
            })
            .show();
    }

    private void fontChanged() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Перезапустите приложение, чтобы шрифт применился").show();
        refresh();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FONT || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null || getParentActivity() == null) {
            return;
        }
        try {
            java.io.File dir = new java.io.File(org.telegram.messenger.ApplicationLoader.getFilesDirFixed(), "vrg_fonts");
            dir.mkdirs();
            String name = "custom_" + System.currentTimeMillis() + ".ttf";
            java.io.File out = new java.io.File(dir, name);
            try (java.io.InputStream in = getParentActivity().getContentResolver().openInputStream(data.getData());
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            }
            android.graphics.Typeface.createFromFile(out);
            VrgFont.setCustomPath(out.getAbsolutePath());
            fontChanged();
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Не удалось загрузить шрифт").show();
        }
    }

    private static String sourcesLabel() {
        String m = VrgConfig.prefs().getString("search_sources", "all");
        return "sc".equals(m) ? "SoundCloud" : ("yt".equals(m) ? "YouTube" : "оба");
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_LOCKED_CHAT_BASE) {
            int idx = item.id - ID_LOCKED_CHAT_BASE;
            if (idx >= 0 && idx < lockedChats.size()) {
                VrgChatLock.LockedEntry e = lockedChats.get(idx);
                VrgChatLock.promptUnlock(getParentActivity(), e.dialogId, () -> {
                    VrgChatLock.clearPassword(e.dialogId);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Пароль снят: " + e.title).show();
                    refresh();
                });
            }
            return;
        }
        if (item.id >= ID_GHOST_OVERRIDE_BASE) {
            int idx = item.id - ID_GHOST_OVERRIDE_BASE;
            if (idx >= 0 && idx < overridePeerIds.size()) {
                VrgGhost.setReadOverride(overridePeerIds.get(idx), VrgGhost.OVERRIDE_DEFAULT);
                refresh();
            }
            return;
        }
        if (item.id >= ID_VOICE_PRESET_BASE && item.id < ID_VOICE_PRESET_BASE + VrgVoiceChanger.PRESETS.length) {
            VrgVoiceChanger.applyPreset(VrgVoiceChanger.PRESETS[item.id - ID_VOICE_PRESET_BASE].id);
            refresh();
            return;
        }
        if (item.id == ID_VOICE_RESET) {
            VrgVoiceChanger.applyPreset("male");
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Пресет сброшен на «Мужской»").show();
            refresh();
            return;
        }
        switch (item.id) {
            case ID_BATTERY_OPT:
                if (getContext() != null) {
                    VrgBackgroundSync.requestIgnoreBatteryOptimizations(getContext());
                }
                return;
            case ID_AUTOSTART:
                if (getContext() != null) {
                    VrgBackgroundSync.openVendorAutostartSettings(getContext());
                }
                return;
            case ID_DELETED_MARK: {
                String current = VrgConfig.getDeletedMark();
                if ("icon".equals(current)) {
                    VrgConfig.setDeletedMark("text");
                } else if ("text".equals(current)) {
                    VrgConfig.setDeletedMark("none");
                } else {
                    VrgConfig.setDeletedMark("icon");
                }
                refresh();
                return;
            }
            case ID_DELETED_MARK_COLOR: {
                final String[] keys = new String[] { "default", "#ff5c5c", "#ff9f43", "#4dabf7", "#b197fc", "#51cf66" };
                final CharSequence[] labels = new CharSequence[] { "Стандартный", "Красный", "Оранжевый", "Синий", "Фиолетовый", "Зелёный" };
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setTitle("Цвет значка удалённых")
                    .setItems(labels, (d, which) -> {
                        if (which >= 0 && which < keys.length) {
                            VrgConfig.setDeletedMarkColor(keys[which]);
                            org.telegram.messenger.NotificationCenter.getInstance(currentAccount)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                            refresh();
                        }
                    })
                    .show();
                return;
            }
            case ID_DELETED_DIM:
                VrgConfig.setDeletedDim((VrgConfig.getDeletedDim() + 1) % 3);
                refresh();
                return;
            case ID_DELETED_LOG:
                presentFragment(new VrgDeletedListActivity());
                return;
            case ID_BACKUP_EXPORT:
                VrgBackup.exportSettings(this);
                return;
            case ID_BACKUP_IMPORT:
                if (VrgBackup.importSettings(this)) {
                    refresh();
                }
                return;
            case ID_LOCK_ALL_NOW:
                VrgChatLock.lockAll();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Все запароленные чаты заблокированы").show();
                return;
            case ID_TIME_SHIFT: {
                final int count = 49;
                final int[] values = new int[count];
                final CharSequence[] labels = new CharSequence[count];
                for (int i = 0; i < count; i++) {
                    values[i] = -720 + i * 30;
                    labels[i] = VrgTime.label(values[i]);
                }
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setTitle("Время в клиенте")
                    .setItems(labels, (d, which) -> {
                        VrgTime.setShiftMinutes(values[which]);
                        org.telegram.messenger.LocaleController.getInstance().recreateFormatters();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Сдвиг: " + VrgTime.label(values[which]) + ". Полностью применится после перезапуска").show();
                        refresh();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
                return;
            }
            case ID_CLEAR_CACHE: {
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setTitle("Очистить кэш")
                    .setMessage("Вы уверены, что хотите очистить кэш?")
                    .setPositiveButton("Очистить", (d, w) -> VrgCache.clear(freed -> {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Кэш очищен: " + org.telegram.messenger.AndroidUtilities.formatFileSize(freed)).show();
                    }))
                    .setNegativeButton("Отмена", null)
                    .show();
                return;
            }
            case ID_ROUND_QUALITY: {
                final String[] opts = {"Обычное", "Высокое (480, 2 Мбит/с)", "Максимальное (640, 3,5 Мбит/с)"};
                showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(
                    getParentActivity(), opts, "Качество кружков",
                    VrgRound.level(),
                    (d, which) -> {
                        VrgConfig.prefs().edit().putInt("round_quality", which).apply();
                        refresh();
                    }));
                return;
            }
            case ID_DENSITY: {
                final String[] opts = {"Компактно", "Обычно", "Свободно"};
                showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(
                    getParentActivity(), opts, "Плотность интерфейса",
                    VrgConfig.prefs().getInt("ui_density", 1),
                    (d, which) -> {
                        VrgConfig.prefs().edit().putInt("ui_density", which).apply();
                        applyDensity(which);
                        refresh();
                    }));
                return;
            }
            case ID_TITLE_MODE: {
                final String[] keys = {"logo", "vrg", "username", "name", "custom"};
                final String[] opts = {"Логотип Telegram", "VRG", "Мой юзернейм", "Моё имя", "Свой текст"};
                int selected = 0;
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i].equals(VrgTitle.mode())) {
                        selected = i;
                    }
                }
                showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(
                    getParentActivity(), opts, "Что показывать в шапке",
                    selected,
                    (d, which) -> {
                        VrgConfig.prefs().edit().putString("title_mode", keys[which]).apply();
                        applyTitleChange();
                        refresh();
                        if ("custom".equals(keys[which])) {
                            onClick(UItem.asButton(ID_TITLE_CUSTOM, "", ""), null, 0, 0, 0);
                        }
                    }));
                return;
            }
            case ID_TITLE_CUSTOM: {
                final android.widget.EditText input = new android.widget.EditText(getParentActivity());
                input.setText(VrgConfig.prefs().getString("title_custom_text", "VRG"));
                input.setSingleLine(true);
                input.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setTitle("Название в шапке")
                    .setView(input)
                    .setPositiveButton("Сохранить", (d, w) -> {
                        VrgConfig.prefs().edit().putString("title_custom_text", input.getText().toString().trim()).apply();
                        applyTitleChange();
                        refresh();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
                return;
            }
            case ID_BUBBLE_RADIUS: {
                final int[] radii = {2, 6, 12, 17};
                final String[] opts = {"2 dp (почти прямые)", "6 dp", "12 dp", "17 dp (по умолчанию)"};
                int selected = 3;
                for (int i = 0; i < radii.length; i++) {
                    if (radii[i] == org.telegram.messenger.SharedConfig.bubbleRadius) {
                        selected = i;
                    }
                }
                showDialog(org.telegram.ui.Components.AlertsCreator.createSingleChoiceDialog(
                    getParentActivity(), opts, "Скругление пузырей",
                    selected,
                    (d, which) -> {
                        org.telegram.messenger.SharedConfig.bubbleRadius = radii[which];
                        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", radii[which]).apply();
                        refresh();
                    }));
                return;
            }
            case ID_SEARCH_SOURCES: {
                String cur = VrgConfig.prefs().getString("search_sources", "all");
                String next = "all".equals(cur) ? "sc" : ("sc".equals(cur) ? "yt" : "all");
                VrgConfig.prefs().edit().putString("search_sources", next).apply();
                refresh();
                return;
            }
            case ID_YT_LOGIN:
                if (org.telegram.vrg.music.VrgYoutube.isLoggedIn()) {
                    new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("Выйти из YouTube?")
                        .setMessage("Куки сессии будут удалены с этого телефона.")
                        .setPositiveButton("Выйти", (d, w) -> {
                            org.telegram.vrg.music.VrgYoutube.logout();
                            refresh();
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
                } else {
                    presentFragment(new VrgYoutubeLoginActivity());
                }
                return;
            case ID_APPLY_DARK_THEME:
                if (VrgThemeHelper.applyVrgDarkTheme()) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Чёрно-белая тема VRG применена").show();
                }
                return;
            case ID_OTHER_THEMES:
                presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC));
                return;
            case ID_APPLY_LIGHT_THEME:
                if (VrgThemeHelper.applyLightTheme()) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Светлая тема применена").show();
                }
                return;
            case ID_SHOW_WELCOME:
                VrgWelcomeDialog.show(getContext());
                return;
            case ID_FONT:
                showFontDialog();
                return;
            case ID_MASTER:
                requireMasterOrRun(() -> VrgSecurity.promptPassword(getParentActivity(),
                    VrgSecurity.hasMaster() ? "Новый мастер-пароль" : "Мастер-пароль VRG",
                    VrgSecurity.hasMaster() ? "Новый пароль (пусто = снять)" : "Введите пароль",
                    null,
                    pw -> {
                        VrgSecurity.setMaster(pw);
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, pw.isEmpty() ? "Мастер-пароль снят" : "Мастер-пароль сохранён").show();
                        refresh();
                    }, null));
                return;
            case ID_ACC_PASSWORDS:
                requireMasterOrRun(this::showAccountPasswords);
                return;
            case ID_AUTOREPLY:
                presentFragment(new VrgAutoReplyActivity());
                return;
            case ID_FAKE_PROFILE:
                presentFragment(new VrgFakeProfileActivity());
                return;
            case ID_TEMPLATES:
                VrgTemplates.showManageDialog(this);
                return;
        }
    }

    private String resolvePeerTitle(long peerId) {
        if (peerId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
            if (user != null) {
                return (user.first_name != null ? user.first_name : "") + (user.last_name != null ? " " + user.last_name : "");
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
            if (chat != null && chat.title != null) {
                return chat.title;
            }
        }
        return String.valueOf(peerId);
    }

    private static String formatDeletedMark(String mark) {
        if ("text".equals(mark)) {
            return "слово «удалено»";
        }
        if ("none".equals(mark)) {
            return "без пометки";
        }
        return "значок корзины";
    }

    private static String formatDeletedMarkColor(String hex) {
        if ("#ff5c5c".equalsIgnoreCase(hex)) return "Красный";
        if ("#ff9f43".equalsIgnoreCase(hex)) return "Оранжевый";
        if ("#4dabf7".equalsIgnoreCase(hex)) return "Синий";
        if ("#b197fc".equalsIgnoreCase(hex)) return "Фиолетовый";
        if ("#51cf66".equalsIgnoreCase(hex)) return "Зелёный";
        return "Стандартный";
    }

    private static String formatDeletedDim(int dim) {
        if (dim == 1) {
            return "25%";
        }
        if (dim >= 2) {
            return "50%";
        }
        return "выкл";
    }
}
