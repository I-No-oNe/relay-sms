package com.relay.sms;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

final class Journal {
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("sms-journal", Context.MODE_PRIVATE);
    }

    static synchronized String deviceId(Context context) {
        String id = prefs(context).getString("deviceId", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            if (!prefs(context).edit().putString("deviceId", id).commit())
                throw new IllegalStateException("Cannot persist device identity.");
        }
        return id;
    }

    static synchronized JSONObject pending(Context context) throws Exception {
        String data = prefs(context).getString("pending", null);
        return data == null ? null : new JSONObject(data);
    }

    static synchronized void put(Context context, JSONObject record) {
        if (!prefs(context).edit().putString("pending", record.toString()).commit())
            throw new IllegalStateException("Cannot persist SMS attempt. Nothing will be sent.");
    }

    static synchronized void acknowledge(Context context, String id, int part, int code)
            throws Exception {
        JSONObject record = pending(context);
        if (record == null || !record.getString("attemptId").equals(id)) return;
        JSONArray codes = record.getJSONArray("codes");
        if (part < 0 || part >= codes.length()) return;
        if (codes.getInt(part) == AttemptState.PENDING) codes.put(part, code);
        put(context, record);
    }

    static String outcome(JSONObject record) throws Exception {
        JSONArray codes = record.getJSONArray("codes");
        int[] values = new int[codes.length()];
        for (int i = 0; i < values.length; i++) values[i] = codes.getInt(i);
        return AttemptState.outcome(values);
    }

    static synchronized void clear(Context context) {
        if (!prefs(context).edit().remove("pending").commit())
            throw new IllegalStateException("Could not clear confirmed attempt.");
    }
}
