package com.fhdw.biot.speech.iot;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DatePickerHandler {

    private final Context context;
    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar calendar);
    }

    public DatePickerHandler(Context context) {
        this.context = context;
    }

    public void setupButton(final Button button, final OnDateSelectedListener listener) {
        updateButtonText(button, calendar);

        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateButtonText(button, calendar);
            if (listener != null) {
                listener.onDateSelected((Calendar) calendar.clone());
            }
        };

        button.setOnClickListener(v -> new DatePickerDialog(context, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show());
    }

    /**
     * Gibt eine neue Instanz eines DatePickerHandlers für einen Button mit separatem Calendar zurück.
     * ermöglicht mehrere unabhängige Datum-Picker.
     */
    public static DatePickerHandler createForButton(final Button button, final OnDateSelectedListener listener, Context context) {
        DatePickerHandler handler = new DatePickerHandler(context);
        handler.setupButton(button, listener);
        return handler;
    }

    private void updateButtonText(Button button, Calendar cal) {
        button.setText(dateFormat.format(cal.getTime()));
    }
}
