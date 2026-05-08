package com.fhdw.biot.speech.iot.util;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.Button;
import java.util.Calendar;
import java.util.Locale;

/**
 * DateTimePickerHandler
 * ───────────────────────────────────────────────────────────────────────────── Attaches a two-step
 * date+time picker to any Button.
 *
 * <p>Tap sequence: 1. DatePickerDialog opens (calendar wheel) 2. On date confirmed →
 * TimePickerDialog opens immediately (clock wheel, like alarm UI) 3. Button text updates to
 * "dd.MM.yyyy HH:mm" 4. Callback fires with a fully set Calendar
 */
public final class DateTimePickerHandler {

    private DateTimePickerHandler() {}

    public interface OnDateTimeSelectedListener {
        void onDateTimeSelected(Calendar calendar);
    }

    public static void createForButton(
            Button button, OnDateTimeSelectedListener listener, Context context) {
        Calendar cal = Calendar.getInstance();
        button.setText(format(cal));

        button.setOnClickListener(
                v -> {
                    DatePickerDialog datePicker =
                            new DatePickerDialog(
                                    context,
                                    (dateView, year, month, day) -> {
                                        cal.set(Calendar.YEAR, year);
                                        cal.set(Calendar.MONTH, month);
                                        cal.set(Calendar.DAY_OF_MONTH, day);

                                        new TimePickerDialog(
                                                        context,
                                                        (timeView, hour, minute) -> {
                                                            cal.set(Calendar.HOUR_OF_DAY, hour);
                                                            cal.set(Calendar.MINUTE, minute);
                                                            cal.set(Calendar.SECOND, 0);
                                                            cal.set(Calendar.MILLISECOND, 0);
                                                            button.setText(format(cal));
                                                            if (listener != null)
                                                                listener.onDateTimeSelected(
                                                                        (Calendar) cal.clone());
                                                        },
                                                        cal.get(Calendar.HOUR_OF_DAY),
                                                        cal.get(Calendar.MINUTE),
                                                        true)
                                                .show();
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH));
                    datePicker.show();
                });
    }

    public static String format(Calendar cal) {
        return String.format(
                Locale.ENGLISH,
                "%02d.%02d.%04d %02d:%02d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE));
    }
}
