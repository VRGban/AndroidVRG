package org.telegram.vrg.music;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class VrgId3 {
    private VrgId3() {
    }

    private static byte[] textFrame(String id, String text) {
        byte[] utf16 = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] body = new byte[1 + 2 + utf16.length + 2];
        body[0] = 1;
        body[1] = (byte) 0xFF;
        body[2] = (byte) 0xFE;
        System.arraycopy(utf16, 0, body, 3, utf16.length);
        return frame(id, body);
    }

    private static byte[] frame(String id, byte[] body) {
        byte[] out = new byte[10 + body.length];
        byte[] idb = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idb, 0, out, 0, 4);
        out[4] = (byte) (body.length >>> 24);
        out[5] = (byte) (body.length >>> 16);
        out[6] = (byte) (body.length >>> 8);
        out[7] = (byte) body.length;
        System.arraycopy(body, 0, out, 10, body.length);
        return out;
    }

    public static byte[] withTags(byte[] mp3, String title, String artist, byte[] jpegCover) {
        if (mp3 == null || mp3.length < 4 || (mp3[0] == 'I' && mp3[1] == 'D' && mp3[2] == '3')) {
            return mp3;
        }
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        try {
            if (title != null && !title.isEmpty()) {
                frames.write(textFrame("TIT2", title));
            }
            if (artist != null && !artist.isEmpty()) {
                frames.write(textFrame("TPE1", artist));
            }
            if (jpegCover != null && jpegCover.length > 0) {
                ByteArrayOutputStream pic = new ByteArrayOutputStream();
                pic.write(0);
                pic.write("image/jpeg".getBytes(StandardCharsets.US_ASCII));
                pic.write(0);
                pic.write(3);
                pic.write(0);
                pic.write(jpegCover);
                frames.write(frame("APIC", pic.toByteArray()));
            }
            byte[] f = frames.toByteArray();
            if (f.length == 0) {
                return mp3;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(mp3.length + f.length + 10);
            out.write('I');
            out.write('D');
            out.write('3');
            out.write(3);
            out.write(0);
            out.write(0);
            out.write((f.length >>> 21) & 0x7F);
            out.write((f.length >>> 14) & 0x7F);
            out.write((f.length >>> 7) & 0x7F);
            out.write(f.length & 0x7F);
            out.write(f);
            out.write(mp3);
            return out.toByteArray();
        } catch (Throwable e) {
            return mp3;
        }
    }
}

