package com.lexia.accueil;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Économiseur d'écran : l'écran d'accueil (photos, météo, prénom) en mode « veille », sans le
 * rappel des touches. Un appui sur la télécommande le ferme ; le service de réveil ouvre alors
 * l'écran d'accueil complet. Fire OS ne propose pas les économiseurs des autres applis dans ses
 * menus : il se choisit par adb (voir README).
 */
public class AccueilDream extends DreamService {
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        if (!Prefs.configured(this)) return;

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);

        String url = Prefs.url(this);
        web.loadUrl(url + (url.contains("?") ? "&" : "?") + "veille=1");
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
