package com.lexia.accueil;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Réglages de l'application : l'adresse de l'écran d'accueil et l'ouverture automatique. */
final class Prefs {
    private static final String FILE = "accueil";
    private static final String URL = "url";
    private static final String AUTOSTART = "autostart";

    private Prefs() {}

    private static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return get(c).getString(URL, "");
    }

    static boolean autostart(Context c) {
        return get(c).getBoolean(AUTOSTART, true);
    }

    static boolean configured(Context c) {
        return isValidUrl(url(c));
    }

    static void save(Context c, String url, boolean autostart) {
        get(c).edit().putString(URL, url.trim()).putBoolean(AUTOSTART, autostart).apply();
    }

    static boolean isValidUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value.trim());
        String scheme = uri.getScheme();
        return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null && !uri.getHost().isEmpty();
    }
}
