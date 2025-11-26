package com.fhdw.biot.speech.iot;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class SensorEreigniss {
    private long timestamp;
    private String sensorType;
    private float value;
    private String id;
    private Context context;

    public SensorEreigniss(
            long timestamp, String sensorType, float value, String id, Context context) {
        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.value = value;
        this.id = id;
        this.context = context;

        // Notification
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSensorType() {
        return sensorType;
    }

    public float getValue() {
        return value;
    }

    public String getId() {
        return id;
    }

    public EreignisData getEreignisData(){
        EreignisData ereignisData = new EreignisData();
        ereignisData.sensorType = this.sensorType;
        ereignisData.value = this.value;
        ereignisData.timestamp = this.timestamp;
        Log.d("CREATE_EREIGNIS_DATA", "creating EreignisData for: " + this.sensorType);
        return ereignisData;
    }

    public void createNotification(String title, String text) {
        NotificationChannel channel =
                new NotificationChannel(
                        "eventChannel", "eventChannel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager =
                getSystemService(this.context, NotificationManager.class);

        // Create Notification Channel
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);

        createNotification(title, text);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this.context, "eventChannel")
                        .setSmallIcon(R.drawable.outline_circle_notifications_24)
                        .setContentTitle(title)
                        .setContentText(text)
                        //.setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        Log.d("channel", channel.getId());

        notificationManager.notify(1, mBuilder.build());
    }
}
