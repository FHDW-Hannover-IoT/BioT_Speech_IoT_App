package com.fhdw.biot.speech.iot.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fhdw.biot.speech.iot.R;
import java.util.List;

/**
 * EditableEventAdapter --------------------- This adapter displays an EDITABLE list of event
 * configuration items.
 *
 * <p>Each row allows the user to define: • Sensor type (Accel / Gyro / Magnet via Spinner) • Event
 * type (custom rule name) • Threshold (numeric trigger level)
 *
 * <p>Used in NewEreignisActivity to build custom rules for generating EreignisData.
 *
 * <p>Note: Not all logic is implemented yet, but all fields + delete/add row functionality are in
 * place.
 */
public class EditableEventAdapter
        extends RecyclerView.Adapter<EditableEventAdapter.EventViewHolder> {

    private final List<EditableSensorEvent> eventList;
    private long nextId = 0; // Generates unique IDs for new rows

    public EditableEventAdapter(List<EditableSensorEvent> eventList) {
        this.eventList = eventList;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.event_configuration_item, parent, false);

        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {

        EditableSensorEvent currentEvent = eventList.get(position);

        // TODO: populate Spinner and input fields from DB or preset lists

        // Delete row handler
        holder.btnDelete.setOnClickListener(v -> deleteEvent(position));

        // TODO: Add listeners for text changes, spinner selection, etc.
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    /** Adds a new blank rule row. */
    public long addEmptyEvent() {
        long id = ++nextId;
        EditableSensorEvent newEvent = new EditableSensorEvent(id);
        eventList.add(newEvent);

        notifyItemInserted(eventList.size() - 1);
        return id;
    }

    /** Deletes a rule row (and later deletes DB entry). */
    public void deleteEvent(int position) {
        if (position < 0 || position >= eventList.size()) return;

        // TODO: Remove from DB if persistent

        eventList.remove(position);
        notifyItemRemoved(position);
    }

    /** ViewHolder for editable event configuration rows. */
    public static class EventViewHolder extends RecyclerView.ViewHolder {

        public ImageButton btnDelete;
        public Spinner spinnerSensorType;
        public EditText eventType;
        public EditText treshholdValue;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);

            btnDelete = itemView.findViewById(R.id.btn_delete_event);
            spinnerSensorType = itemView.findViewById(R.id.spinner_sensor_type);
            eventType = itemView.findViewById(R.id.spinner_event_type);
            treshholdValue = itemView.findViewById(R.id.et_threshold_value);
        }
    }
}
