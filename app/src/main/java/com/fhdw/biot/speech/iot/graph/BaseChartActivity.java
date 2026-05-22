package com.fhdw.biot.speech.iot.graph;

import android.graphics.Color;
import com.fhdw.biot.speech.iot.config.BiotBaseActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;

/**
 * BaseChartActivity ----------------- WHY THIS CLASS EXISTS: Every sensor Activity (AccelActivity,
 * GyroActivity, MagnetActivity) uses: • the same MPAndroidChart styling • the same logic to insert
 * data into the chart • the same X-axis formatting rules
 *
 * <p>By placing the shared functionality here: → we avoid duplicated code → all sensor charts
 * maintain a consistent visual style → adding new sensors becomes trivial
 */
public abstract class BaseChartActivity extends BiotBaseActivity {

    /**
     * Configure the chart BEFORE inserting data.
     *
     * @param chart The LineChart being prepared.
     * @param label Title shown as chart description ("X-Achse", "Y-Achse", etc.).
     * @param startTime First sensor timestamp → allows converting X-axis values into seconds.
     */
    protected void setupChart(LineChart chart, String label, long startTime) {

        // Chart title
        Description description = new Description();
        description.setText(label);
        description.setTextColor(Color.WHITE);
        chart.setDescription(description);

        // Message displayed when no DB data is available yet.
        chart.setNoDataText("Lade Daten aus der Datenbank...");

        // Chart background
        chart.setBackgroundColor(Color.rgb(0, 0, 0));

        // User interaction: drag + pinch-to-zoom on both axes
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(false); // false = scale X/Y independently
        chart.setDoubleTapToZoomEnabled(true);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.getLegend().setTextColor(Color.WHITE);

        // X-axis styling
        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(Color.WHITE);

        // If we know the first timestamp → apply absolute time formatter
        if (startTime > 0) {
            applyAbsoluteXAxis(chart, startTime);
        }

        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(8f, 16f, 8f, 8f);

        // Y-axis styling + faint horizontal grid lines
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.argb(40, 255, 255, 255));  // ~15% white
        chart.getAxisLeft().setGridLineWidth(0.5f);
        chart.getAxisLeft().setAxisLineColor(Color.argb(80, 255, 255, 255));
        chart.getAxisLeft().setAxisLineWidth(0.5f);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawAxisLine(true);
        chart.getXAxis().setAxisLineColor(Color.argb(60, 255, 255, 255));

        // Custom renderer: draws a filled circle at the touched data point
        chart.setRenderer(
                new HighlightCircleRenderer(
                        chart, chart.getAnimator(), chart.getViewPortHandler()));

        // Trigger chart redraw
        chart.invalidate();
    }

    /**
     * Apply absolute HH:mm:ss X-axis labels with 5-minute granularity. X-values must be millisecond
     * offsets from {@code startTime}.
     */
    protected void applyAbsoluteXAxis(LineChart chart, long startTime) {
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new SecondsValueFormatter(startTime));
        xAxis.setGranularity(5 * 60 * 1000f);
        xAxis.setGranularityEnabled(true);
    }

    /**
     * Pin the visible X-axis range so the chart always shows the full selected time window,
     * even when data only covers part of it. Values are millisecond offsets from the chart's
     * startTime anchor (same coordinate space as the data entries).
     */
    protected void pinXAxisRange(LineChart chart, float minMs, float maxMs) {
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(minMs);
        xAxis.setAxisMaximum(maxMs);
    }

    /**
     * Insert a dataset into the chart.
     *
     * @param entries List of (x,y) pairs representing time vs sensor value.
     * @param label The line name inside the legend.
     * @param color Color of the line.
     */
    protected void setData(LineChart chart, ArrayList<Entry> entries, String label, int color) {

        // No data available → show message + clear chart.
        if (entries.isEmpty()) {
            chart.setNoDataText("Keine Daten in der Datenbank gefunden.");
            chart.clear();
            return;
        }

        // Convert points into a drawable dataset
        LineDataSet lineDataSet = new LineDataSet(entries, label);
        lineDataSet.setColor(color);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setDrawValues(false);
        lineDataSet.setLineWidth(1.8f);
        lineDataSet.setDrawFilled(true);
        lineDataSet.setFillAlpha(30);
        lineDataSet.setFillColor(color);
        lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        lineDataSet.setCubicIntensity(0.05f);
        GraphUtils.applyHighlightStyle(lineDataSet);

        // Wrap into LineData and submit to chart
        LineData lineData = new LineData(lineDataSet);
        chart.setData(lineData);

        // Redraw chart
        chart.invalidate();
    }
}
