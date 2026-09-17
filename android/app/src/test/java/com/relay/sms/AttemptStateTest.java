package com.relay.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AttemptStateTest {
    @Test
    public void waitsForEverySmsPart() {
        assertNull(AttemptState.outcome(new int[] {-1, AttemptState.PENDING}));
    }

    @Test
    public void allAcknowledgedMeansSent() {
        assertEquals("sent", AttemptState.outcome(new int[] {-1, -1, -1}));
    }

    @Test
    public void allErrorsMeansFailed() {
        assertEquals("failed", AttemptState.outcome(new int[] {1, 2}));
    }

    @Test
    public void PartialMultipartMessageIsUncertain() {
        assertEquals("uncertain", AttemptState.outcome(new int[] {-1, 1}));
    }

    @Test
    public void noPartsIsUncertain() {
        assertEquals("uncertain", AttemptState.outcome(new int[] {}));
    }

    @Test
    public void deliveryReports() {
        assertEquals("delivered", AttemptState.delivery("3gpp", 0x00));
        assertNull(AttemptState.delivery("3gpp", 0x20));
        assertEquals("undelivered", AttemptState.delivery("3gpp", 0x41));
        assertEquals("delivered", AttemptState.delivery("3gpp2", 0));
    }
}
