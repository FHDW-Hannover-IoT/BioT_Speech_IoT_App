package com.fhdw.biot.speech.iot.events;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.config.BiotApplication;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * NewEreignisActivity -------------------- Screen where the user can define *event rules*
 * (EditableSensorEvent objects) that determine when a "SensorEreignis" (sensor event) should be
 * raised.
 *
 * <p>Conceptually: - EreignisActivity = shows ALREADY TRIGGERED events from DB. -
 * NewEreignisActivity = lets the user configure WHEN such events should be triggered in the future.
 *
 * <p>UI: - A RecyclerView with rows of type EditableSensorEvent: • sensorType (Accel / Gyro /
 * Magnet) • eventType (e.g. "Sturz", "Schock", "Schwelle überschritten") • threshold (float value)
 * - A "+" button that adds a new editable row. - Navigation buttons back to: • MainActivity (home)
 * • EreignisActivity (event overview)
 *
 * <p>Note: Currently, only the UI list is implemented. The persistence logic ("Datenbanklogik
 * hinzufügen") is still a TODO.
 */
public class NewEreignisActivity extends BiotBaseActivity {

    private static final String TAG = "NewEreignisActivity";
    private static final String PREFS_EVENTS = "EventRules";
    private static final String KEY_RULES = "rules";

    private RecyclerView recyclerView;
    private EditableEventAdapter adapter;
    private List<EditableSensorEvent> editableEventList;
    public List<Sensor> sensors = new ArrayList<>();
    private SensorRepository sensorRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_ereignis);
        sensorRepository = ((BiotApplication) getApplication()).getContainer().sensorRepository();
        loadAvailableSensors();

        // Apply system window insets so content doesn't overlap status/navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.new_ereignis),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        // --- Navigation: back to main dashboard (sensor live view) -----------
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(NewEreignisActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        // --- Navigation: back to event log screen ----------------------------
        ImageButton buttonEreignis = findViewById(R.id.notification_button);
        buttonEreignis.setOnClickListener(
                view -> {
                    Intent intent = new Intent(NewEreignisActivity.this, EreignisActivity.class);
                    startActivity(intent);
                });

        // --- RecyclerView setup ----------------------------------------------
        editableEventList = new ArrayList<>();

        recyclerView = findViewById(R.id.my_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new EditableEventAdapter(editableEventList);
        recyclerView.setAdapter(adapter);

        loadSavedRules();

        // --- Add new rule row (+) -------------------------------------------
        ImageButton addEreignis = findViewById(R.id.add_ereignis);
        addEreignis.setOnClickListener(
                view -> {
                    adapter.addEmptyEvent();
                    recyclerView.scrollToPosition(editableEventList.size() - 1);
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveRules();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadSavedRules() {
        String json = getSharedPreferences(PREFS_EVENTS, MODE_PRIVATE).getString(KEY_RULES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            editableEventList.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                EditableSensorEvent e = new EditableSensorEvent(++adapter.nextId);
                e.sensorType = obj.optString("sensorType", "ACCEL");
                e.eventType = obj.optString("eventType", "");
                e.thresholdValue = (float) obj.optDouble("threshold", 0.0);
                editableEventList.add(e);
            }
            adapter.notifyDataSetChanged();
        } catch (JSONException ex) {
            Log.w(TAG, "loadSavedRules parse error: " + ex.getMessage());
        }
    }

    private void saveRules() {
        JSONArray arr = new JSONArray();
        for (EditableSensorEvent e : editableEventList) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("sensorType", e.sensorType);
                obj.put("eventType", e.eventType);
                obj.put("threshold", e.thresholdValue);
                arr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        getSharedPreferences(PREFS_EVENTS, MODE_PRIVATE)
                .edit()
                .putString(KEY_RULES, arr.toString())
                .apply();
        Log.i(TAG, "Saved " + editableEventList.size() + " event rules");
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadAvailableSensors() {
        new Thread(
                        () -> {
                            List<Sensor> knownSensors = sensorRepository.getAllKnownSensors();
                            runOnUiThread(
                                    () -> {
                                        if (knownSensors != null) {
                                            sensors.clear();
                                            sensors.addAll(knownSensors);
                                        }
                                        if (adapter != null) adapter.notifyDataSetChanged();
                                    });
                        })
                .start();
    }
}
