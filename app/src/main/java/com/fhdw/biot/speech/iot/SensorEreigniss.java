package com.fhdw.biot.speech.iot;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
        int reqCode = 1;
        Intent intent = new Intent(this.context, MainActivity.class);
        this.showNotification(this.context, "SensorEvent", "text", intent, reqCode);
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
    public void showNotification(Context context, String title, String message, Intent intent, int reqCode) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, reqCode, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        String CHANNEL_ID = "channel_name";// The id of the channel.
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_circle_notifications_24)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel Name";// The user-visible name of the channel.
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(mChannel);
        }
        notificationManager.notify(reqCode, notificationBuilder.build()); // 0 is the request code, it should be unique id

        Log.d("showNotification", "showNotification: " + reqCode);
    }
}
