package com.relay.sms;

final class AttemptState {
    static final int PENDING = Integer.MIN_VALUE;

    static String outcome(int[] codes) {
        if (codes.length == 0) return "uncertain";
        boolean success = false;
        boolean failure = false;
        for (int code : codes) {
            if (code == PENDING) return null;
            if (code == -1) success = true;
            else failure = true;
        }
        return success && failure ? "uncertain" : failure ? "failed" : "sent";
    }

    // Carrier delivery report status: "delivered", "undelivered", or null while the network is
    // still trying.
    static String delivery(String format, int status) {
        if ("3gpp".equals(format))
            return status < 0x20 ? "delivered" : status >= 0x40 ? "undelivered" : null;
        return status == 0 ? "delivered" : null;
    }
}
