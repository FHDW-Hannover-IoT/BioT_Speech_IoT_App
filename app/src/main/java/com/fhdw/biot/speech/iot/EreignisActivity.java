package com.fhdw.biot.speech.iot;

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
import java.util.ArrayList;
import java.util.List;
import database.DB;
import database.entities.EreignisData;

public class EreignisActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private MyEventAdapter adapter;
    private List<EreignisData> filteredEvents = new ArrayList<>();
    private TextView headerTextView;
    private TextView btnSortSensor, btnSortType, btnSortValue, btnSortTimestamp;
    private boolean isSortAscending = true;
    private String currentSortKey = "TIMESTAMP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ereignisse);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.ereignisse),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(EreignisActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        ImageButton editEreignisseButton = findViewById(R.id.new_event);
        editEreignisseButton.setOnClickListener(
                view -> {
                    Intent intent = new Intent(EreignisActivity.this, NewEreignisActivity.class);
                    startActivity(intent);
                });
        btnSortSensor = findViewById(R.id.btn_sort_sensor);
        btnSortType = findViewById(R.id.btn_sort_type);
        btnSortValue = findViewById(R.id.btn_sort_value);
        btnSortTimestamp = findViewById(R.id.btn_sort_timestamp);

        btnSortSensor.setOnClickListener(v -> sortEvents("SENSOR"));
        btnSortType.setOnClickListener(v -> sortEvents("TYPE"));
        btnSortValue.setOnClickListener(v -> sortEvents("VALUE"));
        btnSortTimestamp.setOnClickListener(v -> sortEvents("TIMESTAMP"));

        recyclerView = findViewById(R.id.my_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MyEventAdapter(filteredEvents);
        recyclerView.setAdapter(adapter);

        headerTextView = findViewById(R.id.headerText);

        String sensorFilter = getIntent().getStringExtra("SENSOR_FILTER");
        if (sensorFilter == null) {
            sensorFilter = "ALL";
        }
        loadEventData(sensorFilter);
    }

    private void loadEventData(String filter) {
        // Daten aus Datenbank holen ...
        DB.databaseWriteExecutor.execute(
                () -> {
                    List<EreignisData> allEventsList =
                            DB.getDatabase(getApplicationContext())
                                    .sensorDao()
                                    .getAllEreignisData();

                    runOnUiThread(
                            () -> {
                                if (allEventsList != null) {
                                    if ("ALL".equals(filter)) {
                                        filteredEvents.addAll(allEventsList);
                                        headerTextView.setText("Ereignisse");
                                    } else {
                                        for (EreignisData event : allEventsList) {
                                            if (event.sensorType.equals(filter)) {
                                                filteredEvents.add(event);
                                            }
                                        }
                                        switch (filter) {
                                            case "ACCEL":
                                                headerTextView.setText("Ereignisse Beschleunigung");
                                                break;
                                            case "MAGNET":
                                                headerTextView.setText("Ereignisse Magnetfeld");
                                                break;
                                            case "GYRO":
                                                headerTextView.setText("Ereignisse Gyroskop");
                                                break;
                                            default:
                                                headerTextView.setText("Ereignisse");
                                        }
                                    }
                                    adapter.notifyDataSetChanged();
                                } else {
                                    Log.e("ERROR", "allEvents is null");
                                }
                            });
                });
    }

    private void sortEvents(String sortKey) {
        if (currentSortKey.equals(sortKey)) {
            isSortAscending = !isSortAscending;
        } else {
            currentSortKey = sortKey;
            isSortAscending = true;
        }

        filteredEvents.sort(
                (e1, e2) -> {
                    int comparisonResult = 0;
                    switch (sortKey) {
                        case "TYPE":
                            comparisonResult = e1.getSensorType().compareTo(e2.getSensorType());
                            break;
                        case "VALUE":
                            comparisonResult = Float.compare(e1.getValue(), e2.getValue());
                            break;
                        case "TIMESTAMP":
                            comparisonResult = Long.compare(e1.getTimestamp(), e2.getTimestamp());
                            break;
                    }
                    return isSortAscending ? comparisonResult : -comparisonResult;
                });

        adapter.notifyDataSetChanged();
    }
}
