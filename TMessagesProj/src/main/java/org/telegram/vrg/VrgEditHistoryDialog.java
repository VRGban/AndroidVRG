package org.telegram.vrg;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class VrgEditHistoryDialog {
    private VrgEditHistoryDialog() {
    }

    public static void show(Context context, int account, MessageObject messageObject) {
        if (context == null || messageObject == null) {
            return;
        }
        List<VrgStore.EditEntry> entries = VrgStore.getInstance().getEditHistory(
            account,
            messageObject.getDialogId(),
            messageObject.getId()
        );
        if (entries.isEmpty()) {
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (int i = entries.size() - 1; i >= 0; i--) {
            VrgStore.EditEntry entry = entries.get(i);
            sb.append("• ").append(fmt.format(new Date(entry.editedAt * 1000L))).append("\n");
            sb.append(entry.text).append("\n\n");
        }
        String currentText = messageObject.messageText != null ? messageObject.messageText.toString() : "";
        if (!currentText.isEmpty()) {
            sb.append("• Текущий текст:\n").append(currentText);
        }
        final String fullText = sb.toString().trim();

        new AlertDialog.Builder(context)
            .setTitle("История правок (" + entries.size() + ")")
            .setMessage(fullText)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Копировать всё", (d, w) -> AndroidUtilities.addToClipboard(fullText))
            .show();
    }
}
