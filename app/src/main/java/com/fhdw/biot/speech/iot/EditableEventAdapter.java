package com.fhdw.biot.speech.iot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class EditableEventAdapter
        extends RecyclerView.Adapter<EditableEventAdapter.EventViewHolder> {
    private final List<EditableSensorEvent> eventList;
    private long nextId = 0;

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

        // Daten aus der Datenbank eintragen; Selects befüllen

        holder.btnDelete.setOnClickListener(v -> deleteEvent(position));

        // *Listener für die Eingabefelder
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }

    public long addEmptyEvent() {
        long newId = ++nextId;
        EditableSensorEvent newEvent = new EditableSensorEvent(newId);
        eventList.add(newEvent);

        notifyItemInserted(eventList.size() - 1);
        return newId;
    }

    public void deleteEvent(int position) {
        if (position >= 0 && position < eventList.size()) {
            // Eintrag aus der Datenbank löschen

            eventList.remove(position);
            notifyItemRemoved(position);
        }
    }

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
