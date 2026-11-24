package org.telegram.vrg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationsService;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

public final class VrgBackgroundSync {
    private static final String CHANNEL_ID = "vrg_background_sync_v3";
    private static final String OLD_CHANNEL_ID = "vrg_background_sync";
    private static final String OLD_CHANNEL_ID_V2 = "vrg_background_sync_v2";
    private static final int NOTIFICATION_ID = 77401;

    private VrgBackgroundSync() {
    }

    public static void ensureForegroundIfNeeded(Service service) {
        if (!VrgConfig.isBackgroundAntiDelete()) {
            try {
                service.stopForeground(true);
            } catch (Throwable ignore) {
            }
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(OLD_CHANNEL_ID) != null) {
                    nm.deleteNotificationChannel(OLD_CHANNEL_ID);
                }
                if (nm != null && nm.getNotificationChannel(OLD_CHANNEL_ID_V2) != null) {
                    nm.deleteNotificationChannel(OLD_CHANNEL_ID_V2);
                }
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "VRG Фоновая защита",
                        NotificationManager.IMPORTANCE_MIN
                    );
                    channel.setShowBadge(false);
                    channel.enableLights(false);
                    channel.enableVibration(false);
                    channel.setSound(null, null);
                    channel.setDescription("Сохраняет удалённые сообщения в фоне с минимальным расходом заряда");
                    nm.createNotificationChannel(channel);
                }
            }

            Intent launchIntent = new Intent(service, LaunchActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, launchIntent, piFlags);

            Intent restoreIntent = new Intent(service, NotificationsService.class).setAction("org.telegram.vrg.RESTORE");
            PendingIntent restorePi = PendingIntent.getService(service, 1, restoreIntent, piFlags);

            Notification notification = new NotificationCompat.Builder(service, CHANNEL_ID)
                .setDeleteIntent(restorePi)
                .setSmallIcon(R.drawable.vrg_notification_blank)
                .setContentTitle("VRG")
                .setContentText("Фоновая защита от удаления активна")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                service.startForeground(NOTIFICATION_ID, notification);
            }

            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).setPushConnectionEnabled(true);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void syncServiceState() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return;
        }
        Intent intent = new Intent(ctx, NotificationsService.class);
        try {
            if (VrgConfig.isBackgroundAntiDelete()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent);
                } else {
                    ctx.startService(intent);
                }
            } else {
                ctx.stopService(intent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if (!isIgnoringBatteryOptimizations(context)) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                if (!(context instanceof android.app.Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
                return;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        openAppDetailsSettings(context);
    }

    public static void openVendorAutostartSettings(Context context) {
        if (context == null) {
            return;
        }
        Intent[] vendorIntents = new Intent[] {

            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),

            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),

            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),

            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),

            new Intent().setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),

            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),

            new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        };

        PackageManager pm = context.getPackageManager();
        for (Intent intent : vendorIntents) {
            try {
                if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    if (!(context instanceof android.app.Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                    return;
                }
            } catch (Throwable ignore) {
            }
        }
        openAppDetailsSettings(context);
    }

    public static void openAppDetailsSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Throwable e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignore) {
            }
        }
    }
}

