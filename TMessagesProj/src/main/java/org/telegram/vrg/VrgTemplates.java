package org.telegram.vrg;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public final class VrgTemplates {
    private static final String KEY = "vrg_templates_json";

    public static final class Entry {
        public final String title;
        public final String text;

        public Entry(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private VrgTemplates() {
    }

    public static ArrayList<Entry> load() {
        ArrayList<Entry> out = new ArrayList<>();
        String raw = VrgConfig.prefs().getString(KEY, "");
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String text = o.optString("text", "");
                String title = o.optString("title", "").trim();
                if (!TextUtils.isEmpty(text)) {
                    if (title.isEmpty()) {
                        title = text.length() > 24 ? text.substring(0, 24) : text;
                    }
                    out.add(new Entry(title, text));
                }
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    public static void save(ArrayList<Entry> items) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < items.size(); i++) {
            try {
                JSONObject o = new JSONObject();
                o.put("title", items.get(i).title);
                o.put("text", items.get(i).text);
                arr.put(o);
            } catch (Throwable ignore) {
            }
        }
        VrgConfig.prefs().edit().putString(KEY, arr.toString()).apply();
    }

    public static void showPickerInChat(ChatActivity chatActivity) {
        if (chatActivity == null || chatActivity.getParentActivity() == null) {
            return;
        }
        ArrayList<Entry> items = load();
        if (items.isEmpty()) {
            showAddDialog(chatActivity.getParentActivity(), chatActivity, () -> showPickerInChat(chatActivity));
            return;
        }
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(i);
            String preview = e.text.replace('\n', ' ');
            if (preview.length() > 36) {
                preview = preview.substring(0, 36) + "…";
            }
            labels[i] = e.title + " — " + preview;
        }
        new AlertDialog.Builder(chatActivity.getParentActivity())
            .setTitle("Быстрые шаблоны")
            .setItems(labels, (d, which) -> {
                if (which >= 0 && which < items.size()) {
                    chatActivity.vrgInsertTemplateText(items.get(which).text);
                }
            })
            .setNeutralButton("Добавить", (d, w) -> showAddDialog(chatActivity.getParentActivity(), chatActivity, null))
            .setNegativeButton("Закрыть", null)
            .show();
    }

    public static void showManageDialog(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        ArrayList<Entry> items = load();
        if (items.isEmpty()) {
            new AlertDialog.Builder(context)
                .setTitle("Быстрые шаблоны (VRG)")
                .setMessage("Пока нет ни одного шаблона. Вы сможете вставлять их в чате через ⋮ → VRG → Быстрые шаблоны.")
                .setPositiveButton("Добавить", (d, w) -> showAddDialog(context, fragment, () -> showManageDialog(fragment)))
                .setNegativeButton("Закрыть", null)
                .show();
            return;
        }
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(i);
            String preview = e.text.replace('\n', ' ');
            if (preview.length() > 32) {
                preview = preview.substring(0, 32) + "…";
            }
            labels[i] = e.title + " — " + preview + " (удалить)";
        }
        new AlertDialog.Builder(context)
            .setTitle("Быстрые шаблоны (VRG)")
            .setItems(labels, (d, which) -> {
                if (which >= 0 && which < items.size()) {
                    items.remove(which);
                    save(items);
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "Шаблон удалён").show();
                    showManageDialog(fragment);
                }
            })
            .setPositiveButton("Добавить", (d, w) -> showAddDialog(context, fragment, () -> showManageDialog(fragment)))
            .setNegativeButton("Закрыть", null)
            .show();
    }

    private static void showAddDialog(Context context, BaseFragment fragment, Runnable onAdded) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(4));

        EditText titleEdit = new EditText(context);
        titleEdit.setHint("Название (необязательно)");
        titleEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        box.addView(titleEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        EditText textEdit = new EditText(context);
        textEdit.setHint("Текст шаблона");
        textEdit.setMinLines(2);
        textEdit.setGravity(Gravity.TOP | Gravity.LEFT);
        textEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        box.addView(textEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
            .setTitle("Новый шаблон")
            .setView(box)
            .setPositiveButton("Сохранить", (d, w) -> {
                String text = textEdit.getText() != null ? textEdit.getText().toString().trim() : "";
                if (text.isEmpty()) {
                    return;
                }
                String title = titleEdit.getText() != null ? titleEdit.getText().toString().trim() : "";
                if (title.isEmpty()) {
                    title = text.length() > 24 ? text.substring(0, 24) : text;
                }
                ArrayList<Entry> items = load();
                items.add(new Entry(title, text));
                save(items);
                if (fragment != null) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "Шаблон добавлен").show();
                }
                if (onAdded != null) {
                    onAdded.run();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
