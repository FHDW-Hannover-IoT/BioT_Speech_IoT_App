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
import com.fhdw.biot.speech.iot.database.entities.EreignisType;
import com.fhdw.biot.speech.iot.database.entities.Sensor;
import com.fhdw.biot.speech.iot.main.MainActivity;
import com.fhdw.biot.speech.iot.repository.SensorRepository;
import java.util.ArrayList;
import java.util.List;

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

        loadExistingEvents();

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
        saveEventsToDatabase();
    }

    /** Lädt bereits gespeicherte Regeln aus der Room-Datenbank und zeigt sie in der Liste an. */
    @SuppressLint("NotifyDataSetChanged")
    private void loadExistingEvents() {
        new Thread(
                        () -> {
                            List<EreignisType> savedRules = sensorRepository.getAllEreignisTypes();

                            if (savedRules != null) {
                                List<EditableSensorEvent> loadedList = new ArrayList<>();

                                for (EreignisType dbRule : savedRules) {
                                    EditableSensorEvent e =
                                            new EditableSensorEvent(dbRule.ereignisID);

                                    e.eventType = dbRule.ereignisName;
                                    e.sensorType =
                                            dbRule.sensorType != null ? dbRule.sensorType : "ACCEL";
                                    e.thresholdValue = (float) dbRule.ereignisThreshold;
                                    e.thresholdDirection =
                                            dbRule.thresholdDirection != null
                                                    ? dbRule.thresholdDirection
                                                    : ">=";
                                    e.axisX = dbRule.axisX;
                                    e.axisY = dbRule.axisY;
                                    e.axisZ = dbRule.axisZ;
                                    e.axisSum = dbRule.axisSum;

                                    loadedList.add(e);
                                }

                                runOnUiThread(
                                        () -> {
                                            editableEventList.clear();
                                            editableEventList.addAll(loadedList);
                                            adapter.notifyDataSetChanged();
                                        });
                            }
                        })
                .start();
    }

    private void saveEventsToDatabase() {
        List<EditableSensorEvent> listToSave = new ArrayList<>(editableEventList);

        new Thread(
                        () -> {
                            sensorRepository.deleteAllEreignisTypes();

                            for (EditableSensorEvent editableEvent : listToSave) {
                                com.fhdw.biot.speech.iot.database.entities.EreignisType dbEvent =
                                        new com.fhdw.biot.speech.iot.database.entities
                                                .EreignisType();

                                dbEvent.ereignisName = editableEvent.eventType;
                                dbEvent.sensorType = editableEvent.sensorType;
                                dbEvent.ereignisThreshold = (int) editableEvent.thresholdValue;
                                dbEvent.thresholdDirection = editableEvent.thresholdDirection;

                                dbEvent.axisX = editableEvent.axisX;
                                dbEvent.axisY = editableEvent.axisY;
                                dbEvent.axisZ = editableEvent.axisZ;
                                dbEvent.axisSum = editableEvent.axisSum;

                                sensorRepository.insertEreignisType(dbEvent);
                            }

                            Log.i(TAG, "Saved " + listToSave.size() + " event rules to Room DB");
                        })
                .start();
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
