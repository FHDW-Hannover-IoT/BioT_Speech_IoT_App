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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.AppConfig;
import com.fhdw.biot.speech.iot.config.BiotApplication;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * SettingsActivity — lets the user configure the MQTT broker URL, push notifications, and
 * Douglas-Peucker graph reduction.
 *
 * <p>The broker URL field starts empty when no custom value has been saved; the hint shows the
 * active build.gradle IP so the user always knows what address is in use. A Save button appears
 * only when unsaved changes are detected. Saving validates input, persists to SharedPreferences (or
 * removes the override if the field is cleared), and reconnects MQTT immediately if the broker URL
 * changed.
 */
public class SettingsActivity extends BiotBaseActivity {

    private static final String PREF_NAME = "AppPreferences";

    private SwitchMaterial switchPushNotifications;
    private SwitchMaterial switchServerData;
    private EditText etMqttBrokerUrl;
    private EditText etLlmHost;
    private Button btnSave;

    // Values as they were when the screen opened — used to detect unsaved changes
    private String originalBrokerUrl; // null means "no custom override — using build.gradle"
    private String originalLlmHost;   // null means "no custom override — using build.gradle"
    private boolean originalPushActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchPushNotifications = findViewById(R.id.switch_push_notifications);
        switchServerData        = findViewById(R.id.switch_server_data);
        etMqttBrokerUrl         = findViewById(R.id.et_mqtt_broker_url);
        etLlmHost               = findViewById(R.id.et_llm_host);
        btnSave                 = findViewById(R.id.btn_save_settings);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        originalPushActive = prefs.getBoolean("PUSH_ACTIVE", true);
        // null = no custom override saved; field stays empty, hint shows the active default
        originalBrokerUrl  = prefs.getString("MQTT_BROKER", null);
        originalLlmHost    = prefs.getString("LLM_HOST", null);

        switchPushNotifications.setChecked(originalPushActive);
        switchServerData.setChecked(prefs.getBoolean("SERVER_DATA_ENABLED", true));
        switchServerData.setOnCheckedChangeListener((btn, enabled) -> {
            prefs.edit().putBoolean("SERVER_DATA_ENABLED", enabled).apply();
            if (enabled) {
                ((BiotApplication) getApplication()).getContainer().mcpDataSync().fetchInitial();
            }
        });

        if (originalBrokerUrl != null) {
            etMqttBrokerUrl.setText(originalBrokerUrl);
        }
        String defaultUrl = AppConfig.mqttBrokerUrl().replaceFirst("^tcp://", "");
        etMqttBrokerUrl.setHint(getString(R.string.settings_broker_url_hint_default, defaultUrl));

