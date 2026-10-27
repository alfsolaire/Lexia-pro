package com.lexia.accueil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Au démarrage de la Fire TV (ou après une mise à jour de l'appli) : ouvre l'écran d'accueil. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.configured(context) || !Prefs.autostart(context)) return;
        Launcher.startWakeService(context);
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) Launcher.openWelcome(context);
    }
}
