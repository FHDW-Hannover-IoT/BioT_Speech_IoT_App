package com.fhdw.biot.speech.iot.graph;

import com.fhdw.biot.speech.iot.sensor.SensorPoint;

import java.util.ArrayList;
import java.util.List;

public class DouglasPeukerAlg {

    public static <T extends SensorPoint> List<T> simplify(List<T> list, float epsilon) {
        if (list == null || list.size() < 3) return list;

        return dp(list, 0, list.size() - 1, epsilon);
    }

    private static <T extends SensorPoint> List<T> dp(
            List<T> pts, int start, int end, float epsilon) {
        float dmax = 0f;
        int idx = -1;

        T first = pts.get(start);
        T last = pts.get(end);

        for (int i = start + 1; i < end; i++) {
            float d = perpendicularDistance(pts.get(i), first, last);
            if (d > dmax) {
                dmax = d;
                idx = i;
            }
        }

        if (dmax > epsilon) {
            List<T> rec1 = dp(pts, start, idx, epsilon);
            List<T> rec2 = dp(pts, idx, end, epsilon);

            List<T> out = new ArrayList<>(rec1);
            out.remove(out.size() - 1);
            out.addAll(rec2);

            return out;

        } else {
            List<T> out = new ArrayList<>();
            out.add(first);
            out.add(last);
            return out;
        }
    }

    private static float perpendicularDistance(SensorPoint p, SensorPoint a, SensorPoint b) {

        float x = (float) p.getTimestamp();
        float y = magnitude(p);

        float x1 = (float) a.getTimestamp();
        float y1 = magnitude(a);

        float x2 = (float) b.getTimestamp();
        float y2 = magnitude(b);

        float dx = x2 - x1;
        float dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            dx = x - x1;
            dy = y - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        float numerator = Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1);
        float denominator = (float) Math.sqrt(dx * dx + dy * dy);

        return numerator / denominator;
    }

    private static float magnitude(SensorPoint p) {
        return (float) Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ());
    }
}
