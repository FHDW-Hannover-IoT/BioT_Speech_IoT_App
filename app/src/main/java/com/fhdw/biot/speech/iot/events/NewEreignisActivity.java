package com.fhdw.biot.speech.iot.events;

import android.content.Intent;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;

public class NewEreignisActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private EditableEventAdapter adapter;
    private List<EditableSensorEvent> editableEventList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_ereignis);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.new_ereignis),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        ImageButton buttonHome = findViewById(R.id.home_button);
        buttonHome.setOnClickListener(
                view -> {
                    Intent intent = new Intent(NewEreignisActivity.this, MainActivity.class);
                    startActivity(intent);
                });

        ImageButton buttonEreignis = findViewById(R.id.notification_button);
        buttonEreignis.setOnClickListener(
                view -> {
                    Intent intent = new Intent(NewEreignisActivity.this, EreignisActivity.class);
                    startActivity(intent);
                });

        editableEventList = new ArrayList<>();
        recyclerView = findViewById(R.id.my_table_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EditableEventAdapter(editableEventList);
        recyclerView.setAdapter(adapter);

        // Datenbanklogik hinzufügen

        ImageButton addEreignis = findViewById(R.id.add_ereignis);
        addEreignis.setOnClickListener(
                view -> {
                    long newId = adapter.addEmptyEvent();
                    recyclerView.scrollToPosition(editableEventList.size() - 1);
                });
    }
}
