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

public class GyroActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    EdgeToEdge.enable(this);
    setContentView(R.layout.activity_gyroskop);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.gyro),
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
          return insets;
        });

    Button buttonAccel = findViewById(R.id.btnPrevAccel);
    buttonAccel.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Intent intent = new Intent(GyroActivity.this, AccelActivity.class);
            startActivity(intent);
          }
        });

    Button buttonMagnet = findViewById(R.id.btnNextMagnet);
    buttonMagnet.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Intent intent = new Intent(GyroActivity.this, MagnetActivity.class);
            startActivity(intent);
          }
        });
  }
}
