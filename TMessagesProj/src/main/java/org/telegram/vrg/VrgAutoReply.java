package org.telegram.vrg;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public final class VrgAutoReply {
    private static final class PeerLast {
        long greeting;
        long command;
    }

    private static final HashMap<Long, PeerLast> lastSent = new HashMap<>();

    public static final class IgnoredEntry {
        public final long peerId;
        public final String title;

        public IgnoredEntry(long peerId, String title) {
            this.peerId = peerId;
            this.title = title;
        }
    }

    private VrgAutoReply() {
    }

    public static boolean isEnabled() {
        return VrgConfig.prefs().getBoolean("autoreply_on", false);
    }

    public static void setEnabled(boolean value) {
        VrgConfig.prefs().edit().putBoolean("autoreply_on", value).apply();
    }

    public static String getGreeting() {
        return VrgConfig.prefs().getString("autoreply_text", "");
    }

    public static void setGreeting(String text) {
        VrgConfig.prefs().edit().putString("autoreply_text", text != null ? text : "").apply();
    }

    public static String getCommands() {
        return VrgConfig.prefs().getString("autoreply_cmds", "");
    }

    public static void setCommands(String cmds) {
        VrgConfig.prefs().edit().putString("autoreply_cmds", cmds != null ? cmds : "").apply();
    }

    public static int getGreetingCooldownMin() {
        return Math.max(1, VrgConfig.prefs().getInt("autoreply_cooldown", 60));
    }

    public static void setGreetingCooldownMin(int minutes) {
        VrgConfig.prefs().edit().putInt("autoreply_cooldown", Math.max(1, minutes)).apply();
    }

    public static int getCmdCooldownSec() {
        return Math.max(0, VrgConfig.prefs().getInt("autoreply_cmd_cooldown", 5));
    }

    public static void setCmdCooldownSec(int seconds) {
        VrgConfig.prefs().edit().putInt("autoreply_cmd_cooldown", Math.max(0, seconds)).apply();
    }

    private static JSONObject loadIgnoredJson() {
        String raw = VrgConfig.prefs().getString("autoreply_ignored", "");
        if (TextUtils.isEmpty(raw)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (Throwable ignore) {
            return new JSONObject();
        }
    }

    public static boolean isIgnored(long peerId) {
        return loadIgnoredJson().has(String.valueOf(peerId));
    }

    public static void addIgnored(long peerId, String title) {
        try {
            JSONObject obj = loadIgnoredJson();
            obj.put(String.valueOf(peerId), TextUtils.isEmpty(title) ? String.valueOf(peerId) : title);
            VrgConfig.prefs().edit().putString("autoreply_ignored", obj.toString()).apply();
        } catch (Throwable ignore) {
        }
    }

    public static void removeIgnored(long peerId) {
        JSONObject obj = loadIgnoredJson();
        obj.remove(String.valueOf(peerId));
        VrgConfig.prefs().edit().putString("autoreply_ignored", obj.toString()).apply();
    }

    public static ArrayList<IgnoredEntry> listIgnored() {
        ArrayList<IgnoredEntry> out = new ArrayList<>();
        JSONObject obj = loadIgnoredJson();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String k = it.next();
            try {
                long id = Long.parseLong(k);
                out.add(new IgnoredEntry(id, obj.optString(k, k)));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    public static void handleIncoming(int account, long dialogId, ArrayList<MessageObject> messages) {
        if (!isEnabled() || dialogId <= 0 || messages == null || messages.isEmpty()) {
            return;
        }
        if (dialogId == UserConfig.getInstance(account).getClientUserId() || isIgnored(dialogId)) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
        if (user == null || user.self || user.bot || MessagesController.isSupportUser(user)) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg == null || msg.messageOwner == null || msg.isOut() || msg.scheduled) {
                continue;
            }
            if (msg.messageOwner.action != null && !(msg.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)) {
                continue;
            }
            if (Math.abs(nowSec - msg.messageOwner.date) > 60) {
                continue;
            }
            String incomingText = msg.messageOwner.message != null ? msg.messageOwner.message.trim() : "";
            String answer = null;
            boolean isCmd = false;

            if (incomingText.startsWith("/")) {
                String word = incomingText.split("\\s+")[0].toLowerCase();
                answer = matchCommand(word);
                if (!TextUtils.isEmpty(answer)) {
                    isCmd = true;
                }
            }

            PeerLast last = lastSent.get(dialogId);
            if (last == null) {
                last = new PeerLast();
                lastSent.put(dialogId, last);
            }

            if (isCmd) {
                long cd = getCmdCooldownSec();
                if (nowSec - last.command < cd) {
                    continue;
                }
                last.command = nowSec;
            } else {
                String greeting = getGreeting().trim();
                if (greeting.isEmpty()) {
                    continue;
                }
                long cd = getGreetingCooldownMin() * 60L;
                if (nowSec - last.greeting < cd) {
                    continue;
                }
                answer = greeting;
                last.greeting = nowSec;
            }

            final String finalAnswer = answer;
            AndroidUtilities.runOnUIThread(() -> {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                    finalAnswer, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false
                );
                SendMessagesHelper.getInstance(account).sendMessage(params);
            });
            break;
        }
    }

    private static String matchCommand(String word) {
        String cmds = getCommands();
        if (TextUtils.isEmpty(cmds)) {
            return null;
        }
        String[] lines = cmds.split("\n");
        boolean capturing = false;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int arrow = line.indexOf("->");
            String trimmed = line.trim();
            boolean isHeader = arrow > 0 && trimmed.startsWith("/");
            if (isHeader) {
                if (capturing) {
                    break;
                }
                String cmdKey = line.substring(0, arrow).trim().toLowerCase();
                if (cmdKey.equals(word)) {
                    capturing = true;
                    String rest = line.substring(arrow + 2).trim();
                    if (!rest.isEmpty()) {
                        sb.append(rest);
                    }
                }
            } else if (capturing) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? null : res;
    }
}

