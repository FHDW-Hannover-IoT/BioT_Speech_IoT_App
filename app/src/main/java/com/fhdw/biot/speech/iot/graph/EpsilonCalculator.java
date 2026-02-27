package com.fhdw.biot.speech.iot.graph;

import android.content.Context;
import android.content.SharedPreferences;
import com.fhdw.biot.speech.iot.sensor.SensorPoint;
import java.util.List;

/**
 * EpsilonCalculator --------- Utility class for calculating the epsilon value for the
 * Douglas-Peucker algorithm. The epsilon can be either: 1. User-customized (via Settings SeekBar)
 * 2. Dynamically computed based on the average of all graph values (magnitudes of sensor vectors)
 *
 * <p>PURPOSE: The Douglas-Peucker algorithm needs a threshold (epsilon) to determine whether a
 * curve segment is "flat enough" to simplify. Instead of using a fixed value, we compute it based
 * on the actual data distribution. This makes the algorithm adaptive to different sensor scales.
 *
 * <p>STRATEGY: 1. Check if user has customized epsilon in Settings. 2. If yes, use that value. 3.
 * If no, calculate the default from average magnitude.
 */
public class EpsilonCalculator {

    /**
     * Calculate the epsilon value for enabled algorithm.
     *
     * <p>BEHAVIOR: 1. If user manually changed value → use that value 2. If first time enabled →
     * calculate automatically from data, save it, use it 3. If new data with auto mode → keep using
     * previously calculated value
     *
     * <p>NOTE: This method is only called when the algorithm is ENABLED. When disabled,
     * MainGraphActivity doesn't call this and uses raw data.
     *
     * @param context Android context (for accessing SharedPreferences).
     * @param dataPoints List of sensor data points (AccelData, GyroData, MagnetData, etc.)
     * @return The epsilon value to use for Douglas-Peucker.
     */
    public static float calculateEpsilon(Context context, List<? extends SensorPoint> dataPoints) {
        if (context != null) {
            SharedPreferences prefs =
                    context.getSharedPreferences("GraphSettings", Context.MODE_PRIVATE);

            // Check if user manually changed the epsilon value
            boolean isManual = prefs.getBoolean("dp_epsilon_manual", false);
            float savedEpsilon = prefs.getFloat("dp_epsilon", -1.0f);

            // If user manually set a value, always use it
            if (isManual && savedEpsilon > 0) {
                return savedEpsilon;
            }

            // If not manual, calculate fresh and save it
            float calculatedEpsilon = calculateDefaultEpsilon(dataPoints);
            prefs.edit().putFloat("dp_epsilon", calculatedEpsilon).apply();
            return calculatedEpsilon;
        }

        // Fallback if context is null
        return calculateDefaultEpsilon(dataPoints);
    }

    /**
     * Calculate the default epsilon value based on the average magnitude of all sensor points.
     *
     * @param dataPoints List of sensor data points (AccelData, GyroData, MagnetData, etc.)
     * @return The calculated epsilon value (a float). Returns 1.0f if the list is empty or has
     *     fewer than 2 points.
     */
    private static float calculateDefaultEpsilon(List<? extends SensorPoint> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return 1.0f; // Fallback for empty or minimal data
        }

        // Calculate the magnitude for each data point and sum them
        float sumMagnitudes = 0.0f;
        for (SensorPoint point : dataPoints) {
            float magnitude = calculateMagnitude(point);
            sumMagnitudes += magnitude;
        }

        // Calculate the average magnitude
        float averageMagnitude = sumMagnitudes / dataPoints.size();

        // Use 7% of the average as the epsilon value
        // This is a reasonable default that works well across different sensor scales.
        // Users can adjust this value through the UI.
        float epsilon = averageMagnitude * 0.07f;

        // Ensure epsilon is at least 0.1f to avoid too aggressive simplification
        return Math.max(epsilon, 0.1f);
    }

    /**
     * Helper method: calculate the magnitude (Euclidean norm) of a 3D sensor vector.
     *
     * @param point A sensor data point with X, Y, Z components.
     * @return The magnitude: sqrt(x² + y² + z²)
     */
    private static float calculateMagnitude(SensorPoint point) {
        float x = point.getX();
        float y = point.getY();
        float z = point.getZ();
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
