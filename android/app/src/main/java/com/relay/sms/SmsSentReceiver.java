package com.relay.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SmsSentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Journal.acknowledge(
                    context,
                    intent.getStringExtra("attemptId"),
                    intent.getIntExtra("part", -1),
                    getResultCode());
        } catch (Exception ignored) {
            /* Missing durable acknowledgment is treated as uncertain, never retried. */
        }
    }
}
