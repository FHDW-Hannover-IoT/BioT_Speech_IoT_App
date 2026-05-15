package com.fhdw.biot.speech.iot.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.AppConfig;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends BiotBaseActivity {

    private SwitchMaterial switchPushNotifications;
    private EditText etMqttBrokerUrl;

    private static final String PREF_NAME = "AppPreferences";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchPushNotifications = findViewById(R.id.switch_push_notifications);
        etMqttBrokerUrl = findViewById(R.id.et_mqtt_broker_url);

        SharedPreferences sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        boolean isPushActive = sharedPref.getBoolean("PUSH_ACTIVE", true);
        // Only populate the field if the user previously saved a custom value.
        // When null, the field stays empty and the hint shows the active BuildConfig IP,
        // making it clear what address is currently in use.
        String customBroker = sharedPref.getString("MQTT_BROKER", null);

        switchPushNotifications.setChecked(isPushActive);
        if (customBroker != null) etMqttBrokerUrl.setText(customBroker);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // Home button — finish() returns to the existing back-stack entry (MainActivity or
        // MainGraphActivity) without creating a new instance, preventing duplicate MQTT init.
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(view -> finish());

        ImageButton btnInfoServer = findViewById(R.id.btn_info_server_data);
        btnInfoServer.setOnClickListener(
                v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.settings_server_info_title)
                        .setMessage(R.string.settings_server_info_message)
                        .setPositiveButton(R.string.settings_server_info_ok, (d, w) -> d.dismiss())
                        .setIcon(R.drawable.baseline_info_outline_24)
                        .show());

        // ── Douglas-Peucker section ──────────────────────────────────────────
        SwitchMaterial swActive = findViewById(R.id.switch_dp_active);
        SeekBar sbEpsilon = findViewById(R.id.seekbar_epsilon);
        TextView tvEpsilon = findViewById(R.id.tv_epsilon_value);

        SharedPreferences prefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);

        boolean wasEnabled = prefs.getBoolean("dp_enabled", false);
        float savedEpsilon = prefs.getFloat("dp_epsilon", 0.5f);

        swActive.setChecked(wasEnabled);
        sbEpsilon.setProgress((int) (savedEpsilon * 20));
        tvEpsilon.setText(
                getString(R.string.settings_dp_epsilon_label, String.valueOf(savedEpsilon)));

        swActive.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    prefs.edit().putBoolean("dp_enabled", isChecked).apply();
                    if (isChecked && !wasEnabled) {
                        prefs.edit().putBoolean("dp_epsilon_manual", false).apply();
                    }
                });

        sbEpsilon.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float val = progress / 20f;
                        tvEpsilon.setText(
                                getString(R.string.settings_dp_epsilon_label, String.valueOf(val)));
                        if (fromUser) {
                            prefs.edit().putFloat("dp_epsilon", val).apply();
                            prefs.edit().putBoolean("dp_epsilon_manual", true).apply();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putBoolean("PUSH_ACTIVE", switchPushNotifications.isChecked());

        String entered = etMqttBrokerUrl.getText().toString().trim();
        if (entered.isEmpty() || entered.equals(AppConfig.mqttBrokerUrl())) {
            // User cleared the field or left the default — remove override so BuildConfig wins.
            editor.remove("MQTT_BROKER");
        } else {
            editor.putString("MQTT_BROKER", entered);
        }

        editor.apply();
    }
}
