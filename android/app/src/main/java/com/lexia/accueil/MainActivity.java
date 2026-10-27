package com.lexia.accueil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * L'écran d'accueil du logement, en plein écran. Les touches de la télécommande vont à la page
 * (flèches, OK, Retour pour fermer le guide). Réglages cachés : 5 appuis sur ☰ (Menu).
 */
public class MainActivity extends Activity {
    private static final long RETRY_MS = 30_000;
    private static final long RELOAD_MS = 6 * 60 * 60 * 1000L; // reprend les mises à jour de la page
    private static final int MENU_PRESSES = 5;
    private static final long MENU_WINDOW_MS = 4_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView status;
    private String loadedUrl;
    private boolean failed;
    private int menuCount;
    private long menuFirstAt;

    private final Runnable retry = () -> load(true);
    private final Runnable periodicReload = new Runnable() {
        @Override
        public void run() {
            if (!failed) web.reload();
            handler.postDelayed(this, RELOAD_MS);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.configured(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new Client());

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(22);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.rgb(15, 18, 24));
        status.setPadding(64, 64, 64, 64);
        status.setVisibility(View.GONE);

        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        load(false);
        handler.postDelayed(periodicReload, RELOAD_MS);
        Launcher.startWakeService(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        hideSystemUi();
        web.requestFocus();
        // Adresse changée dans les réglages : on recharge.
        if (!Prefs.url(this).equals(loadedUrl)) load(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void load(boolean isRetry) {
        handler.removeCallbacks(retry);
        failed = false;
        loadedUrl = Prefs.url(this);
        if (!isRetry) showStatus(getString(R.string.loading));
        web.loadUrl(loadedUrl);
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            long now = System.currentTimeMillis();
            if (now - menuFirstAt > MENU_WINDOW_MS) {
                menuFirstAt = now;
                menuCount = 0;
            }
            if (++menuCount >= MENU_PRESSES) {
                menuCount = 0;
                startActivity(new Intent(this, SetupActivity.class));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Retour ferme le guide ou le quiz (historique de la page). Sur l'écran d'accueil lui-même,
        // on reste : la touche Accueil d'Amazon permet toujours d'aller sur Netflix & co.
        if (web != null && web.canGoBack()) web.goBack();
    }

    private static boolean isWelcomePage(Uri uri) {
        String path = uri.getPath();
        return path != null && path.matches("/accueil/[^/]+/?");
    }

    private boolean sameHost(Uri uri) {
        String host = Uri.parse(loadedUrl).getHost();
        return host != null && host.equals(uri.getHost());
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // Reste sur le serveur de l'écran d'accueil : pas de navigation vers d'autres sites.
            Uri home = Uri.parse(loadedUrl);
            Uri target = request.getUrl();
            return home.getHost() == null || !home.getHost().equals(target.getHost());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!failed) status.setVisibility(View.GONE);
            // Appairage réussi : la page du code a mené à /accueil/<clé>/ ; on la garde pour la suite.
            Uri page = Uri.parse(url);
            if (isWelcomePage(page) && sameHost(page) && !url.equals(loadedUrl)) {
                Prefs.saveUrl(MainActivity.this, url);
                loadedUrl = url;
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            // Clé changée ou logement retiré : retour à la page du code pour appairer de nouveau.
            Uri page = request.getUrl();
            if (request.isForMainFrame() && response.getStatusCode() == 404 && isWelcomePage(page)) {
                String root = page.getScheme() + "://" + page.getAuthority() + "/";
                Prefs.saveUrl(MainActivity.this, root);
                load(false);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            failed = true;
            String detail = Build.VERSION.SDK_INT >= 23 ? String.valueOf(error.getDescription()) : "";
            showStatus(getString(R.string.offline, detail));
            handler.removeCallbacks(retry);
            handler.postDelayed(retry, RETRY_MS);
        }
    }
}
