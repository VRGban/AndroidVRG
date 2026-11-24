package org.telegram.vrg.music;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public final class VrgMp4Index {

    private VrgMp4Index() {
    }

    private static int u32(byte[] d, int p) {
        return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16) | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }

    private static boolean is(byte[] d, int p, String type) {
        return d[p] == type.charAt(0) && d[p + 1] == type.charAt(1) && d[p + 2] == type.charAt(2) && d[p + 3] == type.charAt(3);
    }

    private static void putU32(ByteArrayOutputStream out, long v) {
        out.write((int) (v >>> 24) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) v & 0xFF);
    }

    public static byte[] prepare(byte[] d) {
        try {
            byte[] r = addSidx(d);
            return r != null ? r : d;
        } catch (Throwable e) {
            return d;
        }
    }

    private static byte[] addSidx(byte[] d) {
        if (d == null || d.length < 32 || !is(d, 4, "ftyp")) {
            return null;
        }
        int n = d.length;
        ArrayList<int[]> top = new ArrayList<>();
        int pos = 0;
        while (pos + 8 <= n) {
            long size = u32(d, pos) & 0xFFFFFFFFL;
            int start = pos;
            if (size == 1) {
                return null;
            }
            if (size == 0) {
                size = n - pos;
            }
            if (size < 8 || pos + size > n) {
                break;
            }
            top.add(new int[]{start, (int) size});
            pos += (int) size;
        }
        int moovIdx = -1;
        for (int i = 0; i < top.size(); i++) {
            int[] b = top.get(i);
            if (is(d, b[0] + 4, "moov")) {
                moovIdx = i;
            }
        }
        if (moovIdx < 0) {
            return null;
        }
        int[] moov = top.get(moovIdx);
        long[] info = readMoov(d, moov[0] + 8, moov[0] + moov[1]);
        long timescale = info[0];
        long defDur = info[1];
        if (timescale <= 0) {
            return null;
        }

        ByteArrayOutputStream frags = new ByteArrayOutputStream(n);
        ArrayList<long[]> refs = new ArrayList<>();
        int trackId = 1;
        int i = moovIdx + 1;
        while (i < top.size()) {
            int[] b = top.get(i);
            if (is(d, b[0] + 4, "moof")) {
                int fragStart = b[0];
                int fragLen = b[1];
                int j = i + 1;
                while (j < top.size() && !is(d, top.get(j)[0] + 4, "moof")
                    && (is(d, top.get(j)[0] + 4, "mdat") || is(d, top.get(j)[0] + 4, "free") || is(d, top.get(j)[0] + 4, "skip"))) {
                    if (is(d, top.get(j)[0] + 4, "mdat")) {
                        fragLen += top.get(j)[1];
                    }
                    j++;
                }
                long[] fd = readMoof(d, b[0] + 8, b[0] + b[1], defDur);
                if (fd[1] > 0) {
                    trackId = (int) fd[1];
                }
                refs.add(new long[]{fragLen, fd[0]});
                int written = 0;
                for (int k = i; k < j; k++) {
                    int[] bb = top.get(k);
                    if (is(d, bb[0] + 4, "moof") || is(d, bb[0] + 4, "mdat")) {
                        frags.write(d, bb[0], bb[1]);
                        written += bb[1];
                    }
                }
                i = j;
            } else {
                i++;
            }
        }
        if (refs.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream sidx = new ByteArrayOutputStream();
        int boxSize = 32 + 12 * refs.size();
        putU32(sidx, boxSize);
        sidx.write('s');
        sidx.write('i');
        sidx.write('d');
        sidx.write('x');
        putU32(sidx, 0);
        putU32(sidx, trackId);
        putU32(sidx, timescale);
        putU32(sidx, 0);
        putU32(sidx, 0);
        sidx.write(0);
        sidx.write(0);
        sidx.write((refs.size() >>> 8) & 0xFF);
        sidx.write(refs.size() & 0xFF);
        for (long[] ref : refs) {
            putU32(sidx, ref[0] & 0x7FFFFFFFL);
            putU32(sidx, ref[1]);
            putU32(sidx, 0x90000000L);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(n + sidx.size());
        for (int k = 0; k <= moovIdx; k++) {
            int[] bb = top.get(k);
            out.write(d, bb[0], bb[1]);
        }
        byte[] sidxBytes = sidx.toByteArray();
        out.write(sidxBytes, 0, sidxBytes.length);
        byte[] fragBytes = frags.toByteArray();
        out.write(fragBytes, 0, fragBytes.length);
        return out.toByteArray();
    }

    private static long[] readMoov(byte[] d, int from, int to) {
        long timescale = 0;
        long defDur = 0;
        int p = from;
        while (p + 8 <= to) {
            int size = u32(d, p);
            if (size < 8 || p + size > to) {
                break;
            }
            if (is(d, p + 4, "trak")) {
                long ts = findMdhdTimescale(d, p + 8, p + size);
                if (ts > 0 && timescale == 0) {
                    timescale = ts;
                }
            } else if (is(d, p + 4, "mvex")) {
                int q = p + 8;
                int end = p + size;
                while (q + 8 <= end) {
                    int s2 = u32(d, q);
                    if (s2 < 8 || q + s2 > end) {
                        break;
                    }
                    if (is(d, q + 4, "trex") && s2 >= 32) {
                        defDur = u32(d, q + 8 + 12) & 0xFFFFFFFFL;
                    }
                    q += s2;
                }
            }
            p += size;
        }
        return new long[]{timescale, defDur};
    }

    private static long findMdhdTimescale(byte[] d, int from, int to) {
        int p = from;
        while (p + 8 <= to) {
            int size = u32(d, p);
            if (size < 8 || p + size > to) {
                break;
            }
            if (is(d, p + 4, "mdia")) {
                int q = p + 8;
                int end = p + size;
                while (q + 8 <= end) {
                    int s2 = u32(d, q);
                    if (s2 < 8 || q + s2 > end) {
                        break;
                    }
                    if (is(d, q + 4, "mdhd")) {
                        int version = d[q + 8] & 0xFF;
                        int off = q + 8 + 4 + (version == 1 ? 16 : 8);
                        return u32(d, off) & 0xFFFFFFFFL;
                    }
                    q += s2;
                }
            }
            p += size;
        }
        return 0;
    }

    private static long[] readMoof(byte[] d, int from, int to, long trexDefDur) {
        long total = 0;
        long trackId = 0;
        int p = from;
        while (p + 8 <= to) {
            int size = u32(d, p);
            if (size < 8 || p + size > to) {
                break;
            }
            if (is(d, p + 4, "traf")) {
                long tfhdDef = trexDefDur;
                int q = p + 8;
                int end = p + size;
                while (q + 8 <= end) {
                    int s2 = u32(d, q);
                    if (s2 < 8 || q + s2 > end) {
                        break;
                    }
                    if (is(d, q + 4, "tfhd")) {
                        int flags = u32(d, q + 8) & 0xFFFFFF;
                        trackId = u32(d, q + 12) & 0xFFFFFFFFL;
                        int o = q + 16;
                        if ((flags & 0x1) != 0) {
                            o += 8;
                        }
                        if ((flags & 0x2) != 0) {
                            o += 4;
                        }
                        if ((flags & 0x8) != 0) {
                            tfhdDef = u32(d, o) & 0xFFFFFFFFL;
                        }
                    } else if (is(d, q + 4, "trun")) {
                        int flags = u32(d, q + 8) & 0xFFFFFF;
                        long count = u32(d, q + 12) & 0xFFFFFFFFL;
                        int o = q + 16;
                        if ((flags & 0x1) != 0) {
                            o += 4;
                        }
                        if ((flags & 0x4) != 0) {
                            o += 4;
                        }
                        int per = 0;
                        if ((flags & 0x100) != 0) {
                            per += 4;
                        }
                        if ((flags & 0x200) != 0) {
                            per += 4;
                        }
                        if ((flags & 0x400) != 0) {
                            per += 4;
                        }
                        if ((flags & 0x800) != 0) {
                            per += 4;
                        }
                        if ((flags & 0x100) != 0) {
                            for (long k = 0; k < count; k++) {
                                int off = o + (int) (k * per);
                                if (off + 4 > q + s2) {
                                    break;
                                }
                                total += u32(d, off) & 0xFFFFFFFFL;
                            }
                        } else {
                            total += count * tfhdDef;
                        }
                    }
                    q += s2;
                }
            }
            p += size;
        }
        return new long[]{total, trackId};
    }
}

