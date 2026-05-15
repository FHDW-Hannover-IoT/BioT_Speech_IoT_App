package com.fhdw.biot.speech.iot.events;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
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

    private static final String[] SENSOR_TYPES = {"ACCEL", "GYRO", "MAGNET"};

    private final List<EditableSensorEvent> eventList;
    long nextId = 0;

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

        // Populate sensor type spinner
        holder.spinnerSensorType.setOnItemSelectedListener(null);
        for (int i = 0; i < SENSOR_TYPES.length; i++) {
            if (SENSOR_TYPES[i].equalsIgnoreCase(currentEvent.sensorType)) {
                holder.spinnerSensorType.setSelection(i);
                break;
            }
        }

        // Set current selection without triggering listener
        holder.spinnerSensorType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        currentEvent.sensorType = SENSOR_TYPES[pos];
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

        holder.spinnerDirection.setOnItemSelectedListener(null);

        if (currentEvent.thresholdDirection != null) {
            ArrayAdapter<String> dirAdapter =
                    (ArrayAdapter<String>) holder.spinnerDirection.getAdapter();
            int dirPos = dirAdapter.getPosition(currentEvent.thresholdDirection);
            holder.spinnerDirection.setSelection(dirPos >= 0 ? dirPos : 0);
        }

        holder.spinnerDirection.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        currentEvent.thresholdDirection = parent.getItemAtPosition(pos).toString();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

        holder.checkX.setOnCheckedChangeListener(null);
        holder.checkX.setChecked(currentEvent.isAxisX());
        holder.checkX.setOnCheckedChangeListener(
                (btn, isChecked) -> currentEvent.setAxisX(isChecked));

        holder.checkY.setOnCheckedChangeListener(null);
        holder.checkY.setChecked(currentEvent.isAxisY());
        holder.checkY.setOnCheckedChangeListener(
                (btn, isChecked) -> currentEvent.setAxisY(isChecked));

        holder.checkZ.setOnCheckedChangeListener(null);
        holder.checkZ.setChecked(currentEvent.isAxisZ());
        holder.checkZ.setOnCheckedChangeListener(
                (btn, isChecked) -> currentEvent.setAxisZ(isChecked));

        holder.checkSum.setOnCheckedChangeListener(null);
        holder.checkSum.setChecked(currentEvent.isAxisSum());
        holder.checkSum.setOnCheckedChangeListener(
                (btn, isChecked) -> currentEvent.setAxisSum(isChecked));

        holder.eventType.removeTextChangedListener(holder.eventTypeWatcher);
        holder.eventType.setText(currentEvent.eventType);
        holder.eventTypeWatcher = simpleWatcher(text -> currentEvent.eventType = text);
        holder.eventType.addTextChangedListener(holder.eventTypeWatcher);

        holder.thresholdValue.removeTextChangedListener(holder.thresholdWatcher);
        holder.thresholdValue.setText(
                currentEvent.thresholdValue == 0
                        ? ""
                        : String.valueOf(currentEvent.thresholdValue));
        holder.thresholdWatcher =
                simpleWatcher(
                        text -> {
                            try {
                                currentEvent.thresholdValue = Float.parseFloat(text);
                            } catch (NumberFormatException ignored) {
                                currentEvent.thresholdValue = 0;
                            }
                        });
        holder.thresholdValue.addTextChangedListener(holder.thresholdWatcher);

        holder.btnDelete.setOnClickListener(
                v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) deleteEvent(pos);
                });
    }

    private static TextWatcher simpleWatcher(SimpleTextCallback cb) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                cb.onChanged(s.toString());
            }
        };
    }

    interface SimpleTextCallback {
        void onChanged(String text);
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

        eventList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, eventList.size());
    }

    /** ViewHolder for editable event configuration rows. */
    public static class EventViewHolder extends RecyclerView.ViewHolder {

        public ImageButton btnDelete;
        public Spinner spinnerSensorType;
        public EditText eventType;
        public EditText thresholdValue;

        public CheckBox checkX, checkY, checkZ, checkSum;
        public Spinner spinnerDirection;

        TextWatcher eventTypeWatcher;
        TextWatcher thresholdWatcher;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            btnDelete = itemView.findViewById(R.id.btn_delete_event);
            spinnerSensorType = itemView.findViewById(R.id.spinner_sensor_type);
            eventType = itemView.findViewById(R.id.spinner_event_type);
            thresholdValue = itemView.findViewById(R.id.et_threshold_value);

            checkX = itemView.findViewById(R.id.MagxCheck);
            checkY = itemView.findViewById(R.id.MagyCheck);
            checkZ = itemView.findViewById(R.id.MagzCheck);
            checkSum = itemView.findViewById(R.id.MagSumCheck);
            spinnerDirection = itemView.findViewById(R.id.spinner_direction);

            ArrayAdapter<String> sensorAdapter =
                    new ArrayAdapter<>(
                            itemView.getContext(), R.layout.custom_spinner_item, SENSOR_TYPES);
            sensorAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
            spinnerSensorType.setAdapter(sensorAdapter);

            String[] directions = {"<=", ">="};
            ArrayAdapter<String> directionAdapter =
                    new ArrayAdapter<>(
                            itemView.getContext(), R.layout.custom_spinner_item, directions);
            directionAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
            spinnerDirection.setAdapter(directionAdapter);
        }
    }
}
