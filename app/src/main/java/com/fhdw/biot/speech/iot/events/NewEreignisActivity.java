package com.fhdw.biot.speech.iot.events;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.main.MainActivity;
import database.DB;
import database.entities.Sensor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
public class NewEreignisActivity extends AppCompatActivity {

    // RecyclerView that displays the list of editable rules
    private RecyclerView recyclerView;

    // Adapter to bind EditableSensorEvent objects to row views
    private EditableEventAdapter adapter;

    // In-memory list of event-rule configurations
    private List<EditableSensorEvent> editableEventList;

    public List<Sensor> sensors = loadAvailableSensors();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_ereignis);

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
        // Backing list for the adapter; starts empty.
        editableEventList = new ArrayList<>();

        recyclerView = findViewById(R.id.my_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Adapter binds each EditableSensorEvent to event_configuration_item.xml
        adapter = new EditableEventAdapter(editableEventList);
        recyclerView.setAdapter(adapter);

        // TODO: Datenbanklogik hinzufügen
        //  - load existing rules from DB
        //  - populate editableEventList
        //  - adapter.notifyDataSetChanged()

        // --- Add new rule row (+) -------------------------------------------
        ImageButton addEreignis = findViewById(R.id.add_ereignis);
        addEreignis.setOnClickListener(
                view -> {
                    // Ask the adapter to append a new blank EditableSensorEvent
                    long newId = adapter.addEmptyEvent();

                    // Scroll RecyclerView to the last item so user sees the new row.
                    recyclerView.scrollToPosition(editableEventList.size() - 1);
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private List<Sensor> loadAvailableSensors() {
        AtomicReference<List<Sensor>> allEventsList = new AtomicReference<>();
        DB.databaseWriteExecutor.execute(
                () -> {
                    allEventsList.set(
                            DB.getDatabase(getApplicationContext())
                                    .sensorDao()
                                    .getAllKnownSensors());

                    runOnUiThread(
                            () -> {
                                if (allEventsList.get() == null) {
                                    Log.e("ERROR", "Event list returned null!");
                                    return;
                                }

                                adapter.notifyDataSetChanged();
                            });
                });
        return allEventsList.get();
    }
}
