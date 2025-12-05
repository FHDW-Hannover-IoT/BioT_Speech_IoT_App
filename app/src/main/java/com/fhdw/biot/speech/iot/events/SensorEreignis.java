package com.fhdw.biot.speech.iot.events;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.fhdw.biot.speech.iot.R;
import com.fhdw.biot.speech.iot.main.MainActivity;

import database.entities.EreignisData;

public class SensorEreignis {
    private long timestamp;
    private String sensorType;
    private float value;
    private String id;
    private Context context;
    private char axis;

    public SensorEreignis(
            long timestamp, String sensorType, float value, String id, Context context, char axis) {
        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.value = value;
        this.id = id;
        this.context = context;
        this.axis = axis;

        // Notification
        int reqCode = 1;
        Intent intent = new Intent(this.context, MainActivity.class); // goto Home
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

    public EreignisData getEreignisData() {
        EreignisData ereignisData = new EreignisData();
        ereignisData.sensorType = this.sensorType;
        ereignisData.value = this.value;
        ereignisData.timestamp = this.timestamp;
        ereignisData.axis = this.axis;
        Log.d("CREATE_EREIGNIS_DATA", "creating EreignisData for: " + this.sensorType);
        return ereignisData;
    }

    public void showNotification(
            Context context, String title, String message, Intent intent, int reqCode) {
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        String CHANNEL_ID = "channel_name"; // The id of the channel.
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.outline_circle_notifications_24)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel Name"; // The user-visible name of the channel.
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(mChannel);
        }
        notificationManager.notify(
                reqCode,
                notificationBuilder.build()); // 0 is the request code, it should be unique id

        Log.d("showNotification", "showNotification: " + reqCode);
    }
}
