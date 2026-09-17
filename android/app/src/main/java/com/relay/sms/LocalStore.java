package com.relay.sms;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class LocalStore {
    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "campaign.json")); }
    static synchronized JSONObject load(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists()) return new JSONObject().put("rows", new JSONArray()).put("variants", new JSONArray().put(context.getString(R.string.default_variant_1)).put(context.getString(R.string.default_variant_2)).put(context.getString(R.string.default_variant_3)));
        return new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
    }
    static synchronized void save(Context context, JSONObject data) throws Exception {
        AtomicFile file = file(context); FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(data.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); }
        catch (Exception error) { if (stream != null) file.failWrite(stream); throw error; }
    }
    // Marks the next queued row as sending in one locked read-write, so delivery reports saved meanwhile aren't overwritten.
    static synchronized JSONObject claim(Context context, String attemptId) throws Exception {
        JSONObject data = load(context); JSONArray rows = data.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) if (rows.getJSONObject(i).optString("status").equals("queued")) {
            JSONObject row = rows.getJSONObject(i).put("status", "sending").put("attemptId", attemptId); save(context, data);
            return new JSONObject(row.toString()).put("index", i);
        }
        return null;
    }
    static synchronized void delivery(Context context, String attemptId, int part, int parts, String outcome) throws Exception {
        JSONObject data = load(context); JSONArray rows = data.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i); if (!row.optString("attemptId").equals(attemptId)) continue;
            if (outcome.equals("undelivered")) row.put("status", "undelivered");
            else if (!row.optString("status").equals("undelivered")) {
                JSONArray done = row.optJSONArray("deliveredParts"); if (done == null) row.put("deliveredParts", done = new JSONArray());
                boolean seen = false; for (int k = 0; k < done.length(); k++) seen |= done.getInt(k) == part;
                if (!seen) done.put(part);
                if (done.length() >= parts) row.put("status", "delivered");
            }
            save(context, data); return;
        }
    }
    static synchronized void result(Context context, int row, String outcome) throws Exception {
        JSONObject data = load(context); data.getJSONArray("rows").getJSONObject(row).put("status", outcome); save(context, data);
    }
}
