package com.fhdw.biot.speech.iot;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.DatePicker;
import java.util.Calendar;

public class DatePickerHandler {

    private Context context;

    public DatePickerHandler(Context context) {
        this.context = context;
    }

    public void setupButton(final Button button) {
        DatePickerDialog.OnDateSetListener dateSetListener =
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                        month = month + 1;
                        String date = makeDateString(day, month, year);
                        button.setText(date);
                    }
                };
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        final DatePickerDialog dialog =
                new DatePickerDialog(context, dateSetListener, year, month, day);

        button.setOnClickListener(
                view -> {
                    dialog.show();
                });
    }

    private String makeDateString(int day, int month, int year) {
        return day + "." + month + "." + year;
    }
}
