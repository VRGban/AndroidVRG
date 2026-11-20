package org.telegram.vrg.music;

import android.text.TextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class VrgTrackMatcher {

    private VrgTrackMatcher() {
    }

    public static final class Want {
        public String artist = "";
        public String title = "";
        public long durationMs;
    }

    private static final Pattern BRACKETS = Pattern.compile("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]");
    private static final Pattern FEAT = Pattern.compile("\\s(feat\\.?|ft\\.?|featuring|with)\\s.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX = Pattern.compile("\\s[-\u2013\u2014]\\s(.*(remaster|version|edit|mix|live|mono|stereo|single|deluxe|bonus|\\d{4}).*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(,|&|;|\\sx\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sand\\s)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH = Pattern.compile("\\s[-\u2013\u2014]\\s");

    private static final String[] BAD = {"remix", "cover", "slowed", "sped", "nightcore", "reverb", "karaoke", "instrumental", "8d", "mashup", "bootleg", "edit"};

    public static Want parse(String line) {
        Want w = new Want();
        String s = line == null ? "" : line.trim();
        java.util.regex.Matcher m = DASH.matcher(s);
        if (m.find()) {
            w.artist = s.substring(0, m.start()).trim();
            w.title = s.substring(m.end()).trim();
        } else {
            w.title = s;
        }
        return w;
    }

    public static String cleanTitle(String title) {
        String t = title == null ? "" : title;
        t = BRACKETS.matcher(t).replaceAll("");
        t = SUFFIX.matcher(t).replaceAll("");
        t = FEAT.matcher(t).replaceAll("");
        return t.trim().replaceAll("\\s+", " ");
    }

    public static String mainArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String[] parts = ARTIST_SPLIT.split(artist.trim());
        return parts.length > 0 ? parts[0].trim() : "";
    }

    private static String translit(char c) {
        switch (c) {
            case 'а': return "a"; case 'б': return "b"; case 'в': return "v"; case 'г': return "g";
            case 'д': return "d"; case 'е': return "e"; case 'ё': return "e"; case 'ж': return "zh";
            case 'з': return "z"; case 'и': return "i"; case 'й': return "y"; case 'к': return "k";
            case 'л': return "l"; case 'м': return "m"; case 'н': return "n"; case 'о': return "o";
            case 'п': return "p"; case 'р': return "r"; case 'с': return "s"; case 'т': return "t";
            case 'у': return "u"; case 'ф': return "f"; case 'х': return "h"; case 'ц': return "c";
            case 'ч': return "ch"; case 'ш': return "sh"; case 'щ': return "sh"; case 'ъ': return "";
            case 'ы': return "y"; case 'ь': return ""; case 'э': return "e"; case 'ю': return "u";
            case 'я': return "ya";
            default: return String.valueOf(c);
        }
    }

    public static List<String> tokens(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return out;
        }
        String d = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(ch)) {
                cur.append(translit(ch));
            } else if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    public static double score(Want w, VrgMusicTypes.TrackInfo c) {
        List<String> titleTokens = tokens(cleanTitle(w.title));
        if (titleTokens.isEmpty()) {
            return 0;
        }
        Set<String> hay = new HashSet<>(tokens(c.artist + " " + c.title));
        int hits = 0;
        for (String t : titleTokens) {
            if (hay.contains(t)) {
                hits++;
            }
        }
        double titleHit = (double) hits / titleTokens.size();

        double artistHit = 1;
        List<List<String>> sets = new ArrayList<>();
        if (!TextUtils.isEmpty(w.artist)) {
            for (String part : ARTIST_SPLIT.split(w.artist)) {
                List<String> tk = tokens(part);
                if (!tk.isEmpty()) {
                    sets.add(tk);
                }
            }
        }
        if (!sets.isEmpty()) {
            artistHit = 0;
            for (List<String> tk : sets) {
                int h = 0;
                for (String t : tk) {
                    if (hay.contains(t)) {
                        h++;
                    }
                }
                artistHit = Math.max(artistHit, (double) h / tk.size());
            }
            if (artistHit < 0.5) {
                return 0.2;
            }
        }
        double score = 0.65 * titleHit + 0.35 * artistHit;
        Set<String> wanted = new HashSet<>(tokens(w.artist + " " + w.title));
        for (String bad : BAD) {
            if (hay.contains(bad) && !wanted.contains(bad)) {
                score -= 0.25;
            }
        }
        if (w.durationMs > 0 && c.durationMs > 0) {
            long diff = Math.abs(w.durationMs - c.durationMs);
            if (diff <= 4000) {
                score += 0.15;
            } else if (diff > 20000) {
                score -= 0.25;
            }
        }
        return score;
    }

    public static List<String> variants(Want w) {
        ArrayList<String> v = new ArrayList<>();
        String clean = cleanTitle(w.title);
        String main = mainArtist(w.artist);
        String[] cand = {
            (main + " " + clean).trim(),
            (w.artist + " " + w.title).trim(),
            clean
        };
        for (String c : cand) {
            String s = c.trim().replaceAll("\\s+", " ");
            if (!s.isEmpty() && !v.contains(s)) {
                v.add(s);
            }
        }
        return v;
    }
}
