package com.fhdw.biot.speech.iot.graph;

import android.graphics.Color;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Static utilities for MPAndroidChart data preparation.
 *
 * <p>buildSegmented: splits a flat entry list into multiple LineDataSets at "gap" positions (where
 * the inter-sample interval exceeds 3× the median). Each segment becomes its own dataset so
 * MPAndroidChart does not draw a bridging line across periods with no data (e.g. offline windows,
 * paused sensors).
 */
public final class GraphUtils {

    private GraphUtils() {}

    /**
     * @param entries Pre-built entries where entry.getX() is elapsed-ms from the first sample.
     * @param label Dataset label (shown in the legend).
     * @param color Line colour.
     * @return One or more LineDataSets, each styled identically, covering contiguous data segments.
     */
    public static List<LineDataSet> buildSegmented(
            ArrayList<Entry> entries, String label, int color) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        if (entries.size() == 1)
            return Collections.singletonList(makeDataSet(entries, label, color));

        float threshold = computeGapThreshold(entries);

        List<LineDataSet> result = new ArrayList<>();
        int segStart = 0;

        for (int i = 0; i < entries.size() - 1; i++) {
            float gap = entries.get(i + 1).getX() - entries.get(i).getX();
            if (gap > threshold) {
                result.add(makeDataSet(entries.subList(segStart, i + 1), label, color));
                segStart = i + 1;
            }
        }
        result.add(makeDataSet(entries.subList(segStart, entries.size()), label, color));
        return result;
    }

    private static float computeGapThreshold(List<Entry> entries) {
        float[] diffs = new float[entries.size() - 1];
        for (int i = 0; i < diffs.length; i++) {
            diffs[i] = entries.get(i + 1).getX() - entries.get(i).getX();
        }

        float[] sorted = diffs.clone();
        Arrays.sort(sorted);
        float median =
                sorted.length % 2 == 0
                        ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2f
                        : sorted[sorted.length / 2];

        // Guard against degenerate data (duplicate timestamps → median = 0).
        if (median < 1f) median = 1000f;

        return 3f * median;
    }

    private static LineDataSet makeDataSet(List<Entry> entries, String label, int color) {
        LineDataSet ds = new LineDataSet(new ArrayList<>(entries), label);
        ds.setColor(color);
        ds.setDrawCircles(false);
        ds.setValueTextSize(10f);
        ds.setValueTextColor(Color.DKGRAY);
        return ds;
    }
}
