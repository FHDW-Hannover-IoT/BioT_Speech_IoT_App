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

public abstract class BaseChartActivity extends AppCompatActivity {

    protected void setupChart(LineChart chart, String label, long startTime) {
        Description description = new Description();
        description.setText(label);
        description.setTextColor(Color.WHITE);
        chart.setDescription(description);
        chart.setNoDataText("Lade Daten aus der Datenbank...");

        chart.setBackgroundColor(Color.rgb(0, 0, 0));

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextColor(Color.WHITE);

        if (startTime > 0) {
            xAxis.setValueFormatter(new SecondsValueFormatter(startTime));
        }

        chart.getAxisLeft().setTextColor(Color.WHITE);
        // chart.getAxisRight().setTextColor(Color.WHITE);
        // chart.getLegend().setTextColor(Color.WHITE);

        chart.invalidate();
    }

    protected void setData(LineChart chart, ArrayList<Entry> entries, String label, int color) {
        if (entries.isEmpty()) {
            chart.setNoDataText("Keine Daten in der Datenbank gefunden.");
            chart.clear();
            return;
        }

        LineDataSet lineDataSet = new LineDataSet(entries, label);
        lineDataSet.setColor(color);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setValueTextSize(10f);
        lineDataSet.setValueTextColor(Color.DKGRAY);

        LineData lineData = new LineData(lineDataSet);
        chart.setData(lineData);

        chart.invalidate();
    }
}
