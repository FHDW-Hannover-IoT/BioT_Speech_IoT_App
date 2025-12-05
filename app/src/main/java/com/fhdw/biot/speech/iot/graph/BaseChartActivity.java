package com.fhdw.biot.speech.iot.graph;

import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
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
public abstract class BaseChartActivity extends AppCompatActivity {

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

        // X-axis styling
        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(Color.WHITE);

        // If we know the first timestamp → convert X values into seconds
        if (startTime > 0) {
            xAxis.setValueFormatter(new SecondsValueFormatter(startTime));
        }

        // Y-axis styling
        chart.getAxisLeft().setTextColor(Color.WHITE);

        // Trigger chart redraw
        chart.invalidate();
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
        lineDataSet.setDrawCircles(false); // smoother line, no points
        lineDataSet.setValueTextSize(10f);
        lineDataSet.setValueTextColor(Color.DKGRAY);

        // Wrap into LineData and submit to chart
        LineData lineData = new LineData(lineDataSet);
        chart.setData(lineData);

        // Redraw chart
        chart.invalidate();
    }
}
