package com.fhdw.biot.speech.iot;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;

public class SensorEreigniss {
    private long timestamp;
    private String sensorType;
    private float value;
    private String id;
    private Context context;

    public SensorEreigniss(long timestamp, String sensorType, float value, String id, Context context) {
        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.value = value;
        this.id = id;
        this.context = context;

        // Notifocation
        createNotification("title", "text");
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

    private void createNotification(String title, String text) {
        NotificationChannel channel =
                new NotificationChannel(
                        "eventChannel", "eventChannel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager =
                getSystemService(this.context, NotificationManager.class);

        // Create Notification Channel
        notificationManager.createNotificationChannel(channel);

        createNotification(title, text);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this.context)
                        .setSmallIcon(R.drawable.outline_circle_notifications_24)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setOngoing(true)
                        .setChannelId("eventChannel")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        var obj = mBuilder.build();
        System.out.println(channel);

        notificationManager.notify(1, obj);
    }
}
