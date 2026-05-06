package com.fhdw.biot.speech.iot.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BiotBaseActivity {

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

        // Home button
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(view ->
                startActivity(new Intent(SettingsActivity.this, MainActivity.class)));

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
}
