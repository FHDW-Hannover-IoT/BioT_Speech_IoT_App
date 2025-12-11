package com.fhdw.biot.speech.iot.graph;

import com.github.mikephil.charting.formatter.ValueFormatter;

/**
 * SecondsValueFormatter --------------------- This formatter is attached to the X-axis of charts
 * that plot sensor data.
 *
 * <p>WHY THIS CLASS EXISTS: • MPAndroidChart normally displays raw X-axis values as floats. • In
 * your graphs, the X-axis represents **elapsed time since first data point**, in milliseconds. •
 * This formatter converts those milliseconds into **seconds**, so the charts read like: 0.00s 1.42s
 * 3.50s 5.87s ...
 *
 * <p>HOW IT WORKS: • The "value" passed in is the difference: (currentTimestamp - startTimestamp) •
 * We divide by 1000 to convert to seconds. • We attach an "s" to indicate seconds.
 */
public class SecondsValueFormatter extends ValueFormatter {

    /**
     * The timestamp (in millis) of the very first data point. All other timestamps are interpreted
     * as offsets from this point.
     */
    private long startTime;

    public SecondsValueFormatter(long startTime) {
        this.startTime = startTime;
    }

    @Override
    public String getFormattedValue(float value) {
        // Convert the offset (value) into a total timestamp
        long millis = startTime + (long) value;

        // Convert difference into seconds (float precision)
        float seconds = (millis - startTime) / 1000.0f;

        // Format to two decimals → "1.42s"
        return String.format("%.2f", seconds) + "s";
    }
}
