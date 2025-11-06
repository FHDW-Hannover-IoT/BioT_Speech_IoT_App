package com.fhdw.biot.speech.iot;

import com.github.mikephil.charting.formatter.ValueFormatter;

public class SecondsValueFormatter extends ValueFormatter {
    private long startTime;

    public SecondsValueFormatter(long startTime) {
        this.startTime = startTime;
    }

    @Override
    public String getFormattedValue(float value) {
        long millis = startTime + (long) value;
        return String.format("%.2f", (float) (millis - startTime) / 1000.0f) + "s";
    }
}
