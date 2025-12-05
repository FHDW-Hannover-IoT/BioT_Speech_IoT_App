package com.fhdw.biot.speech.iot.util;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * DatePickerHandler
 * ------------------
 * A small helper utility that attaches a DatePickerDialog to any Button.
 *
 * Features:
 *  - Automatically opens a DatePickerDialog when the button is clicked.
 *  - Updates the button text to show the selected date in "dd.MM.yyyy" format.
 *  - Notifies the caller through a callback (OnDateSelectedListener).
 *
 * Purpose:
 *  This avoids repeating the same DatePicker setup code in every Activity and
 *  keeps UI logic clean and reusable.
 */
public class DatePickerHandler {

    /** Context needed to display dialogs */
    private final Context context;

    /** Stores the last selected date/time (not currently used in external logic) */
    private final Calendar selectedDateTime = Calendar.getInstance();

    /** Working calendar used for the DatePicker state (current selection) */
    private final Calendar calendar = Calendar.getInstance();

    /** Format used for displaying dates on buttons */
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    /**
     * Callback interface for Activities/Fragments.
     * Called whenever the user picks a date.
     */
    public interface OnDateSelectedListener {
        void onDateSelected(Calendar calendar);
    }

    /**
     * Constructor — supply the calling Activity/Fragment context.
     */
    public DatePickerHandler(Context context) {
        this.context = context;
    }

    /**
     * Attaches a DatePickerDialog to a Button.
     *
     * Behaviour:
     *  - Button text is initially filled with the default calendar date.
     *  - When the button is clicked → DatePickerDialog opens.
     *  - When user selects a date:
     *         → internal calendar updates
     *         → button text updates
     *         → callback is triggered (if provided)
     *
     * @param button the button that should open a date picker
     * @param listener callback invoked after user selects a date
     */
    public void setupButton(final Button button, final OnDateSelectedListener listener) {

        // Initialize button text with the current calendar value.
        updateButtonText(button, calendar);

        // Reacts to the user selecting a date in the dialog.
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {

            // Update internal calendar to the chosen date.
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            // Update the button label to reflect new date.
            updateButtonText(button, calendar);

            // Notify caller (Activity) of the chosen date.
            if (listener != null) {
                listener.onDateSelected((Calendar) calendar.clone());
            }
        };

        // Button click opens the DatePicker dialog.
        button.setOnClickListener(v ->
                new DatePickerDialog(
                        context,
                        dateSetListener,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
        );
    }

    /**
     * Factory helper that creates a DatePickerHandler AND attaches it to a button.
     *
     * @param button Button to attach the picker to
     * @param listener callback invoked when date changes
     * @param context the Activity or UI context
     * @return configured DatePickerHandler instance
     */
    public static DatePickerHandler createForButton(
            final Button button, final OnDateSelectedListener listener, Context context) {

        DatePickerHandler handler = new DatePickerHandler(context);
        handler.setupButton(button, listener);
        return handler;
    }

    /**
     * Updates the button UI text using the formatted calendar date.
     */
    private void updateButtonText(Button button, Calendar cal) {
        button.setText(dateFormat.format(cal.getTime()));
    }

    /**
     * Helper to build a full datetime string (currently unused, but kept for future UI features).
     */
    private String makeDateTimeString(int day, int month, int year, int hour, int minute) {
        return String.format(
                Locale.GERMAN,
                "%02d.%02d.%04d %02d:%02d",
                day, month, year, hour, minute
        );
    }
}
