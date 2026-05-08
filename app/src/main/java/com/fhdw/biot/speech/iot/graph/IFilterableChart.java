package com.fhdw.biot.speech.iot.graph;

/**
 * IFilterableChart
 * ─────────────────────────────────────────────────────────────────────────────
 * Contract that every chart Activity must fulfil so that the voice command
 * system (and later the LLM) can apply time filters without knowing which
 * specific sensor screen is currently open.
 *
 * Why this interface exists
 * ─────────────────────────
 * {@link com.fhdw.biot.speech.iot.sensor.AccelActivity},
 * {@link com.fhdw.biot.speech.iot.sensor.GyroActivity}, and
 * {@link com.fhdw.biot.speech.iot.sensor.MagnetActivity} all implement the
 * same "last N minutes" sliding-window filter independently.
 *
 * By having them all implement {@code IFilterableChart}, the voice executor
 * and broadcast receiver can call {@code applyTimeFilter(minutes)} on whatever
 * Activity is currently in the foreground — without a chain of {@code instanceof}
 * checks.
 *
 * Implementations
 * ───────────────
 *  • AccelActivity
 *  • GyroActivity
 *  • MagnetActivity
 *  • MainGraphActivity  (optional — applies filter to all three charts at once)
 */
public interface IFilterableChart {

    /**
     * Apply a sliding time-window filter to the chart data.
     *
     * @param minutes Window size in minutes.
     *                Pass {@code 0} to clear all active filters and show all data.
     *                Common values: 5, 10, 30, 60, 1440 (24 h).
     */
    void applyTimeFilter(int minutes);

    /**
     * Clear any active time filter and reload all available data.
     * Equivalent to calling {@link #applyTimeFilter(int)} with {@code 0},
     * but provided as a named method for clarity at call sites.
     */
    void clearFilter();

    /**
     * Returns {@code true} if a time filter is currently active on this chart.
     * Useful for the voice system to give contextual feedback ("Filter ist aktiv").
     */
    boolean isFilterActive();
}