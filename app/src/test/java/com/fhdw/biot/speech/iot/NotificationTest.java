package com.fhdw.biot.speech.iot;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
// import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTest {

    @Mock private Context mockContext;
    @Mock private NotificationManager mockNotificationManager;
    @Mock private SensorManager mockSensorManager;
    @Mock private Sensor mockAccelerometer;

    private MainActivity mainActivity;

    @Before
    public void setUp() {
        // Mocking the Context and NotificationManager
        when(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mockNotificationManager);

        // Initialize MainActivity with mocked context
        mainActivity = new MainActivity();
        mainActivity.sensorManager = mockSensorManager;
        mainActivity.accelerometer = mockAccelerometer;
    }

    @Test
    public void testNotificationCreation() {
        // Create a mock SensorEvent
        SensorEvent mockEvent = mock(SensorEvent.class);
        mockEvent.sensor = mockAccelerometer;
        // mockEvent.values = new float[]{16.0f, 0.0f, 0.0f};

        when(mockAccelerometer.getType()).thenReturn(Sensor.TYPE_ACCELEROMETER);

        // Trigger onSensorChanged to create a notification
        mainActivity.onSensorChanged(mockEvent);

        // Verify that a notification was sent
        ArgumentCaptor<NotificationChannel> channelCaptor =
                ArgumentCaptor.forClass(NotificationChannel.class);
        verify(mockNotificationManager).createNotificationChannel(channelCaptor.capture());

        NotificationChannel capturedChannel = channelCaptor.getValue();
        assertEquals("eventChannel", capturedChannel.getId());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, capturedChannel.getImportance());

        // Verify that a notification was sent
        verify(mockNotificationManager).notify(eq(1), any());
    }
}
