package com.fhdw.biot.speech.iot.settings;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.fhdw.biot.speech.iot.config.LanguageManager;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BiotBaseActivity {

    private Dialog loadingDialog;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // Home button — FLAG_ACTIVITY_CLEAR_TOP brings the existing MainActivity to
        // the front instead of creating a second instance on top of the stack.
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(view -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // ── Language selector ────────────────────────────────────────────────
        RadioGroup rgLanguage = findViewById(R.id.rg_language);

        // Pre-select the currently saved language
        switch (LanguageManager.getCode(this)) {
            case "en": rgLanguage.check(R.id.rb_lang_en); break;
            case "zh": rgLanguage.check(R.id.rb_lang_zh); break;
            default:   rgLanguage.check(R.id.rb_lang_de); break;
        }

        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String code;
            if      (checkedId == R.id.rb_lang_en) code = "en";
            else if (checkedId == R.id.rb_lang_zh) code = "zh";
            else                                    code = "de";

            if (!code.equals(LanguageManager.getCode(this))) {
                LanguageManager.save(this, code);
                showLanguageLoadingAndRestart();
            }
        });

        // ── Douglas-Peucker section ──────────────────────────────────────────
        SwitchMaterial swActive  = findViewById(R.id.switch_dp_active);
        SeekBar        sbEpsilon = findViewById(R.id.seekbar_epsilon);
        TextView       tvEpsilon = findViewById(R.id.tv_epsilon_value);

        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);

        boolean wasEnabled    = prefs.getBoolean("dp_enabled", false);
        float   savedEpsilon  = prefs.getFloat("dp_epsilon", 0.5f);

        swActive.setChecked(wasEnabled);
        sbEpsilon.setProgress((int) (savedEpsilon * 20));
        tvEpsilon.setText(getString(R.string.settings_dp_epsilon_label, String.valueOf(savedEpsilon)));

        swActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dp_enabled", isChecked).apply();
            if (isChecked && !wasEnabled) {
                prefs.edit().putBoolean("dp_epsilon_manual", false).apply();
            }
        });

        sbEpsilon.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 20f;
                tvEpsilon.setText(getString(R.string.settings_dp_epsilon_label, String.valueOf(val)));
                if (fromUser) {
                    prefs.edit().putFloat("dp_epsilon", val).apply();
                    prefs.edit().putBoolean("dp_epsilon_manual", true).apply();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar)  {}
        });
    }

    private void showLanguageLoadingAndRestart() {
        loadingDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        loadingDialog.setContentView(R.layout.dialog_language_loading);
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        handler.postDelayed(() -> {
            if (loadingDialog != null) {
                loadingDialog.dismiss();
                loadingDialog = null;
            }
            // Restart the entire task so every Activity picks up the new locale
            // via BiotBaseActivity.attachBaseContext(). recreate() only fixes the
            // current Activity — all other Activities in the back stack keep the
            // old locale until they are destroyed and recreated.
            Intent restart = new Intent(this, MainActivity.class);
            restart.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restart);
        }, 5000);
    }
}
