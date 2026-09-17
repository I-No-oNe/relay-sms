package com.relay.sms;

final class AttemptState {
    static final int PENDING = Integer.MIN_VALUE;
    static String outcome(int[] codes) {
        if (codes.length == 0) return "uncertain";
        boolean success = false;
        boolean failure = false;
        for (int code : codes) {
            if (code == PENDING) return null;
            if (code == -1) success = true; else failure = true;
        }
        return success && failure ? "uncertain" : failure ? "failed" : "sent";
    }
}
