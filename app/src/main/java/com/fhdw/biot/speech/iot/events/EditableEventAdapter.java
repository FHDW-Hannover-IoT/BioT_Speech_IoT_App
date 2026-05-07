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
 * <p>Each row allows the user to define: • Sensor type (Accel / Gyro / Magnet via Spinner)
 * • Axis (x / y / z /sum)
 * • Event type (custom rule name)
 * • Threshold (numeric trigger level)
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

        // Delete row handler
        holder.btnDelete.setOnClickListener(v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                deleteEvent(currentPosition);
            }
        });

        holder.checkX.setOnCheckedChangeListener(null);
        holder.checkX.setChecked(currentEvent.isAxisX());
        holder.checkX.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentEvent.setAxisX(isChecked);
        });

        holder.checkY.setOnCheckedChangeListener(null);
        holder.checkY.setChecked(currentEvent.isAxisY());
        holder.checkY.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentEvent.setAxisY(isChecked);
        });

        holder.checkZ.setOnCheckedChangeListener(null);
        holder.checkZ.setChecked(currentEvent.isAxisZ());
        holder.checkZ.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentEvent.setAxisZ(isChecked);
        });

        holder.checkSum.setOnCheckedChangeListener(null);
        holder.checkSum.setChecked(currentEvent.isAxisSum());
        holder.checkSum.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentEvent.setAxisSum(isChecked);
        });

        if (holder.eventTypeWatcher != null) {
            holder.eventType.removeTextChangedListener(holder.eventTypeWatcher);
        }
        holder.eventType.setText(currentEvent.eventType);
        holder.eventTypeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                currentEvent.eventType = s.toString();
            }
        };
        holder.eventType.addTextChangedListener(holder.eventTypeWatcher);

        if (holder.thresholdWatcher != null) {
            holder.treshholdValue.removeTextChangedListener(holder.thresholdWatcher);
        }
        holder.treshholdValue.setText(String.valueOf(currentEvent.thresholdValue));
        holder.thresholdWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    currentEvent.thresholdValue = Float.parseFloat(s.toString());
                } catch (NumberFormatException e) {
                    currentEvent.thresholdValue = 0f;
                }
            }
        };
        holder.treshholdValue.addTextChangedListener(holder.thresholdWatcher);

        holder.spinnerSensorType.setOnItemSelectedListener(null);
        ArrayAdapter<String> sensorAdapter = (ArrayAdapter<String>) holder.spinnerSensorType.getAdapter();
        if (sensorAdapter != null) {
            int spinnerPosition = sensorAdapter.getPosition(currentEvent.sensorType);
            holder.spinnerSensorType.setSelection(spinnerPosition);
        }
        holder.spinnerSensorType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                currentEvent.sensorType = parent.getItemAtPosition(pos).toString();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        holder.spinnerDirection.setOnItemSelectedListener(null);
        ArrayAdapter<String> directionAdapter = (ArrayAdapter<String>) holder.spinnerDirection.getAdapter();
        if (directionAdapter != null) {
            int spinnerPosition = directionAdapter.getPosition(currentEvent.thresholdDirection);
            holder.spinnerDirection.setSelection(spinnerPosition);
        }
        holder.spinnerDirection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                currentEvent.thresholdDirection = parent.getItemAtPosition(pos).toString();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // TODO: OnItemSelectedListener hinzufügen, um die Auswahl in currentEvent zu speichern
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
        notifyItemRangeChanged(position, eventList.size());
    }

    /** ViewHolder for editable event configuration rows. */
    public static class EventViewHolder extends RecyclerView.ViewHolder {

        public ImageButton btnDelete;
        public Spinner spinnerSensorType;
        public EditText eventType;
        public EditText treshholdValue;

        public CheckBox checkX;
        public CheckBox checkY;
        public CheckBox checkZ;
        public CheckBox checkSum;

        public Spinner spinnerDirection;

        public TextWatcher eventTypeWatcher;
        public TextWatcher thresholdWatcher;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);

            btnDelete = itemView.findViewById(R.id.btn_delete_event);
            spinnerSensorType = itemView.findViewById(R.id.spinner_sensor_type);
            eventType = itemView.findViewById(R.id.spinner_event_type);
            treshholdValue = itemView.findViewById(R.id.et_threshold_value);

            checkX = itemView.findViewById(R.id.MagxCheck);
            checkY = itemView.findViewById(R.id.MagyCheck);
            checkZ = itemView.findViewById(R.id.MagzCheck);
            checkSum = itemView.findViewById(R.id.MagSumCheck);
            spinnerDirection = itemView.findViewById(R.id.spinner_direction);

            String[] sensors = {"Accel", "Gyro", "Magnet"};
            ArrayAdapter<String> sensorAdapter = new ArrayAdapter<>(
                itemView.getContext(),
                R.layout.custom_spinner_item,
                sensors
            );

            sensorAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
            spinnerSensorType.setAdapter(sensorAdapter);

            String[] directions = {"<=", ">="};
            ArrayAdapter<String> directionAdapter = new ArrayAdapter<>(
                itemView.getContext(),
                R.layout.custom_spinner_item,
                directions
            );

            directionAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
            spinnerDirection.setAdapter(directionAdapter);
        }
    }
}
