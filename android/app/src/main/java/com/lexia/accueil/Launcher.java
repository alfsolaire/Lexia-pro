package com.lexia.accueil;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/** Ouvre l'écran d'accueil et démarre le service qui le rouvre au réveil de la TV. */
final class Launcher {
    private static final String TAG = "AccueilTV";

    private Launcher() {}

    static void openWelcome(Context c) {
        if (!Prefs.configured(c)) return;
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            // Refusé par le système (Android 10+ sans l'autorisation « par-dessus les autres applis »).
            Log.w(TAG, "Ouverture automatique refusée", e);
        }
    }

    static void startWakeService(Context c) {
        if (!Prefs.configured(c) || !Prefs.autostart(c)) return;
        Intent i = new Intent(c, WakeService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "Service de réveil non démarré", e);
        }
    }

    static void stopWakeService(Context c) {
        c.stopService(new Intent(c, WakeService.class));
    }

    /** Sur Android 10 et plus, une appli ne passe au premier plan seule qu'avec cette autorisation. */
    static boolean canOpenFromBackground(Context c) {
        return Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(c);
    }
}
