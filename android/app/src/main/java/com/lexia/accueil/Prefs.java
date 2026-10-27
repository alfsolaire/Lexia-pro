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

    /** Adresse ouverte : celle enregistrée, sinon le serveur intégré à l'APK (page d'appairage). */
    static String url(Context c) {
        String saved = get(c).getString(URL, "");
        return saved.isEmpty() ? normalize(c.getString(R.string.serveur_defaut)) : saved;
    }

    static void saveUrl(Context c, String url) {
        get(c).edit().putString(URL, url).apply();
    }

    /** « tv.exemple.fr » → « https://tv.exemple.fr/ » ; une adresse complète est gardée telle quelle. */
    static String normalize(String input) {
        String v = input == null ? "" : input.trim();
        if (v.isEmpty()) return "";
        if (!v.contains("://")) v = "https://" + v;
        Uri uri = Uri.parse(v);
        if (uri.getPath() == null || uri.getPath().isEmpty()) v = v + "/";
        return v;
    }

    static boolean autostart(Context c) {
        return get(c).getBoolean(AUTOSTART, true);
    }

    static boolean configured(Context c) {
        return isValidUrl(url(c));
    }

    static void save(Context c, String url, boolean autostart) {
        get(c).edit().putString(URL, normalize(url)).putBoolean(AUTOSTART, autostart).apply();
    }

    static boolean isValidUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        Uri uri = Uri.parse(normalize(value));
        String scheme = uri.getScheme();
        return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null && !uri.getHost().isEmpty();
    }
}
