package com.lexia.accueil;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Reste actif en arrière-plan pour rouvrir l'écran d'accueil quand la Fire TV sort de veille
 * (la télé est rallumée) ou de l'économiseur d'écran. Une Fire TV se met en veille, elle ne
 * redémarre presque jamais : le démarrage seul ne suffirait pas.
 */
public class WakeService extends Service {
    private static final String CHANNEL = "accueil";
    private static final int NOTIFICATION_ID = 1;
    // Laisse l'écran d'accueil d'Amazon se poser avant de passer devant.
    private static final long DELAY_MS = 1500;
    private static final int FGS_TYPE_SPECIAL_USE = 0x40000000; // ServiceInfo, Android 14

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reopen = () -> Launcher.openWelcome(this);

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Prefs.configured(context) || !Prefs.autostart(context)) return;
            handler.removeCallbacks(reopen);
            handler.postDelayed(reopen, DELAY_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, FGS_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION_ID, notification);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_DREAMING_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(wakeReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.configured(this) || !Prefs.autostart(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(reopen);
        unregisterReceiver(wakeReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_text))
                .setOngoing(true)
                .build();
    }
}
