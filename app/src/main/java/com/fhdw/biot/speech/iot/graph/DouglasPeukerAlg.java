package com.fhdw.biot.speech.iot.graph;

import com.fhdw.biot.speech.iot.sensor.SensorPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * DouglasPeukerAlg ---------------- PURPOSE: The Douglas–Peucker algorithm reduces the number of
 * points in a line while preserving the overall shape.
 *
 * <p>WHY THIS IS IMPORTANT IN YOUR APP: • Sensor data can easily reach thousands of points. •
 * Plotting too many points slows down charts and UI rendering. • This algorithm keeps important
 * shape points and removes noise.
 *
 * <p>HOW IT WORKS (simplified): 1. Draw a line between the first and last data point. 2. Find the
 * point that is *furthest away* from that line. 3. If that distance > epsilon (threshold) → keep
 * splitting recursively. 4. Else: the entire section is considered "flat enough" → reduce to 2
 * points.
 *
 * <p>GENERIC TYPE T: The algorithm supports any class implementing SensorPoint (AccelData,
 * GyroData, MagnetData, custom sensor types, etc.)
 */
public class DouglasPeukerAlg {

    /**
     * Public entry point.
     *
     * @param list Full list of data points.
     * @param epsilon Tolerance threshold (higher = more aggressive simplification).
     */
    public static <T extends SensorPoint> List<T> simplify(List<T> list, float epsilon) {
        if (list == null || list.size() < 3) return list; // too few points to simplify
        return dp(list, 0, list.size() - 1, epsilon);
    }

    /**
     * Recursive Douglas–Peucker implementation.
     *
     * @param pts All points
     * @param start Index of first point in segment
     * @param end Index of last point in segment
     * @param epsilon Threshhold for keeping detail
     */
    private static <T extends SensorPoint> List<T> dp(
            List<T> pts, int start, int end, float epsilon) {

        float maxDistance = 0f;
        int indexOfFarthest = -1;

        T first = pts.get(start);
        T last = pts.get(end);

        // Find the point most distant from the baseline
        for (int i = start + 1; i < end; i++) {
            float dist = perpendicularDistance(pts.get(i), first, last);
            if (dist > maxDistance) {
                maxDistance = dist;
                indexOfFarthest = i;
            }
        }

        // If the farthest point exceeds tolerance → split in two segments
        if (maxDistance > epsilon) {

            List<T> left = dp(pts, start, indexOfFarthest, epsilon);
            List<T> right = dp(pts, indexOfFarthest, end, epsilon);

            // Combine results: include all from left except last,
            // then include all from right.
            List<T> combined = new ArrayList<>(left);
            combined.remove(combined.size() - 1);
            combined.addAll(right);

            return combined;

        } else {
            // Segment is flat → reduce to endpoints
            List<T> out = new ArrayList<>();
            out.add(first);
            out.add(last);
            return out;
        }
    }

    /**
     * Compute perpendicular distance of point p from line AB.
     *
     * <p>X-axis = timestamp. Y-axis = magnitude of (x,y,z vector).
     */
    private static float perpendicularDistance(SensorPoint p, SensorPoint a, SensorPoint b) {

        // Convert sensor point to graph space
        float x = p.getTimestamp();
        float y = magnitude(p);

        float x1 = a.getTimestamp();
        float y1 = magnitude(a);

        float x2 = b.getTimestamp();
        float y2 = magnitude(b);

        float dx = x2 - x1;
        float dy = y2 - y1;

        // If both points are identical → fallback to point distance
        if (dx == 0 && dy == 0) {
            dx = x - x1;
            dy = y - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        // Standard perpendicular distance formula
        float numerator = Math.abs(dy * x - dx * y + x2 * y1 - y2 * x1);
        float denominator = (float) Math.sqrt(dx * dx + dy * dy);

        return numerator / denominator;
    }

    /** Magnitude of a 3D acceleration/gyro/magnet vector. sqrt(x² + y² + z²) */
    private static float magnitude(SensorPoint p) {
        return (float) Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ());
    }
}
