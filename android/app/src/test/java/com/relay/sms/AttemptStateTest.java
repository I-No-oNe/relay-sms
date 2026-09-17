package com.relay.sms;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AttemptStateTest {
    @Test public void waitsForEverySmsPart() { assertNull(AttemptState.outcome(new int[]{-1, AttemptState.PENDING})); }
    @Test public void allAcknowledgedMeansSent() { assertEquals("sent", AttemptState.outcome(new int[]{-1, -1, -1})); }
    @Test public void allErrorsMeansFailed() { assertEquals("failed", AttemptState.outcome(new int[]{1, 2})); }
    @Test public void PartialMultipartMessageIsUncertain() { assertEquals("uncertain", AttemptState.outcome(new int[]{-1, 1})); }
    @Test public void noPartsIsUncertain() { assertEquals("uncertain", AttemptState.outcome(new int[]{})); }
}
