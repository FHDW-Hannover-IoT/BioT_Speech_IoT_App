package com.fhdw.biot.speech.iot;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MyEventAdapter extends RecyclerView.Adapter<MyEventAdapter.EventViewHolder> {

    private List<SensorEvent> eventList;

    public MyEventAdapter(List<SensorEvent> eventList) {
        this.eventList = eventList;
    }

    public static class EventViewHolder extends RecyclerView.ViewHolder {
        public TextView tvSensorType;
        public TextView tvTimestamp;
        public TextView tvValue;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSensorType = itemView.findViewById(R.id.tv_sensor_type);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvValue = itemView.findViewById(R.id.tv_value);
        }
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.event_list_item, parent, false);

        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        SensorEvent currentEvent = eventList.get(position);
        holder.tvSensorType.setText(currentEvent.getSensorType());
        holder.tvTimestamp.setText(String.valueOf(currentEvent.getTimestamp()));
        holder.tvValue.setText(String.valueOf(currentEvent.getValue()));
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }
}
