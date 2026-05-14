package com.fhdw.biot.speech.iot.graph;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.renderer.LineChartRenderer;
import com.github.mikephil.charting.utils.MPPointD;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/** Extends the default line renderer to draw a filled circle at each highlighted data point. */
public class HighlightCircleRenderer extends LineChartRenderer {

    private static final float OUTER_DP = 6f;
    private static final float INNER_DP = 3f;
    private static final float STROKE_DP = 1.5f;
    private static final int BG_COLOR = Color.rgb(10, 10, 20);

    private final Paint mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HighlightCircleRenderer(
            LineChart chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
    }

    @Override
    public void drawHighlighted(Canvas c, Highlight[] indices) {
        super.drawHighlighted(c, indices);

        LineData lineData = mChart.getLineData();
        if (lineData == null) return;

        float outerR = Utils.convertDpToPixel(OUTER_DP);
        float innerR = Utils.convertDpToPixel(INNER_DP);
        float strokeW = Utils.convertDpToPixel(STROKE_DP);

        for (Highlight high : indices) {
            ILineDataSet set = lineData.getDataSetByIndex(high.getDataSetIndex());
            if (set == null || !set.isHighlightEnabled()) continue;

            Entry e = set.getEntryForXValue(high.getX(), high.getY());
            if (e == null || !isInBoundsX(e, set)) continue;

            MPPointD pix =
                    mChart.getTransformer(set.getAxisDependency())
                            .getPixelForValues(e.getX(), e.getY() * mAnimator.getPhaseY());

            float cx = (float) pix.x;
            float cy = (float) pix.y;

            // Filled outer circle in the dataset's line color
            mCirclePaint.setStyle(Paint.Style.FILL);
            mCirclePaint.setColor(set.getColor());
            c.drawCircle(cx, cy, outerR, mCirclePaint);

            // Dark inner fill so the circle looks like a donut
            mCirclePaint.setColor(BG_COLOR);
            c.drawCircle(cx, cy, innerR, mCirclePaint);

            // White stroke ring
            mCirclePaint.setStyle(Paint.Style.STROKE);
            mCirclePaint.setColor(Color.WHITE);
            mCirclePaint.setStrokeWidth(strokeW);
            c.drawCircle(cx, cy, outerR, mCirclePaint);
        }
    }
}
