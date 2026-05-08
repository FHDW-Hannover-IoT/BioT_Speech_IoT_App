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

/**
 * SensorEreignis --------------- Represents a *single triggered event* in memory.
 *
 * <p>Responsibilities: 1. Holds the event information: - timestamp (when the condition was met) -
 * sensorType (ACCEL, GYRO, MAGNET, ...) - value (sensor value that caused the event) - id
 * (identifier / correlation id) - axis (X, Y, or Z)
 *
 * <p>2. Creates a matching EreignisData object for persistence in the Room database.
 *
 * <p>3. Immediately shows a notification in the Android status bar to inform the user that an event
 * has occurred.
 *
 * <p>Typical usage: - Some logic (e.g., in MainActivity or a future rule engine) detects: "Accel X
 * > threshold" → event triggered - It creates a new SensorEreignis(...) - The constructor: → stores
 * the fields → triggers showNotification() - The caller then uses getEreignisData() to insert into
 * DB.
 */
public class SensorEreignis {

    // When did the event happen (ms since epoch)?
    private long timestamp;

    // Which sensor triggered the event? e.g. "ACCEL", "GYRO", "MAGNET"
    private String sensorType;

    // Actual sensor reading that exceeded the threshold
    private float value;

    // Optional identifier; can be used to correlate rules or sources.
    private String id;

    // Android Context, required for notification + DB etc.
    private Context context;

    // Axis along which the event occurred (e.g., 'X', 'Y', 'Z')
    private char axis;

    /**
     * Builds a new SensorEreignis and directly shows a notification.
     *
     * @param timestamp Time of event (usually System.currentTimeMillis()).
     * @param sensorType Logical sensor ID, e.g. "ACCEL", "GYRO", ...
     * @param value The sensor reading that triggered the event.
     * @param id Arbitrary identifier for this event.
     * @param context Android Context used for notifications.
     * @param axis 'X', 'Y', or 'Z' depending on which axis exceeded threshold.
     */
    public SensorEreignis(
            long timestamp, String sensorType, float value, String id, Context context, char axis) {

        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.value = value;
        this.id = id;
        this.context = context;
        this.axis = axis;

        // Immediately show a notification to the user when the event is created.
        // NOTE: Currently title and text are generic ("SensorEvent", "text").
        //       You may want to adapt this to display sensorType/value/axis.
        int reqCode = 1;
        Intent intent = new Intent(this.context, MainActivity.class); // Go to Home when tapped
        this.showNotification(this.context, "SensorEvent", "text", intent, reqCode);
    }

    // --- Simple getters for further usage -----------------------------------

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

    /**
     * Creates a EreignisData entity filled with the current event data. This object can be inserted
     * into the Room database.
     *
     * @return EreignisData instance mirroring this SensorEreignis.
     */
    public EreignisData getEreignisData() {
        EreignisData ereignisData = new EreignisData();
        ereignisData.sensorType = this.sensorType;
        ereignisData.value = this.value;
        ereignisData.timestamp = this.timestamp;
        ereignisData.axis = this.axis;

        Log.d("CREATE_EREIGNIS_DATA", "creating EreignisData for: " + this.sensorType);

        return ereignisData;
    }

    /**
     * Builds and shows an Android notification to inform the user that a sensor event has occurred.
     *
     * @param context Context used to access NotificationManager.
     * @param title Title text of the notification.
     * @param message Body text of the notification.
     * @param intent Intent launched when the user taps the notification.
     * @param reqCode Request code to differentiate PendingIntents / notifications.
     */
    public void showNotification(
            Context context, String title, String message, Intent intent, int reqCode) {

        // Wrap the target activity (MainActivity) into a PendingIntent
        // so that it can be launched from the notification.
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Static channel ID used by this app. On Android 8+, notifications
        // must be associated with a channel.
        String CHANNEL_ID = "channel_name";

        // Build the visual notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.outline_circle_notifications_24)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true) // Dismiss when tapped
                        .setContentIntent(pendingIntent); // Open MainActivity on tap

        // Get system service for posting the notification
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // For Android 8.0+ (Oreo): we must register a NotificationChannel first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel Name"; // Shown in Android notification settings
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        // Finally, post the notification.
        // reqCode acts as the notification ID; if reused, it will update the existing one.
        notificationManager.notify(reqCode, notificationBuilder.build());

        Log.d("showNotification", "showNotification: " + reqCode);
    }
}
