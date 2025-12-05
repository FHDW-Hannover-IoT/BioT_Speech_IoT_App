package com.fhdw.biot.speech.iot.events;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

import database.DB;
import database.entities.EreignisData;

/**
 * EreignisActivity
 * ----------------
 * Displays ALL previously detected events stored in the Room database.
 *
 * Features:
 *   ✔ Displays full list of events (ACCEL, GYRO, MAGNET)
 *   ✔ Supports filtering based on originating screen (e.g., Accel → only ACCEL events)
 *   ✔ Supports sorting by:
 *        - Sensor type
 *        - Event type (future field)
 *        - Value (threshold-exceeding)
 *        - Timestamp (time of detection)
 *   ✔ Includes button to create new event configurations via NewEreignisActivity
 *
 * This screen is essentially the "Event Log" of the entire system.
 */
public class EreignisActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MyEventAdapter adapter;

    private List<EreignisData> filteredEvents = new ArrayList<>();

    private TextView headerTextView;
    private TextView btnSortSensor, btnSortType, btnSortValue, btnSortTimestamp;

    private boolean isSortAscending = true;
    private String currentSortKey = "TIMESTAMP"; // Default sort: newest first

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ereignisse);

        // Handle safe area insets
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.ereignisse),
                (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
                    return insets;
                });

        // Home button → navigate back to main sensor dashboard
        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(v ->
                startActivity(new Intent(EreignisActivity.this, MainActivity.class)));

        // Create new event configuration
        ImageButton editEreignisseButton = findViewById(R.id.new_event);
        editEreignisseButton.setOnClickListener(v ->
                startActivity(new Intent(EreignisActivity.this, NewEreignisActivity.class)));

        // Sorting controls
        btnSortSensor    = findViewById(R.id.btn_sort_sensor);
        btnSortType      = findViewById(R.id.btn_sort_type);
        btnSortValue     = findViewById(R.id.btn_sort_value);
        btnSortTimestamp = findViewById(R.id.btn_sort_timestamp);

        btnSortSensor.setOnClickListener(v -> sortEvents("SENSOR"));
        btnSortType.setOnClickListener(v -> sortEvents("TYPE"));
        btnSortValue.setOnClickListener(v -> sortEvents("VALUE"));
        btnSortTimestamp.setOnClickListener(v -> sortEvents("TIMESTAMP"));

        // RecyclerView setup
        recyclerView = findViewById(R.id.my_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MyEventAdapter(filteredEvents);
        recyclerView.setAdapter(adapter);

        headerTextView = findViewById(R.id.headerText);

        // Filter passed by intent (Accel, Gyro, Magnet, or "ALL")
        String sensorFilter = getIntent().getStringExtra("SENSOR_FILTER");
        if (sensorFilter == null) sensorFilter = "ALL";

        loadEventData(sensorFilter);
    }

    /**
     * Loads event data from ROOM → optionally filtered by sensor category.
     */
    private void loadEventData(String filter) {
        DB.databaseWriteExecutor.execute(() -> {

            List<EreignisData> allEventsList =
                    DB.getDatabase(getApplicationContext())
                            .sensorDao()
                            .getAllEreignisData();

            runOnUiThread(() -> {
                if (allEventsList == null) {
                    Log.e("ERROR", "Event list returned null!");
                    return;
                }

                filteredEvents.clear();

                if ("ALL".equals(filter)) {
                    filteredEvents.addAll(allEventsList);
                    headerTextView.setText("Ereignisse");
                } else {
                    // Filter by sensor type
                    for (EreignisData event : allEventsList) {
                        if (event.sensorType.equals(filter)) {
                            filteredEvents.add(event);
                        }
                    }

                    // Adjust screen header text
                    switch (filter) {
                        case "ACCEL": headerTextView.setText("Ereignisse Beschleunigung"); break;
                        case "MAGNET": headerTextView.setText("Ereignisse Magnetfeld"); break;
                        case "GYRO": headerTextView.setText("Ereignisse Gyroskop"); break;
                        default: headerTextView.setText("Ereignisse");
                    }
                }

                adapter.notifyDataSetChanged();
            });
        });
    }

    /**
     * Sorts events based on column clicked by user.
     */
    private void sortEvents(String sortKey) {

        // Toggle between ascending / descending if same column clicked twice
        if (currentSortKey.equals(sortKey)) {
            isSortAscending = !isSortAscending;
        } else {
            isSortAscending = true;
            currentSortKey = sortKey;
        }

        filteredEvents.sort((e1, e2) -> {
            int cmp = 0;

            switch (sortKey) {
                case "TYPE":
                    cmp = e1.getSensorType().compareTo(e2.getSensorType());
                    break;

                case "VALUE":
                    cmp = Float.compare(e1.getValue(), e2.getValue());
                    break;

                case "TIMESTAMP":
                    cmp = Long.compare(e1.getTimestamp(), e2.getTimestamp());
                    break;
            }

            return isSortAscending ? cmp : -cmp;
        });

        adapter.notifyDataSetChanged();
    }
}
