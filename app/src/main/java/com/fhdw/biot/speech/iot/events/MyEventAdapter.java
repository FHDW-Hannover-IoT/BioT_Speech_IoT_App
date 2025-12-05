package com.fhdw.biot.speech.iot.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fhdw.biot.speech.iot.R;

import java.util.Date;
import java.util.List;

import database.entities.EreignisData;

/**
 * MyEventAdapter
 * ----------------
 * This RecyclerView.Adapter renders a list of EreignisData (event entries)
 * into the event_list_item layout.
 *
 * Each event row shows:
 *   • Sensor type  (ACCEL / GYRO / MAGNET)
 *   • Timestamp    (converted to readable Date)
 *   • Value        (the detected threshold-exceeding sensor value)
 *   • Axis         (X / Y / Z)
 *
 * The adapter itself is intentionally very lightweight:
 *   - All logic is handled inside EreignisActivity (sorting/filtering).
 *   - The adapter only displays the data given to it.
 */
public class MyEventAdapter extends RecyclerView.Adapter<MyEventAdapter.EventViewHolder> {

    private List<EreignisData> eventList;  // Data provided by EreignisActivity

    public MyEventAdapter(List<EreignisData> eventList) {
        this.eventList = eventList;
    }

    /**
     * ViewHolder: Holds references to the UI elements inside a single row.
     */
    public static class EventViewHolder extends RecyclerView.ViewHolder {
        public TextView tvSensorType;
        public TextView tvTimestamp;
        public TextView tvValue;
        public TextView tvAxis;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);

            tvSensorType = itemView.findViewById(R.id.tv_sensor_type);
            tvTimestamp  = itemView.findViewById(R.id.tv_timestamp);
            tvValue      = itemView.findViewById(R.id.tv_value);
            tvAxis       = itemView.findViewById(R.id.tv_axis);
        }
    }

    /**
     * Inflates one row of the RecyclerView (event_list_item.xml).
     */
    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.event_list_item, parent, false);

        return new EventViewHolder(view);
    }

    /**
     * Binds one EreignisData item to UI fields.
     */
    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EreignisData currentEvent = eventList.get(position);

        // Type → ACCEL, GYRO, MAGNET
        holder.tvSensorType.setText(currentEvent.sensorType);

        // Convert UNIX timestamp to readable date
        holder.tvTimestamp.setText(String.valueOf(new Date(currentEvent.timestamp)));

        // Sensor value causing the event
        holder.tvValue.setText(String.valueOf(currentEvent.value));

        // Axis of the threshold detection
        holder.tvAxis.setText("In " + currentEvent.axis + "-Richtung");
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }
}
