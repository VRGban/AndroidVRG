package org.telegram.vrg;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

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

public class VrgAutoReplyActivity extends BaseFragment {
    private static final int ID_ENABLE = 1;
    private static final int ID_GREETING = 2;
    private static final int ID_GREETING_CD = 3;
    private static final int ID_COMMANDS = 4;
    private static final int ID_CMD_CD = 5;
    private static final int ID_IGNORED_BASE = 100;

    private UniversalRecyclerView listView;
    private final ArrayList<VrgAutoReply.IgnoredEntry> ignored = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Автоответчик");
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
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Автоответчик"));
        items.add(VrgInfoSwitchCell.Factory.of(
            ID_ENABLE,
            "Включить автоответчик",
            "Автоматически отвечает на входящие сообщения в личных чатах (кроме ботов и исключений).",
            VrgAutoReply.isEnabled(),
            () -> {
                VrgAutoReply.setEnabled(!VrgAutoReply.isEnabled());
                refresh();
            }
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Приветствие"));
        String greet = VrgAutoReply.getGreeting();
        items.add(UItem.asButton(ID_GREETING, "Текст приветствия", TextUtils.isEmpty(greet) ? "не задан" : trimPreview(greet)));
        items.add(UItem.asButton(ID_GREETING_CD, "Кулдаун приветствия", VrgAutoReply.getGreetingCooldownMin() + " мин"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Команды (формат: /команда -> ответ)"));
        String cmds = VrgAutoReply.getCommands();
        items.add(UItem.asButton(ID_COMMANDS, "Список команд", TextUtils.isEmpty(cmds) ? "не задан" : trimPreview(cmds)));
        items.add(UItem.asButton(ID_CMD_CD, "Кулдаун команд", VrgAutoReply.getCmdCooldownSec() + " сек"));

        ignored.clear();
        ignored.addAll(VrgAutoReply.listIgnored());
        if (!ignored.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader("Исключения (нажмите чтобы убрать)"));
            for (int i = 0; i < ignored.size(); i++) {
                items.add(UItem.asButton(ID_IGNORED_BASE + i, ignored.get(i).title, "убрать"));
            }
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_IGNORED_BASE) {
            int idx = item.id - ID_IGNORED_BASE;
            if (idx >= 0 && idx < ignored.size()) {
                VrgAutoReply.removeIgnored(ignored.get(idx).peerId);
                refresh();
            }
            return;
        }
        switch (item.id) {
            case ID_GREETING:
                showTextEdit("Текст приветствия", "Например: Привет! Сейчас занят, отвечу позже.", VrgAutoReply.getGreeting(), false, text -> {
                    VrgAutoReply.setGreeting(text);
                    refresh();
                });
                break;
            case ID_GREETING_CD:
                showTextEdit("Кулдаун приветствия (мин)", "60", String.valueOf(VrgAutoReply.getGreetingCooldownMin()), true, text -> {
                    try {
                        VrgAutoReply.setGreetingCooldownMin(Integer.parseInt(text.trim()));
                        refresh();
                    } catch (NumberFormatException ignore) {
                    }
                });
                break;
            case ID_COMMANDS:
                showTextEdit("Команды автоответчика", "/прайс -> 1000 руб\n/связь -> @casekind", VrgAutoReply.getCommands(), false, text -> {
                    VrgAutoReply.setCommands(text);
                    refresh();
                });
                break;
            case ID_CMD_CD:
                showTextEdit("Кулдаун команд (сек)", "5", String.valueOf(VrgAutoReply.getCmdCooldownSec()), true, text -> {
                    try {
                        VrgAutoReply.setCmdCooldownSec(Integer.parseInt(text.trim()));
                        refresh();
                    } catch (NumberFormatException ignore) {
                    }
                });
                break;
        }
    }

    private interface TextCallback {
        void onSave(String value);
    }

    private void showTextEdit(String title, String hint, String current, boolean numberOnly, TextCallback cb) {
        if (getParentActivity() == null) {
            return;
        }
        FrameLayout wrap = new FrameLayout(getParentActivity());
        EditText edit = new EditText(getParentActivity());
        edit.setHint(hint);
        edit.setText(current != null ? current : "");
        edit.setSelection(edit.getText().length());
        if (numberOnly) {
            edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else {
            edit.setMinLines(3);
            edit.setGravity(Gravity.TOP | Gravity.LEFT);
        }
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        wrap.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 24, 8, 24, 8));

        new AlertDialog.Builder(getParentActivity())
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Сохранить", (d, w) -> {
                cb.onSave(edit.getText() != null ? edit.getText().toString() : "");
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Сохранено").show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private static String trimPreview(String s) {
        String clean = s.replace('\n', ' ').trim();
        return clean.length() > 22 ? clean.substring(0, 22) + "…" : clean;
    }
}

