package com.fhdw.biot.speech.iot;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class AndererUnitTest {

    public static class SensorEventTest {

        private Context mockContext;
        private NotificationManager mockNotificationManager;

        @Before
        public void setUp() {
            // Mocking the Context and NotificationManager
            mockContext = mock(Context.class);
            mockNotificationManager = mock(NotificationManager.class);

            // Mocking getSystemService to return our mock NotificationManager
            when(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager);
        }

        @Test
        public void testSensorEventInitialization() {
            long timestamp = System.currentTimeMillis();
            String sensorType = "Temperature";
            float value = 25.5f;
            String id = "sensor123";

            SensorEvent sensorEvent = new SensorEvent(timestamp, sensorType, value, id, mockContext);

            assertEquals(timestamp, sensorEvent.getTimestamp());
            assertEquals(sensorType, sensorEvent.getSensorType());
            assertEquals(value, sensorEvent.getValue(), 0.0);
            assertEquals(id, sensorEvent.getId());
        }

        @Test
        public void testCreateNotification() {
            long timestamp = System.currentTimeMillis();
            String sensorType = "Temperature";
            float value = 25.5f;
            String id = "sensor123";

            SensorEvent sensorEvent = new SensorEvent(timestamp, sensorType, value, id, mockContext);

            // Verify that the notification channel was created
            ArgumentCaptor<NotificationChannel> channelCaptor = ArgumentCaptor.forClass(NotificationChannel.class);
            verify(mockNotificationManager).createNotificationChannel(channelCaptor.capture());

            NotificationChannel capturedChannel = channelCaptor.getValue();
            assertEquals("eventChannel", capturedChannel.getId());
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, capturedChannel.getImportance());

            // Verify that a notification was sent
            verify(mockNotificationManager).notify(eq(1), any());
        }
    }
}
