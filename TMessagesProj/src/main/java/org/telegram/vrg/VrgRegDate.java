package org.telegram.vrg;

public final class VrgRegDate {
    private static final long[] ANCHOR_IDS = {
        2768409L,
        7679610L,
        20000000L,
        100000000L,
        200000000L,
        350000000L,
        500000000L,
        750000000L,
        1000000000L,
        1400000000L,
        2000000000L,
        5000000000L,
        5500000000L,
        6000000000L,
        6500000000L,
        7000000000L,
        7500000000L,
        8000000000L,
        8500000000L,
        9000000000L
    };

    private static final int[] ANCHOR_YEARS = {
        2013, 2014, 2014, 2015, 2016, 2017, 2018, 2018, 2019, 2020,
        2021, 2021, 2022, 2023, 2023, 2024, 2024, 2025, 2025, 2026
    };

    private static final int[] ANCHOR_MONTHS = {
        11, 1, 8, 5, 3, 4, 2, 11, 11, 9,
        9, 11, 7, 2, 9, 3, 9, 4, 11, 6
    };

    private static final String[] MONTHS_RU = {
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };

    private VrgRegDate() {
    }

    public static String estimate(long userId) {
        if (userId <= 0) {
            return "";
        }
        if (userId <= ANCHOR_IDS[0]) {
            return "до ноября 2013";
        }
        int last = ANCHOR_IDS.length - 1;
        if (userId >= ANCHOR_IDS[last]) {
            return "после июня 2026";
        }
        for (int i = 1; i < ANCHOR_IDS.length; i++) {
            long idA = ANCHOR_IDS[i - 1];
            long idB = ANCHOR_IDS[i];
            if (userId <= idB) {
                double t = (double) (userId - idA) / (double) (idB - idA);
                int mA = ANCHOR_YEARS[i - 1] * 12 + (ANCHOR_MONTHS[i - 1] - 1);
                int mB = ANCHOR_YEARS[i] * 12 + (ANCHOR_MONTHS[i] - 1);
                int m = (int) (mA + t * (mB - mA) + 0.5);
                int year = m / 12;
                int month = m % 12;
                return "~" + MONTHS_RU[month] + " " + year;
            }
        }
        return "";
    }
}
