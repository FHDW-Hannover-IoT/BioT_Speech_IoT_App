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
     * Reduces {@code list} to the minimum set of points that preserves the visual shape
     * of the line within {@code epsilon} tolerance.
     *
     * <p>Internally uses squared distances throughout to avoid {@code Math.sqrt} in the
     * inner loop — {@code epsilon} is squared once here so callers pass the natural unit.
     *
     * @param list    Full list of data points (at least 3; smaller lists are returned as-is).
     * @param epsilon Tolerance threshold in sensor units (higher = more aggressive simplification).
     */
    public static <T extends SensorPoint> List<T> simplify(List<T> list, float epsilon) {
        if (list == null || list.size() < 3) return list;
        // Square epsilon once so the recursive inner loop only compares squared distances,
        // eliminating sqrt calls from the O(n log n) hot path.
        return dp(list, 0, list.size() - 1, epsilon * epsilon);
    }

    /**
     * Recursive Douglas–Peucker split. Operates entirely in squared-distance space so
     * no {@code Math.sqrt} or {@code Math.abs} is needed in the hot inner loop.
     *
     * @param pts       All points (never modified)
     * @param start     Index of first point in the current segment
     * @param end       Index of last point in the current segment
     * @param epsilonSq Pre-squared tolerance (epsilon²)
     */
    private static <T extends SensorPoint> List<T> dp(
            List<T> pts, int start, int end, float epsilonSq) {

        float maxDistSq = 0f;
        int indexOfFarthest = -1;

        T first = pts.get(start);
        T last = pts.get(end);

        // Find the point most distant from the baseline
        for (int i = start + 1; i < end; i++) {
            float distSq = perpendicularDistanceSq(pts.get(i), first, last);
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                indexOfFarthest = i;
            }
        }

        // If the farthest point exceeds tolerance → split in two segments
        if (maxDistSq > epsilonSq) {

            List<T> left = dp(pts, start, indexOfFarthest, epsilonSq);
            List<T> right = dp(pts, indexOfFarthest, end, epsilonSq);

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
     * Returns the SQUARED perpendicular distance of point {@code p} from line AB.
     *
     * <p>Avoids {@code Math.sqrt} and {@code Math.abs} — safe because DP only compares
     * distances against a threshold, never needs the actual distance value. The squared
     * result is compared against {@code epsilonSq} (also squared) so the comparison is valid.
     *
     * <p>X-axis = timestamp, Y-axis = Euclidean magnitude of the (x,y,z) sensor vector.
     */
    private static float perpendicularDistanceSq(SensorPoint p, SensorPoint a, SensorPoint b) {
        float x = p.getTimestamp();
        float y = magnitude(p);

        float x1 = a.getTimestamp();
        float y1 = magnitude(a);

        float x2 = b.getTimestamp();
        float y2 = magnitude(b);

        float dx = x2 - x1;
        float dy = y2 - y1;

        float denomSq = dx * dx + dy * dy;
        if (denomSq == 0) {
            float ddx = x - x1;
            float ddy = y - y1;
            return ddx * ddx + ddy * ddy;
        }

        float n = dy * x - dx * y + x2 * y1 - y2 * x1;
        return (n * n) / denomSq;
    }

    /** Magnitude of a 3D acceleration/gyro/magnet vector. sqrt(x² + y² + z²) */
    private static float magnitude(SensorPoint p) {
        return (float) Math.sqrt(p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ());
    }
}
