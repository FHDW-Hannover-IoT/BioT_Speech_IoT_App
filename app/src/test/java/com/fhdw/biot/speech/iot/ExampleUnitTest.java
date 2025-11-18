package com.fhdw.biot.speech.iot;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testNormal() {
        assertFalse(shouldNotify(10, 10, 10));
    }

    @Test
    public void testGrenzfall() {
        assertFalse(shouldNotify(15, 15, 15));
    }

    @Test
    public void testGroeßerEineAchse() {
        assertTrue(shouldNotify(16, 10, 10));
    }

    @Test
    public void testGroeßerZweiAchsen() {
        assertTrue(shouldNotify(16, 16, 10));
    }

    @Test
    public void testGroeßerAlleAchsen() {
        assertTrue(shouldNotify(16, 16, 16));
    }

    @Test
    public void testNegativeValues() {
        assertTrue(shouldNotify(-16, -16, -16));
    }

    private boolean shouldNotify(float x, float y, float z) {
        return x > 15 || y > 15 || z > 15 || x < -15 || y < -15 || z < -15;
    }


}
// Da Mattis aktuell für alle 3 Sensoren das Limit auf 15 gesetzt hat, sind das die Testfälle
// Da ich den aktuellen Namen der Methode nicht kenne, heißt sie bei mir "shouldNotify"
