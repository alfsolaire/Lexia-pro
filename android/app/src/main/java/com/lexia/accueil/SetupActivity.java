package com.lexia.accueil;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Réglage unique : l'adresse de l'écran d'accueil du logement. Le clavier de l'appli
 * « Amazon Fire TV » sur le téléphone évite de la taper à la télécommande.
 * Par adb : adb shell am start -n com.lexia.accueil/.SetupActivity -e url https://…
 */
public class SetupActivity extends Activity {
    private EditText url;
    private CheckBox autostart;
    private TextView message;
    private Button permission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Réglage par adb : on enregistre et on ouvre directement.
        String fromIntent = getIntent().getStringExtra("url");
        if (Prefs.isValidUrl(fromIntent)) {
            Prefs.save(this, fromIntent, getIntent().getBooleanExtra("autostart", true));
            openWelcome();
            return;
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(96, 64, 96, 64);
        col.setBackgroundColor(Color.rgb(15, 18, 24));

        col.addView(text(getString(R.string.setup_title), 34, Color.WHITE));
        col.addView(text(getString(R.string.setup_help), 18, Color.rgb(170, 178, 192)));

        url = new EditText(this);
        url.setSingleLine(true);
        url.setTextSize(22);
        url.setTextColor(Color.WHITE);
        url.setHint("tv.votre-domaine.fr");
        url.setHintTextColor(Color.GRAY);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_DONE);
        url.setText(Prefs.configured(this) ? Prefs.url(this) : "");
        url.setSelection(url.getText().length());
        url.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) save();
            return false;
        });
        col.addView(url, spaced());

        autostart = new CheckBox(this);
        autostart.setText(R.string.setup_autostart);
        autostart.setTextSize(20);
        autostart.setTextColor(Color.WHITE);
        autostart.setChecked(Prefs.autostart(this));
        col.addView(autostart, spaced());

        Button save = new Button(this);
        save.setText(R.string.setup_save);
        save.setTextSize(20);
        save.setOnClickListener(v -> save());
        col.addView(save, spaced());

        permission = new Button(this);
        permission.setText(R.string.setup_permission);
        permission.setTextSize(18);
        permission.setOnClickListener(v -> askOverlayPermission());
        col.addView(permission, spaced());

        message = text("", 18, Color.rgb(243, 196, 107));
        col.addView(message, spaced());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(15, 18, 24));
        scroll.addView(col);
        setContentView(scroll);
        url.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permission == null) return;
        boolean needed = !Launcher.canOpenFromBackground(this);
        permission.setVisibility(needed ? View.VISIBLE : View.GONE);
        if (needed) message.setText(getString(R.string.setup_permission_help, getPackageName()));
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 24;
        return lp;
    }

    private void save() {
        String value = url.getText().toString().trim();
        if (!Prefs.isValidUrl(value)) {
            message.setText(R.string.setup_invalid);
            return;
        }
        Prefs.save(this, value, autostart.isChecked());
        if (!autostart.isChecked()) Launcher.stopWakeService(this);
        openWelcome();
    }

    private void openWelcome() {
        Launcher.startWakeService(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void askOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            // Fire OS n'a pas toujours cet écran : il reste la commande adb affichée.
            message.setText(getString(R.string.setup_permission_help, getPackageName()));
        }
    }
}
