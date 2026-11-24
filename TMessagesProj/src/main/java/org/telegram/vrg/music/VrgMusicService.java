package org.telegram.vrg.music;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.telegram.messenger.FileLog;

public class VrgMusicService extends Service {
    public static final int NOTIF_ID = 94721;
    private static VrgMusicService instance;
    private static Notification pending;

    public static void post(Context ctx, Notification notification, boolean playing) {
        pending = notification;
        if (playing) {
            if (instance != null) {
                instance.promote(notification);
                return;
            }
            try {
                Intent intent = new Intent(ctx, VrgMusicService.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(intent);
                } else {
                    ctx.startService(intent);
                }
                return;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        } else if (instance != null) {
            instance.demote(notification);
            return;
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, notification);
        }
    }

    public static void stop(Context ctx) {
        pending = null;
        if (instance != null) {
            instance.shutdown();
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
        }
    }

    private void promote(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void demote(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_DETACH);
            } else {
                stopForeground(false);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, n);
        }
    }

    private void shutdown() {
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        stopSelf();
        instance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        Notification n = pending;
        if (n == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        promote(n);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

