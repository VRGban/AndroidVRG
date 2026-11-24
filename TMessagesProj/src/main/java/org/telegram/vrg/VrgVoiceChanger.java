package org.telegram.vrg;

import android.content.SharedPreferences;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VrgVoiceChanger {
    public static final class Preset {
        public final String id;
        public final String title;
        public final String description;
        public final int pitch;
        public final int bass;
        public final int treble;
        public final int tempo;
        public final int echo;
        public final boolean robot;
        public final boolean preserveFormants;

        Preset(String id, String title, String description, int pitch, int bass, int treble, int tempo, int echo, boolean robot, boolean preserveFormants) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.pitch = pitch;
            this.bass = bass;
            this.treble = treble;
            this.tempo = tempo;
            this.echo = echo;
            this.robot = robot;
            this.preserveFormants = preserveFormants;
        }
    }

    public static final Preset[] PRESETS = new Preset[]{
        new Preset("male", "Мужской", "Более низкий и глубокий мужской голос", -4, 6, 1, 100, 0, false, false),
        new Preset("deep", "Демон / Бас", "Тяжёлый низкий голос с лёгким эхом", -7, 9, -2, 95, 25, false, false),
        new Preset("female", "Женский", "Выше тон и мягче верха", 5, -2, 3, 100, 0, false, false),
        new Preset("chipmunk", "Бурундук", "Высокий писклявый и чуть ускоренный", 9, -4, 4, 112, 0, false, false),
        new Preset("robot", "Робот / Рация", "Эффект рации с модуляцией", -2, -3, 2, 100, 15, true, false),
        new Preset("custom", "Свой (ползунки)", "Ручная настройка всех параметров ниже", 0, 0, 0, 100, 0, false, false),
    };

    private VrgVoiceChanger() {
    }

    private static SharedPreferences p() {
        return VrgConfig.prefs();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static boolean isEnabled() {
        return p().getBoolean("voice_changer_enabled", false);
    }

    public static void setEnabled(boolean v) {
        p().edit().putBoolean("voice_changer_enabled", v).apply();
    }

    public static String getPresetId() {
        String v = p().getString("voice_changer_preset", "");
        return v == null || v.isEmpty() ? "male" : v;
    }

    public static String presetTitle(String id) {
        for (Preset pr : PRESETS) {
            if (pr.id.equals(id)) {
                return pr.title;
            }
        }
        return PRESETS[0].title;
    }

    public static int getPitch() {
        return clamp(p().getInt("voice_pitch", -4), -12, 12);
    }

    public static int getBass() {
        return clamp(p().getInt("voice_bass", 6), -6, 12);
    }

    public static int getTreble() {
        return clamp(p().getInt("voice_treble", 1), -6, 12);
    }

    public static int getTempo() {
        return clamp(p().getInt("voice_tempo", 100), 75, 135);
    }

    public static int getEcho() {
        return clamp(p().getInt("voice_echo", 0), 0, 100);
    }

    public static boolean isRobot() {
        return p().getBoolean("voice_robot", false);
    }

    public static boolean isPreserveFormants() {
        return p().getBoolean("voice_preserve_formants", false);
    }

    public static void setPitch(int v) {
        p().edit().putInt("voice_pitch", clamp(v, -12, 12)).apply();
    }

    public static void setBass(int v) {
        p().edit().putInt("voice_bass", clamp(v, -6, 12)).apply();
    }

    public static void setTreble(int v) {
        p().edit().putInt("voice_treble", clamp(v, -6, 12)).apply();
    }

    public static void setTempo(int v) {
        p().edit().putInt("voice_tempo", clamp(v, 75, 135)).apply();
    }

    public static void setEcho(int v) {
        p().edit().putInt("voice_echo", clamp(v, 0, 100)).apply();
    }

    public static void setRobot(boolean v) {
        p().edit().putBoolean("voice_robot", v).apply();
    }

    public static void setPreserveFormants(boolean v) {
        p().edit().putBoolean("voice_preserve_formants", v).apply();
    }

    public static void applyPreset(String id) {
        SharedPreferences.Editor e = p().edit().putString("voice_changer_preset", id);
        for (Preset pr : PRESETS) {
            if (pr.id.equals(id) && !id.equals("custom")) {
                e.putInt("voice_pitch", pr.pitch)
                    .putInt("voice_bass", pr.bass)
                    .putInt("voice_treble", pr.treble)
                    .putInt("voice_tempo", pr.tempo)
                    .putInt("voice_echo", pr.echo)
                    .putBoolean("voice_robot", pr.robot)
                    .putBoolean("voice_preserve_formants", pr.preserveFormants);
            }
        }
        e.apply();
    }

    public static Processor createProcessor(int sampleRate) {
        if (!isEnabled()) {
            return null;
        }
        Processor processor = new Processor(sampleRate, getPitch(), getBass(), getTreble(), getEcho(), isRobot());
        return processor.isIdentity() ? null : processor;
    }

    private static final class Biquad {
        private double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        static Biquad lowShelf(double fs, double f0, double gainDb, double q) {
            double a = Math.pow(10, gainDb / 40);
            double w = 2 * Math.PI * f0 / fs;
            double cs = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double beta = 2 * Math.sqrt(a) * alpha;
            Biquad f = new Biquad();
            double a0 = (a + 1) + (a - 1) * cs + beta;
            f.b0 = a * ((a + 1) - (a - 1) * cs + beta) / a0;
            f.b1 = 2 * a * ((a - 1) - (a + 1) * cs) / a0;
            f.b2 = a * ((a + 1) - (a - 1) * cs - beta) / a0;
            f.a1 = -2 * ((a - 1) + (a + 1) * cs) / a0;
            f.a2 = ((a + 1) + (a - 1) * cs - beta) / a0;
            return f;
        }

        static Biquad highShelf(double fs, double f0, double gainDb, double q) {
            double a = Math.pow(10, gainDb / 40);
            double w = 2 * Math.PI * f0 / fs;
            double cs = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            double beta = 2 * Math.sqrt(a) * alpha;
            Biquad f = new Biquad();
            double a0 = (a + 1) - (a - 1) * cs + beta;
            f.b0 = a * ((a + 1) + (a - 1) * cs + beta) / a0;
            f.b1 = -2 * a * ((a - 1) + (a + 1) * cs) / a0;
            f.b2 = a * ((a + 1) + (a - 1) * cs - beta) / a0;
            f.a1 = 2 * ((a - 1) - (a + 1) * cs) / a0;
            f.a2 = ((a + 1) - (a - 1) * cs - beta) / a0;
            return f;
        }

        static Biquad highPass(double fs, double f0, double q) {
            double w = 2 * Math.PI * f0 / fs;
            double cs = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            Biquad f = new Biquad();
            double a0 = 1 + alpha;
            f.b0 = (1 + cs) / 2 / a0;
            f.b1 = -(1 + cs) / a0;
            f.b2 = (1 + cs) / 2 / a0;
            f.a1 = -2 * cs / a0;
            f.a2 = (1 - alpha) / a0;
            return f;
        }

        static Biquad lowPass(double fs, double f0, double q) {
            double w = 2 * Math.PI * f0 / fs;
            double cs = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            Biquad f = new Biquad();
            double a0 = 1 + alpha;
            f.b0 = (1 - cs) / 2 / a0;
            f.b1 = (1 - cs) / a0;
            f.b2 = (1 - cs) / 2 / a0;
            f.a1 = -2 * cs / a0;
            f.a2 = (1 - alpha) / a0;
            return f;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    public static final class Processor {
        private static final int WINDOW_MS = 40;
        private final int pitch;
        private final boolean robot;
        private final int echo;
        private final Biquad bass;
        private final Biquad treble;
        private final Biquad robotHigh;
        private final Biquad robotLow;

        private final double[] pitchBuf;
        private final int pitchMask;
        private int pitchPos;
        private final double windowSamples;
        private double phase;
        private final double rate;

        private final double[] echoBuf;
        private int echoPos;
        private final int echoDelay;
        private final double echoDecay;

        private final double[] flangeBuf;
        private int flangePos;
        private double flangePhase;
        private final double flangeBase;
        private final double flangeDepth;
        private final double flangeStep;

        Processor(int sampleRate, int pitch, int bassDb, int trebleDb, int echo, boolean robot) {
            this.pitch = pitch;
            this.robot = robot;
            this.echo = echo;
            this.bass = bassDb != 0 ? Biquad.lowShelf(sampleRate, 120, bassDb, 0.7) : null;
            this.treble = trebleDb != 0 ? Biquad.highShelf(sampleRate, 3500, trebleDb, 0.7) : null;
            this.robotHigh = robot ? Biquad.highPass(sampleRate, 350, 0.707) : null;
            this.robotLow = robot ? Biquad.lowPass(sampleRate, 2800, 0.707) : null;

            int size = 1;
            while (size < sampleRate / 4) {
                size <<= 1;
            }
            pitchBuf = new double[size];
            pitchMask = size - 1;
            windowSamples = sampleRate * WINDOW_MS / 1000.0;
            rate = Math.pow(2.0, pitch / 12.0);
            phase = 0;

            echoDelay = sampleRate * (35 + (echo * 85) / 100) / 1000;
            echoDecay = 0.15 + (echo / 100.0) * 0.45;
            echoBuf = new double[Math.max(1, echoDelay + 1)];

            flangeBase = sampleRate * 0.004;
            flangeDepth = sampleRate * 0.006;
            flangeBuf = new double[(int) (flangeBase + flangeDepth) + 4];
            flangeStep = 2 * Math.PI * 4.0 / sampleRate;
        }

        boolean isIdentity() {
            return pitch == 0 && bass == null && treble == null && echo == 0 && !robot;
        }

        public void process(ByteBuffer buffer, int byteCount) {
            int count = byteCount / 2;
            int start = buffer.position();
            ByteBuffer b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                int idx = start + i * 2;
                double x = b.getShort(idx);
                x = processSample(x);
                if (x > 32767.0) {
                    x = 32767.0;
                } else if (x < -32768.0) {
                    x = -32768.0;
                }
                b.putShort(idx, (short) x);
            }
        }

        private double readPitch(double delay) {
            double pos = pitchPos - delay;
            int i0 = (int) Math.floor(pos);
            double frac = pos - i0;
            double s0 = pitchBuf[i0 & pitchMask];
            double s1 = pitchBuf[(i0 + 1) & pitchMask];
            return s0 + (s1 - s0) * frac;
        }

        private double processSample(double x) {
            if (pitch != 0) {
                pitchBuf[pitchPos & pitchMask] = x;
                double p2 = phase + 0.5;
                if (p2 >= 1.0) {
                    p2 -= 1.0;
                }
                double g1 = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
                double g2 = 0.5 - 0.5 * Math.cos(2 * Math.PI * p2);
                double d1 = phase * windowSamples + 2;
                double d2 = p2 * windowSamples + 2;
                x = readPitch(d1) * g1 + readPitch(d2) * g2;
                pitchPos++;
                phase += (1.0 - rate) / windowSamples;
                phase -= Math.floor(phase);
            }
            if (robot) {
                x = robotLow.process(robotHigh.process(x));
                flangeBuf[flangePos] = x;
                double d = flangeBase + flangeDepth * 0.5 * (1 + Math.sin(flangePhase));
                flangePhase += flangeStep;
                double pos = flangePos - d;
                while (pos < 0) {
                    pos += flangeBuf.length;
                }
                int i0 = (int) pos;
                double frac = pos - i0;
                double s0 = flangeBuf[i0 % flangeBuf.length];
                double s1 = flangeBuf[(i0 + 1) % flangeBuf.length];
                double delayed = s0 + (s1 - s0) * frac;
                x = x + delayed * 0.7;
                flangeBuf[flangePos] = x * 0.5 + delayed * 0.5;
                flangePos = (flangePos + 1) % flangeBuf.length;
                x = Math.tanh(x / 20000.0) * 20000.0;
            }
            if (bass != null) {
                x = bass.process(x);
            }
            if (treble != null) {
                x = treble.process(x);
            }
            if (echo > 0) {
                double delayed = echoBuf[echoPos];
                double y = x + delayed * echoDecay;
                echoBuf[echoPos] = y;
                echoPos = (echoPos + 1) % echoBuf.length;
                x = y;
            }
            return Math.tanh(x / 31000.0) * 31000.0;
        }
    }
}

