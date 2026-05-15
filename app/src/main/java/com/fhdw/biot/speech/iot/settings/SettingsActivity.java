package com.fhdw.biot.speech.iot.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.AppConfig;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * SettingsActivity — lets the user configure the MQTT broker URL, push notifications, and
 * Douglas-Peucker graph reduction.
 *
 * <p>The broker URL field starts empty when no custom value has been saved; the hint shows the
 * active build.gradle IP so the user always knows what address is in use. A Save button appears
 * only when unsaved changes are detected. Saving validates input, persists to SharedPreferences
 * (or removes the override if the field is cleared), and reconnects MQTT immediately if the
 * broker URL changed.
 */
public class SettingsActivity extends BiotBaseActivity {

    private static final String PREF_NAME = "AppPreferences";

    private SwitchMaterial switchPushNotifications;
    private EditText etMqttBrokerUrl;
    private Button btnSave;

    // Values as they were when the screen opened — used to detect unsaved changes
    private String originalBrokerUrl; // null means "no custom override — using build.gradle"
    private boolean originalPushActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchPushNotifications = findViewById(R.id.switch_push_notifications);
        etMqttBrokerUrl         = findViewById(R.id.et_mqtt_broker_url);
        btnSave                 = findViewById(R.id.btn_save_settings);
        ScrollView scrollView   = findViewById(R.id.settings_scroll);

        // Scroll the broker URL field above the keyboard when it gains focus.
        etMqttBrokerUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) scrollView.post(() -> scrollView.requestChildFocus(etMqttBrokerUrl, etMqttBrokerUrl));
        });

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        originalPushActive = prefs.getBoolean("PUSH_ACTIVE", true);
        // null = no custom override saved; field stays empty, hint shows the active default
        originalBrokerUrl  = prefs.getString("MQTT_BROKER", null);

        switchPushNotifications.setChecked(originalPushActive);

        if (originalBrokerUrl != null) {
            // User previously saved a custom URL — pre-fill so they can edit it
            etMqttBrokerUrl.setText(originalBrokerUrl);
        }
        // Hint shows the active default without tcp:// prefix since it's added automatically
        String defaultUrl = AppConfig.mqttBrokerUrl().replaceFirst("^tcp://", "");
        etMqttBrokerUrl.setHint(getString(R.string.settings_broker_url_hint_default, defaultUrl));

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        // Home button — navigates back without saving unsaved changes
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(v -> finish());

        ImageButton btnInfoServer = findViewById(R.id.btn_info_server_data);
        btnInfoServer.setOnClickListener(
                v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.settings_server_info_title)
                        .setMessage(R.string.settings_server_info_message)
                        .setPositiveButton(R.string.settings_server_info_ok, (d, w) -> d.dismiss())
                        .setIcon(R.drawable.baseline_info_outline_24)
                        .show());

        etMqttBrokerUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSaveVisibility(); }
        });
        switchPushNotifications.setOnCheckedChangeListener((btn, checked) -> updateSaveVisibility());

        btnSave.setOnClickListener(v -> saveSettings());

        // ── Douglas-Peucker section ──────────────────────────────────────────
        SwitchMaterial swActive  = findViewById(R.id.switch_dp_active);
        SeekBar sbEpsilon        = findViewById(R.id.seekbar_epsilon);
        TextView tvEpsilon       = findViewById(R.id.tv_epsilon_value);

        SharedPreferences graphPrefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);
        boolean wasEnabled = graphPrefs.getBoolean("dp_enabled", false);
        float savedEpsilon = graphPrefs.getFloat("dp_epsilon", 0.5f);

        swActive.setChecked(wasEnabled);
        sbEpsilon.setProgress((int) (savedEpsilon * 20));
        tvEpsilon.setText(getString(R.string.settings_dp_epsilon_label, String.valueOf(savedEpsilon)));

        swActive.setOnCheckedChangeListener(
                (btn, isChecked) -> {
                    graphPrefs.edit().putBoolean("dp_enabled", isChecked).apply();
                    if (isChecked && !wasEnabled)
                        graphPrefs.edit().putBoolean("dp_epsilon_manual", false).apply();
                });

        sbEpsilon.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 20f;
                tvEpsilon.setText(getString(R.string.settings_dp_epsilon_label, String.valueOf(val)));
                if (fromUser) {
                    graphPrefs.edit().putFloat("dp_epsilon", val).apply();
                    graphPrefs.edit().putBoolean("dp_epsilon_manual", true).apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /** Shows the Save button only when at least one field differs from its loaded value. */
    private void updateSaveVisibility() {
        String entered = etMqttBrokerUrl.getText().toString().trim();
        // A change means: the entered text differs from what was loaded (treating null as "")
        boolean brokerChanged = !entered.equals(originalBrokerUrl == null ? "" : originalBrokerUrl);
        boolean pushChanged   = switchPushNotifications.isChecked() != originalPushActive;
        btnSave.setVisibility((brokerChanged || pushChanged) ? View.VISIBLE : View.GONE);
    }

    /**
     * Validates the broker URL, persists all changes, triggers an MQTT reconnect if the URL
     * changed, shows a success toast, and closes the screen.
     */
    private void saveSettings() {
        String entered = etMqttBrokerUrl.getText().toString().trim();

        // Auto-prepend tcp:// so users can type just "192.168.1.1:1883"
        if (!entered.isEmpty() && !entered.startsWith("tcp://") && !entered.startsWith("ssl://")) {
            entered = "tcp://" + entered;
            etMqttBrokerUrl.setText(entered);
        }

        // Empty field = remove custom override so build.gradle default takes over
        if (!entered.isEmpty() && !isValidBrokerUrl(entered)) {
            Toast.makeText(this, getString(R.string.settings_broker_url_invalid), Toast.LENGTH_LONG)
                    .show();
            etMqttBrokerUrl.requestFocus();
            return;
        }

        SharedPreferences.Editor editor =
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();

        editor.putBoolean("PUSH_ACTIVE", switchPushNotifications.isChecked());
        if (entered.isEmpty()) {
            editor.remove("MQTT_BROKER"); // revert to build.gradle default
        } else {
            editor.putString("MQTT_BROKER", entered);
        }
        editor.apply();

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Returns true if the URL is a valid MQTT broker address.
     * Accepts tcp:// and ssl:// schemes with a non-empty host and a valid port number.
     */
    private boolean isValidBrokerUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!url.startsWith("tcp://") && !url.startsWith("ssl://")) return false;
        String hostPort = url.substring(url.indexOf("://") + 3);
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0) return false;
        String host    = hostPort.substring(0, colonIdx);
        String portStr = hostPort.substring(colonIdx + 1);
        if (host.isEmpty()) return false;
        try {
            int port = Integer.parseInt(portStr);
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
