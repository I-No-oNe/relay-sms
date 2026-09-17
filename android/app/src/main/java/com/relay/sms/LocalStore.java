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
    static synchronized void result(Context context, int row, String outcome) throws Exception {
        JSONObject data = load(context); data.getJSONArray("rows").getJSONObject(row).put("status", outcome); save(context, data);
    }
}
