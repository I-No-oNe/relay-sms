package com.relay.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

public final class SmsDeliveredReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        byte[] pdu = intent.getByteArrayExtra("pdu");
        String format = intent.getStringExtra("format");
        if (pdu == null) return;
        SmsMessage report = SmsMessage.createFromPdu(pdu, format);
        String outcome = report == null ? null : AttemptState.delivery(format, report.getStatus());
        if (outcome == null) return;
        try {
            LocalStore.delivery(
                    context,
                    intent.getStringExtra("attemptId"),
                    intent.getIntExtra("part", 0),
                    intent.getIntExtra("parts", 1),
                    outcome);
        } catch (Exception ignored) {
            /* The row stays "sent". */
        }
    }
}
