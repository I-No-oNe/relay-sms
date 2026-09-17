package com.relay.sms;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SmsManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmsService extends Service {
    static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private volatile boolean cancelled;
    private NotificationManager notifications;
    @Override public void onCreate() {
        super.onCreate(); notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("sending", getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String text, boolean ongoing) { return notification(text, ongoing, 0, 0); }
    private Notification notification(String text, boolean ongoing, int done, int total) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, "sending").setSmallIcon(R.drawable.ic_stat_sms).setContentTitle(getString(R.string.app_name)).setContentText(text).setContentIntent(open).setOngoing(ongoing).setOnlyAlertOnce(true);
        if (total > 0) builder.setProgress(total, done, false);
        if (ongoing) builder.addAction(new Notification.Action.Builder(null, getString(R.string.stop_after_current), PendingIntent.getService(this, 1, new Intent(this, SmsService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("STOP".equals(intent.getAction())) { cancelled = true; if (!RUNNING.get()) stopSelf(); return START_NOT_STICKY; }
        if (!RUNNING.compareAndSet(false, true)) return START_NOT_STICKY;
        startForeground(1, notification(getString(R.string.notif_preparing), true));
        int subscription = intent.getIntExtra("subscriptionId", -1);
        new Thread(() -> runCampaign(subscription), "relay-sms").start();
        return START_NOT_STICKY;
    }
    private void report(JSONObject record, String outcome) throws Exception {
        LocalStore.result(this, record.getInt("recipientId"), outcome);
        Journal.clear(this);
    }
    private void runCampaign(int subscription) {
        PowerManager.WakeLock wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RelaySMS:Campaign");
        int sent = 0;
        String finalText = getString(R.string.notif_stopped);
        try {
            wake.acquire(4 * 60 * 60_000L); // about 4 seconds per SMS
            if (subscription < 0 || checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) throw new IllegalStateException("SMS permission or SIM unavailable");
            JSONObject pending = Journal.pending(this);
            if (pending != null) { String result = Journal.outcome(pending); report(pending, result == null ? "uncertain" : result); }
            SmsManager sms = Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class).createForSubscriptionId(subscription) : SmsManager.getSmsManagerForSubscriptionId(subscription);
            int total = 0; JSONArray initial = LocalStore.load(this).getJSONArray("rows"); for (int i = 0; i < initial.length(); i++) if (initial.getJSONObject(i).optString("status").equals("queued")) total++;
            while (!cancelled) {
                JSONObject claim = LocalStore.claim(this, UUID.randomUUID().toString());
                if (claim == null) { finalText = getString(R.string.notif_done); break; }
                int recipient = claim.getInt("index");
                ArrayList<String> parts = sms.divideMessage(claim.getString("text"));
                JSONArray codes = new JSONArray();
                for (int i = 0; i < parts.size(); i++) codes.put(AttemptState.PENDING);
                JSONObject record = new JSONObject().put("recipientId", recipient).put("attemptId", claim.getString("attemptId")).put("codes", codes);
                Journal.put(this, record); // Persist before handing anything to the radio.
                ArrayList<PendingIntent> callbacks = new ArrayList<>(), deliveries = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    Intent event = new Intent(this, SmsSentReceiver.class).setData(Uri.parse("relay://sms/" + claim.getString("attemptId") + "/" + i)).putExtra("attemptId", claim.getString("attemptId")).putExtra("part", i);
                    callbacks.add(PendingIntent.getBroadcast(this, i, event, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                    // Mutable so the system can attach the report PDU.
                    Intent report = new Intent(this, SmsDeliveredReceiver.class).setData(Uri.parse("relay://delivery/" + claim.getString("attemptId") + "/" + i)).putExtra("attemptId", claim.getString("attemptId")).putExtra("part", i).putExtra("parts", parts.size());
                    deliveries.add(PendingIntent.getBroadcast(this, i, report, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)));
                }
                if (parts.size() == 1) sms.sendTextMessage(claim.getString("phone"), null, parts.get(0), callbacks.get(0), deliveries.get(0));
                else sms.sendMultipartTextMessage(claim.getString("phone"), null, parts, callbacks, deliveries);
                long timeout = SystemClock.elapsedRealtime() + 90_000;
                String outcome = null;
                while (SystemClock.elapsedRealtime() < timeout) {
                    record = Journal.pending(this);
                    if (record == null) throw new IllegalStateException("SMS journal missing");
                    outcome = Journal.outcome(record);
                    if (outcome != null) break;
                    Thread.sleep(500);
                }
                if (outcome == null) outcome = "uncertain";
                report(record, outcome);
                if (!outcome.equals("sent")) { finalText = getString(R.string.notif_paused); break; }
                sent++;
                notifications.notify(1, notification(getString(R.string.notif_progress, sent, total), true, sent, total));
                Thread.sleep(3000);
            }
            if (cancelled) finalText = getString(R.string.notif_stopped_user);
        } catch (Exception ignored) {
            try { JSONObject pending = Journal.pending(this); if (pending != null) { String result = Journal.outcome(pending); report(pending, result == null ? "uncertain" : result); } } catch (Exception unresolved) { /* Durable journal stays for the next start. */ }
            finalText = getString(R.string.notif_stopped_error);
        } finally {
            if (wake.isHeld()) wake.release();
            RUNNING.set(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notifications.notify(2, notification(finalText, false));
            stopSelf();
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
