package com.fhdw.biot.speech.iot.graph;

import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    @Override
    public String getFormattedValue(float value) {
        long absMs = startTime + (long) value;
        return TIME_FORMAT.format(new Date(absMs));
    }
}
