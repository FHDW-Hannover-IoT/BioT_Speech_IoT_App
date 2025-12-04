package com.fhdw.biot.speech.iot;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DatePickerHandler {

    private final Context context;
    private final Calendar selectedDateTime = Calendar.getInstance();

    public DatePickerHandler(Context context) {
        this.context = context;
    }

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar calendar);
    }

    public void setupButton(final Button button, final OnDateSelectedListener listener) {
        updateButtonText(button, calendar);

        DatePickerDialog.OnDateSetListener dateSetListener =
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateButtonText(button, calendar);
                    if (listener != null) {
                        listener.onDateSelected((Calendar) calendar.clone());
                    }
                };

        button.setOnClickListener(
                v ->
                        new DatePickerDialog(
                                        context,
                                        dateSetListener,
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH))
                                .show());
    }

    public static DatePickerHandler createForButton(
            final Button button, final OnDateSelectedListener listener, Context context) {
        DatePickerHandler handler = new DatePickerHandler(context);
        handler.setupButton(button, listener);
        return handler;
    }

    private void updateButtonText(Button button, Calendar cal) {
        button.setText(dateFormat.format(cal.getTime()));
    }

    private String makeDateTimeString(int day, int month, int year, int hour, int minute) {
        return String.format(
                Locale.GERMAN, "%02d.%02d.%04d %02d:%02d", day, month, year, hour, minute);
    }
}
