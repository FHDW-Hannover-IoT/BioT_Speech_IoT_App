package com.fhdw.biot.speech.iot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MagnetActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_magnetfeld);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.magnet),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        Button buttonGyro = findViewById(R.id.btnPrevGyro);
        buttonGyro.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MagnetActivity.this, GyroActivity.class);
                        startActivity(intent);
                    }
                });

        Button buttonAccel = findViewById(R.id.btnNextAccel);
        buttonAccel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MagnetActivity.this, AccelActivity.class);
                        startActivity(intent);
                    }
                });
    }
}
