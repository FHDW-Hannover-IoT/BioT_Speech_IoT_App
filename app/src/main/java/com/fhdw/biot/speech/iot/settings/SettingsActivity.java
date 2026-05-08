package com.fhdw.biot.speech.iot.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Ensure content is not hidden under system bars (status/navigation).
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // Home button: return to main values / MQTT screen
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        SwitchMaterial swActive = findViewById(R.id.switch_dp_active);
        SeekBar sbEpsilon = findViewById(R.id.seekbar_epsilon);
        TextView tvEpsilon = findViewById(R.id.tv_epsilon_value);

        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);

        boolean wasEnabled = prefs.getBoolean("dp_enabled", false);
        swActive.setChecked(wasEnabled);

        float savedEpsilon = prefs.getFloat("dp_epsilon", 0.5f);
        sbEpsilon.setProgress((int) (savedEpsilon * 20));
        tvEpsilon.setText("Epsilon (Schwellenwert): " + savedEpsilon);

        swActive.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    prefs.edit().putBoolean("dp_enabled", isChecked).apply();

                    // When user enables algorithm for first time, reset manual flag
                    // so epsilon gets auto-calculated from next data load
                    if (isChecked && !wasEnabled) {
                        prefs.edit().putBoolean("dp_epsilon_manual", false).apply();
                    }
                });

        sbEpsilon.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float val = progress / 20f; // Wandelt 0-100 in 0.0-5.0 um
                        tvEpsilon.setText("Epsilon (Schwellenwert): " + val);

                        // Only save if user manually moved the slider (fromUser=true)
                        // Ignore programmatic updates from initialization
                        if (fromUser) {
                            prefs.edit().putFloat("dp_epsilon", val).apply();
                            // Mark that user manually changed epsilon
                            prefs.edit().putBoolean("dp_epsilon_manual", true).apply();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
    }
}
