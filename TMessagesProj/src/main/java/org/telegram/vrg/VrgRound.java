package org.telegram.vrg;

import org.telegram.messenger.MessagesController;

public final class VrgRound {
    private VrgRound() {
    }

    public static int level() {
        return VrgConfig.prefs().getInt("round_quality", 0);
    }

    public static String label(int level) {
        return level == 2 ? "максимальное" : (level == 1 ? "высокое" : "обычное");
    }

    public static int size(int account) {
        int level = level();
        int server = MessagesController.getInstance(account).roundVideoSize;
        return level == 2 ? Math.max(server, 640) : (level == 1 ? Math.max(server, 480) : server);
    }

    public static int bitrate(int account) {
        int level = level();
        int server = MessagesController.getInstance(account).roundVideoBitrate;
        return level == 2 ? Math.max(server, 3500) : (level == 1 ? Math.max(server, 2000) : server);
    }

    public static int audioBitrate(int account) {
        int level = level();
        int server = MessagesController.getInstance(account).roundAudioBitrate;
        return level == 2 ? Math.max(server, 128) : (level == 1 ? Math.max(server, 96) : server);
    }
}

