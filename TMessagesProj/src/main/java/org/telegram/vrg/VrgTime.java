package org.telegram.vrg;

import java.util.TimeZone;

public final class VrgTime {
    private static TimeZone original;

    private VrgTime() {
    }

    public static int shiftMinutes() {
        return VrgConfig.prefs().getInt("time_shift_min", 0);
    }

    public static void setShiftMinutes(int minutes) {
        VrgConfig.prefs().edit().putInt("time_shift_min", minutes).apply();
        apply();
    }

    public static void apply() {
        if (original == null) {
            original = TimeZone.getDefault();
        }
        int shift = shiftMinutes();
        if (shift == 0) {
            TimeZone.setDefault(original);
            return;
        }
        int total = original.getOffset(System.currentTimeMillis()) / 60000 + shift;
        int abs = Math.abs(total);
        String id = String.format("GMT%s%02d:%02d", total < 0 ? "-" : "+", abs / 60, abs % 60);
        TimeZone.setDefault(TimeZone.getTimeZone(id));
    }

    public static String label(int minutes) {
        if (minutes == 0) {
            return "без сдвига";
        }
        int abs = Math.abs(minutes);
        String text = (minutes < 0 ? "−" : "+") + (abs / 60) + " ч";
        if (abs % 60 != 0) {
            text += " " + (abs % 60) + " мин";
        }
        return text;
    }
}