        if (originalLlmHost != null) {
            etLlmHost.setText(originalLlmHost);
        }
        String defaultLlmHost = AppConfig.isEmulator()
                ? com.fhdw.biot.speech.iot.BuildConfig.LLM_HOST_EMULATOR
                : com.fhdw.biot.speech.iot.BuildConfig.LLM_HOST_PHONE;
        etLlmHost.setHint(getString(R.string.settings_llm_host_hint_default, defaultLlmHost));

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        // Home button — navigates back without saving unsaved changes
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                v -> {
                    if (btnSave.getVisibility() == View.GONE) finish();
                    else {
                        LinearLayout notSavedPopUp =
                                findViewById(R.id.notification_unsaved_settings);
                        notSavedPopUp.setVisibility(View.VISIBLE);
                        Button btnDiscard = notSavedPopUp.findViewById(R.id.btn_discard_changes);
                        Button btnSave = notSavedPopUp.findViewById(R.id.btn_save_changes);
                        btnDiscard.setOnClickListener(v1 -> finish());
                        btnSave.setOnClickListener(
                                v1 -> {
                                    saveSettings();
                                    finish();
                                });
                    }
                });

        ImageButton btnInfoServer = findViewById(R.id.btn_info_server_data);
        btnInfoServer.setOnClickListener(
                v ->
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle(R.string.settings_server_info_title)
                                .setMessage(R.string.settings_server_info_message)
                                .setPositiveButton(
                                        R.string.settings_server_info_ok, (d, w) -> d.dismiss())
                                .setIcon(R.drawable.baseline_info_outline_24)
                                .show());

        etMqttBrokerUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSaveVisibility(); }
        });
        etLlmHost.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSaveVisibility(); }
        });
        switchPushNotifications.setOnCheckedChangeListener((btn, checked) -> updateSaveVisibility());

        btnSave.setOnClickListener(v -> saveSettings());

        // ── Douglas-Peucker section ──────────────────────────────────────────
        SwitchMaterial swActive = findViewById(R.id.switch_dp_active);
        SeekBar sbEpsilon = findViewById(R.id.seekbar_epsilon);
        TextView tvEpsilon = findViewById(R.id.tv_epsilon_value);

        SharedPreferences graphPrefs = getSharedPreferences("GraphSettings", MODE_PRIVATE);
        boolean wasEnabled = graphPrefs.getBoolean("dp_enabled", false);
        float savedEpsilon = graphPrefs.getFloat("dp_epsilon", 0.5f);

        swActive.setChecked(wasEnabled);
        sbEpsilon.setProgress((int) (savedEpsilon * 20));
        tvEpsilon.setText(
                getString(R.string.settings_dp_epsilon_label, String.valueOf(savedEpsilon)));

        swActive.setOnCheckedChangeListener(
                (btn, isChecked) -> {
                    graphPrefs.edit().putBoolean("dp_enabled", isChecked).apply();
                    if (isChecked && !wasEnabled)
                        graphPrefs.edit().putBoolean("dp_epsilon_manual", false).apply();
                });

        sbEpsilon.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float val = progress / 20f;
                        tvEpsilon.setText(
                                getString(R.string.settings_dp_epsilon_label, String.valueOf(val)));
                        if (fromUser) {
                            graphPrefs.edit().putFloat("dp_epsilon", val).apply();
                            graphPrefs.edit().putBoolean("dp_epsilon_manual", true).apply();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
    }

    /** Shows the Save button only when at least one field differs from its loaded value. */
    private void updateSaveVisibility() {
        String enteredBroker = etMqttBrokerUrl.getText().toString().trim();
        String enteredLlm    = etLlmHost.getText().toString().trim();
        boolean brokerChanged = !enteredBroker.equals(originalBrokerUrl == null ? "" : originalBrokerUrl);
        boolean llmChanged    = !enteredLlm.equals(originalLlmHost == null ? "" : originalLlmHost);
        boolean pushChanged   = switchPushNotifications.isChecked() != originalPushActive;
        btnSave.setVisibility((brokerChanged || llmChanged || pushChanged) ? View.VISIBLE : View.GONE);
    }

    /**
     * Validates the broker URL, persists all changes, triggers an MQTT reconnect if the URL
     * changed, shows a success toast, and closes the screen.
     */
    private void saveSettings() {
        String enteredBroker = etMqttBrokerUrl.getText().toString().trim();
        String enteredLlm    = etLlmHost.getText().toString().trim();

        // Auto-prepend tcp:// so users can type just "192.168.1.1:1883"
        if (!enteredBroker.isEmpty() && !enteredBroker.startsWith("tcp://") && !enteredBroker.startsWith("ssl://")) {
            enteredBroker = "tcp://" + enteredBroker;
            etMqttBrokerUrl.setText(enteredBroker);
        }

        if (!enteredBroker.isEmpty() && !isValidBrokerUrl(enteredBroker)) {
            Toast.makeText(this, getString(R.string.settings_broker_url_invalid), Toast.LENGTH_LONG)
                    .show();
            etMqttBrokerUrl.requestFocus();
            return;
        }

        SharedPreferences.Editor editor =
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();

        editor.putBoolean("PUSH_ACTIVE", switchPushNotifications.isChecked());
        if (enteredBroker.isEmpty()) {
            editor.remove("MQTT_BROKER");
        } else {
            editor.putString("MQTT_BROKER", enteredBroker);
        }
        if (enteredLlm.isEmpty()) {
            editor.remove("LLM_HOST");
        } else {
            editor.putString("LLM_HOST", enteredLlm);
        }
        editor.apply();

        ((BiotApplication) getApplication()).getContainer().refreshMcpHost(this);

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Returns true if the URL is a valid MQTT broker address. Accepts tcp:// and ssl:// schemes
     * with a non-empty host and a valid port number.
     */
    private boolean isValidBrokerUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!url.startsWith("tcp://") && !url.startsWith("ssl://")) return false;
        String hostPort = url.substring(url.indexOf("://") + 3);
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0) return false;
        String host = hostPort.substring(0, colonIdx);
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
