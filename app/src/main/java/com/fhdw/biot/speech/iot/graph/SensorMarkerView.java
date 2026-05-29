package com.fhdw.biot.speech.iot.graph;

import android.content.Context;
import android.widget.TextView;
import com.fhdw.biot.speech.iot.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SensorMarkerView extends MarkerView {

    private final String unit;
    private final TextView tvValue;
    private final TextView tvDate;
    private final TextView tvTime;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH);
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    public SensorMarkerView(Context context, String unit) {
        super(context, R.layout.marker_sensor);
        this.unit = unit;
        tvValue = findViewById(R.id.tv_marker_value);
        tvDate = findViewById(R.id.tv_marker_date);
        tvTime = findViewById(R.id.tv_marker_time);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        long absMs = resolveAbsoluteTimestamp(e);
        Date date = new Date(absMs);

        String axisLabel = resolveAxisLabel(highlight);
        tvValue.setText(String.format(Locale.ENGLISH, "%.3f %s [%s]", e.getY(), unit, axisLabel));
        tvDate.setText(DATE_FMT.format(date));
        tvTime.setText(TIME_FMT.format(date));

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Center horizontally on the point, float above it
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 12);
    }

    private long resolveAbsoluteTimestamp(Entry e) {
        if (!(getChartView() instanceof LineChart)) return 0;
        XAxis xAxis = ((LineChart) getChartView()).getXAxis();
        if (xAxis.getValueFormatter() instanceof SecondsValueFormatter) {
            return ((SecondsValueFormatter) xAxis.getValueFormatter()).getStartTime()
                    + (long) e.getX();
        }
        return 0;
    }

    private String resolveAxisLabel(Highlight highlight) {
        if (!(getChartView() instanceof LineChart)) return "";
        LineData ld = ((LineChart) getChartView()).getData();
        if (ld == null) return "";
        int idx = highlight.getDataSetIndex();
        if (idx < 0 || idx >= ld.getDataSetCount()) return "";
        return ld.getDataSetByIndex(idx).getLabel();
    }
}
