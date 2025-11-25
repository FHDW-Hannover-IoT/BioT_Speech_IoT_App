package com.fhdw.biot.speech.iot;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;
import java.util.Calendar;
import java.util.Locale;

public class DatePickerHandler {

    private final Context context;
    private final Calendar selectedDateTime = Calendar.getInstance();

    public DatePickerHandler(Context context) {
        this.context = context;
    }

    public void setupButton(final Button button) {
        TimePickerDialog.OnTimeSetListener timeSetListener =
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                        selectedDateTime.set(Calendar.HOUR_OF_DAY, hour);
                        selectedDateTime.set(Calendar.MINUTE, minute);

                        String date =
                                makeDateTimeString(
                                        selectedDateTime.get(Calendar.DAY_OF_MONTH),
                                        selectedDateTime.get(Calendar.MONTH),
                                        selectedDateTime.get(Calendar.YEAR),
                                        hour,
                                        minute);
                        button.setText(date);
                    }
                };

        DatePickerDialog.OnDateSetListener dateSetListener =
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                        selectedDateTime.set(Calendar.YEAR, year);
                        selectedDateTime.set(Calendar.MONTH, month);
                        selectedDateTime.set(Calendar.DAY_OF_MONTH, day);

                        new TimePickerDialog(
                                        context,
                                        timeSetListener,
                                        selectedDateTime.get(Calendar.HOUR_OF_DAY),
                                        selectedDateTime.get(Calendar.MINUTE),
                                        true)
                                .show();
                    }
                };

        button.setOnClickListener(
                view -> {
                    new DatePickerDialog(
                                    context,
                                    dateSetListener,
                                    selectedDateTime.get(Calendar.YEAR),
                                    selectedDateTime.get(Calendar.MONTH),
                                    selectedDateTime.get(Calendar.DAY_OF_MONTH))
                            .show();
                });
    }

    private String makeDateTimeString(int day, int month, int year, int hour, int minute) {
        return String.format(
                Locale.GERMAN, "%02d.%02d.%04d %02d:%02d", day, month, year, hour, minute);
    }
}
