package com.relay.sms;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.Editable;
import android.text.TextDirectionHeuristics;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class MainActivity extends Activity {
    static final int MAX_RECIPIENTS = 1000;
    private static final int BG = 0xfff2f2f7, SURFACE = 0xffffffff, INK = 0xff1c1c1e, MUTED = 0xff636366, LINE = 0xffe5e5ea, FIELD_LINE = 0xffc7c7cc, TRACK = 0xffd1d1d6,
        GREEN = 0xff0f6b3d, GREEN_PRESSED = 0xff0a5530, GREEN_SOFT = 0xffe8f3ec, WARN = 0xff8a4b00, WARN_SOFT = 0xfffff6e0, DISABLED_FILL = 0xffe5e5ea, DISABLED_TEXT = 0xff8e8e93;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private LinearLayout page;
    private JSONObject data;
    private String screen = "home", shown = "", country;
    private Runnable beforeLeave;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean live;
    // Refresh home while sending, and once more after it stops.
    private final Runnable refresh = new Runnable() { public void run() { boolean running = SmsService.RUNNING.get(); if (screen.equals("home") && (running || live)) home(); live = running; handler.postDelayed(this, 1500); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        country = Phones.country(this);
        try {
            data = LocalStore.load(this);
            if (!SmsService.RUNNING.get()) {
                JSONArray rows = data.getJSONArray("rows");
                JSONObject pending = Journal.pending(this); String outcome = pending == null ? null : Journal.outcome(pending);
                if (outcome != null && pending.getInt("recipientId") < rows.length()) { rows.getJSONObject(pending.getInt("recipientId")).put("status", outcome); save(); Journal.clear(this); }
                for (int i = 0; i < rows.length(); i++) if (rows.getJSONObject(i).optString("status").equals("sending")) rows.getJSONObject(i).put("status", "uncertain");
                save();
            }
            home();
        } catch (Exception error) { error(error); }
    }
    @Override protected void onResume() { super.onResume(); handler.postDelayed(refresh, 1500); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable round(int fill, int stroke, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius)); if (stroke != 0) shape.setStroke(dp(1), stroke); return shape; }
    private static LinearLayout.LayoutParams margins(int width, int top) { LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(width, -2); layout.topMargin = top; return layout; }
    private void base(int title, String action, Runnable click) { base(getString(title), action, click); }
    private void base(String title, String action, Runnable click) {
        beforeLeave = null;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setTextDirection(View.TEXT_DIRECTION_LOCALE); page.setPadding(dp(16), dp(4), dp(16), dp(24)); scroll.addView(page);
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((view, insets) -> { var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets; });
        setContentView(scroll);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setMinimumHeight(dp(44)); page.addView(bar);
        if (!screen.equals("home")) link(bar, getString(R.string.back), this::leave);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (action != null) link(bar, action, click);
        TextView heading = text(title, 30, INK); heading.setLetterSpacing(-0.01f);
        if (!title.equals(shown) && ValueAnimator.areAnimatorsEnabled()) { page.setAlpha(0f); page.setTranslationY(dp(8)); page.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(new DecelerateInterpolator()).start(); }
        shown = title;
    }
    private void link(LinearLayout parent, String label, Runnable click) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(17); view.setTypeface(MEDIUM); view.setTextColor(GREEN); view.setGravity(Gravity.CENTER_VERTICAL); view.setMinHeight(dp(44)); view.setPadding(dp(4), 0, dp(4), 0);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1f0f6b3d), null, null)); view.setOnClickListener(v -> click.run()); parent.addView(view);
    }
    private void leave() { if (beforeLeave != null) beforeLeave.run(); home(); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(0, 1.1f); view.setPadding(0, dp(2), 0, dp(2));
        if (size >= 20) view.setTypeface(null, Typeface.BOLD); else if (size == 17 && color == INK) view.setTypeface(MEDIUM);
        page.addView(view); return view;
    }
    private TextView pill(LinearLayout parent, String value, int fill, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(13); view.setTypeface(MEDIUM); view.setTextColor(color); view.setPadding(dp(10), dp(4), dp(10), dp(4)); view.setBackground(round(fill, 0, 999));
        parent.addView(view, new LinearLayout.LayoutParams(-2, -2)); return view;
    }
    private LinearLayout open(int fill) {
        LinearLayout outer = page, box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(12), dp(16), dp(16)); box.setBackground(round(fill, fill == SURFACE ? LINE : 0, 16));
        outer.addView(box, margins(-1, dp(12))); page = box; return outer;
    }
    private void close(LinearLayout outer) { page = outer; }
    private Button button(String title, Runnable click) { return make(title, click, new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{DISABLED_FILL, GREEN}), 0, new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{DISABLED_TEXT, Color.WHITE}), -1); }
    private Button secondary(String title, Runnable click) { return make(title, click, ColorStateList.valueOf(SURFACE), GREEN, ColorStateList.valueOf(GREEN), -1); }
    private Button quiet(String title, Runnable click) { Button view = make(title, click, ColorStateList.valueOf(Color.TRANSPARENT), 0, ColorStateList.valueOf(GREEN), -1); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48)); ((LinearLayout.LayoutParams) view.getLayoutParams()).topMargin = dp(4); return view; }
    private Button chip(String title, Runnable click) { Button view = make(title, click, ColorStateList.valueOf(SURFACE), GREEN, ColorStateList.valueOf(GREEN), -2); view.setMinHeight(dp(44)); view.setMinimumHeight(dp(44)); view.setTextSize(15); view.setPadding(dp(16), 0, dp(16), 0); return view; }
    private Button make(String title, Runnable click, ColorStateList fill, int stroke, ColorStateList color, int width) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false); view.setTextColor(color); view.setTextSize(17); view.setTypeface(MEDIUM); view.setMinHeight(dp(52)); view.setMinimumHeight(dp(52));
        boolean round = width == -2; GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(round ? 22 : 12)); if (stroke != 0) shape.setStroke(dp(round ? 1 : 2) , stroke);
        int ripple = fill.getDefaultColor() == GREEN ? GREEN_PRESSED : 0x1f0f6b3d;
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape, round(Color.WHITE, 0, round ? 22 : 12)));
        StateListAnimator press = new StateListAnimator(); press.addState(new int[]{android.R.attr.state_pressed}, scale(view, 0.98f)); press.addState(new int[0], scale(view, 1f)); view.setStateListAnimator(press);
        page.addView(view, margins(width, dp(10))); view.setOnClickListener(v -> click.run()); return view;
    }
    private ObjectAnimator scale(View view, float to) { return ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat(View.SCALE_X, to), PropertyValuesHolder.ofFloat(View.SCALE_Y, to)).setDuration(100); }
    private EditText field(String label, String value) {
        if (label != null) { TextView caption = text(label, 13, MUTED); caption.setTypeface(MEDIUM); caption.setPadding(0, dp(8), 0, dp(6)); }
        EditText view = new EditText(this); view.setText(value); view.setTextSize(17); view.setTextColor(INK); view.setHintTextColor(DISABLED_TEXT); view.setMinHeight(dp(52)); view.setPadding(dp(14), dp(12), dp(14), dp(12)); view.setContentDescription(label);
        StateListDrawable states = new StateListDrawable(); GradientDrawable focused = round(SURFACE, 0, 12); focused.setStroke(dp(2), GREEN); states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[0], round(SURFACE, FIELD_LINE, 12)); view.setBackground(states);
        page.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private Spinner select(String label, List<String> items, int index) {
        TextView caption = text(label, 13, MUTED); caption.setTypeface(MEDIUM); caption.setPadding(0, dp(8), 0, dp(6));
        Spinner spinner = new Spinner(this); spinner.setContentDescription(label); spinner.setMinimumHeight(dp(52)); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); spinner.setSelection(Math.max(0, index));
        FrameLayout box = new FrameLayout(this); box.setBackground(round(SURFACE, FIELD_LINE, 12)); box.setPadding(dp(4), 0, dp(4), 0); box.addView(spinner); page.addView(box); return spinner;
    }
    private void progress(int done, int total) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(Math.max(1, total)); bar.setProgress(done);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{round(TRACK, 0, 3), new ClipDrawable(round(GREEN, 0, 3), Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress); layers.setAutoMirrored(true); bar.setProgressDrawable(layers);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(6)); layout.setMargins(0, dp(10), 0, dp(4)); page.addView(bar, layout);
    }
    private void stats(int[] values, int[] labels) {
        LinearLayout row = new LinearLayout(this); row.setBackground(round(SURFACE, LINE, 16)); row.setPadding(0, dp(10), 0, dp(10)); page.addView(row, margins(-1, dp(12)));
        for (int i = 0; i < values.length; i++) {
            if (i > 0) { View divider = new View(this); divider.setBackgroundColor(LINE); row.addView(divider, new LinearLayout.LayoutParams(dp(1), -1)); }
            LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER_HORIZONTAL); row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout outer = page; page = tile; text(String.valueOf(values[i]), 28, INK).setGravity(Gravity.CENTER); text(getString(labels[i]), 13, MUTED).setGravity(Gravity.CENTER); page = outer;
        }
    }
    private TextView notice(String value, boolean ok) {
        TextView view = text(value, 15, ok ? GREEN : WARN); style(view, ok); LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) view.getLayoutParams(); layout.topMargin = dp(10); return view;
    }
    private void style(TextView view, boolean ok) { view.setTypeface(MEDIUM); view.setTextColor(ok ? GREEN : WARN); view.setBackground(round(ok ? GREEN_SOFT : WARN_SOFT, 0, 10)); view.setPadding(dp(12), dp(8), dp(12), dp(8)); }
    private LinearLayout list() { LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setBackground(round(SURFACE, LINE, 16)); page.addView(list, margins(-1, dp(10))); return list; }
    private void entry(LinearLayout list, String name, String phone, String tag, int[] colors, Runnable click) {
        if (list.getChildCount() > 0) { View divider = new View(this); divider.setBackgroundColor(LINE); LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1)); line.setMarginStart(dp(16)); list.addView(divider, line); }
        LinearLayout item = new LinearLayout(this); item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(dp(16), dp(8), dp(16), dp(8)); item.setMinimumHeight(dp(56)); list.addView(item);
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout outer = page; page = labels; text(name.isEmpty() ? getString(R.string.no_name) : name, 17, INK).setPadding(0, 0, 0, 0); text(ltr(phone), 15, MUTED).setPadding(0, 0, 0, 0); page = outer;
        if (tag != null) pill(item, tag, colors[1], colors[0]);
        if (click != null) {
            TextView chevron = new TextView(this); chevron.setText("›"); chevron.setTextSize(22); chevron.setTextColor(FIELD_LINE); chevron.setPaddingRelative(dp(10), 0, 0, 0); item.addView(chevron);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1f000000), null, round(Color.WHITE, 0, 16))); item.setOnClickListener(v -> click.run());
        }
    }
    private static TextWatcher watcher(Runnable change) { return new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) { change.run(); } public void afterTextChanged(Editable e) {} }; }
    private static String ltr(String value) { return BidiFormatter.getInstance(true).unicodeWrap(value, TextDirectionHeuristics.LTR); }
    private String plural(int id, int count) { return getResources().getQuantityString(id, count, count); }
    private static String max() { return NumberFormat.getIntegerInstance().format(MAX_RECIPIENTS); }
    private String display(String phone) { return Phones.display(phone, country); }

    private void save() throws Exception { LocalStore.save(this, data); }
    private void error(Exception error) {
        String message = error instanceof Spreadsheet.ImportError ? getString(((Spreadsheet.ImportError) error).message, max()) : error.getMessage();
        new AlertDialog.Builder(this).setTitle(R.string.error_title).setMessage(message == null ? getString(R.string.save_failed) : message).setPositiveButton(R.string.ok, null).show();
    }
    private JSONArray rows() { return data.optJSONArray("rows"); }
    private String script() { return data.optString("script", ContactRules.defaultScript(getResources().getConfiguration().getLocales().get(0).getLanguage())); }

    private void home() {
        screen = "home";
        try {
            data = LocalStore.load(this);
            int pending = 0, approved = 0, sent = 0, failed = 0, uncertain = 0, skipped = 0, queued = 0, sending = 0;
            for (int i = 0; i < rows().length(); i++) {
                switch (rows().getJSONObject(i).optString("status", "pending")) { case "pending": pending++; break; case "approved": approved++; break; case "queued": queued++; break; case "sending": sending++; break; case "sent": sent++; break; case "failed": failed++; break; case "uncertain": uncertain++; break; case "skipped": skipped++; break; }
            }
            boolean running = SmsService.RUNNING.get(), prepared = data.optBoolean("prepared"); int total = rows().length();
            if (total == 0) {
                base(R.string.title_home, null, null);
                LinearLayout outer = open(SURFACE);
                text(getString(R.string.home_intro, max()), 15, MUTED);
                LinearLayout sample = new LinearLayout(this); sample.setOrientation(LinearLayout.VERTICAL); sample.setBackground(round(BG, 0, 12)); sample.setPadding(dp(12), dp(6), dp(12), dp(6)); page.addView(sample, margins(-1, dp(10)));
                int[][] example = {{R.string.example_name, R.string.example_phone}, {R.string.example_name_1, R.string.example_phone_1}, {R.string.example_name_2, R.string.example_phone_2}};
                for (int r = 0; r < example.length; r++) {
                    LinearLayout line = new LinearLayout(this); line.setPadding(0, dp(6), 0, dp(6)); sample.addView(line);
                    for (int c = 0; c < 2; c++) { TextView cell = new TextView(this); String value = getString(example[r][c]); cell.setText(r > 0 && c == 1 ? ltr(value) : value); cell.setTextSize(15); cell.setTextColor(r == 0 ? MUTED : INK); if (r == 0) cell.setTypeface(MEDIUM); line.addView(cell, new LinearLayout.LayoutParams(0, -2, 1)); }
                }
                button(getString(R.string.choose_file), this::pick);
                close(outer);
                return;
            }
            base(getString(R.string.title_home), running ? null : getString(R.string.new_file), () -> new AlertDialog.Builder(this).setTitle(R.string.start_over_title).setMessage(R.string.start_over_message).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.choose_file, (d, w) -> pick()).show());
            int sendable = sent + failed + uncertain + queued + sending, done = sent + failed + uncertain;
            LinearLayout outer = open(SURFACE);
            if (running) {
                progress(done, sendable); text(getString(R.string.running_progress, done, sendable), 15, MUTED);
                secondary(getString(R.string.stop_after_current), () -> startService(new Intent(this, SmsService.class).setAction("STOP")));
            } else if (prepared && queued > 0) {
                if (done > 0) { progress(done, sendable); text(getString(R.string.paused_progress, done, sendable), 15, MUTED); }
                button(plural(R.plurals.send_messages, queued), this::permissions);
            } else if (prepared) {
                text(getString(R.string.finished), 20, GREEN);
            } else {
                progress(total - pending, total); text(getString(R.string.reviewed_progress, total - pending, total), 15, MUTED);
                button(getString(pending > 0 ? R.string.continue_review : R.string.write_messages), () -> review(0));
            }
            close(outer);
            if (failed + uncertain > 0) notice(getString(R.string.failed_notice, failed, uncertain), false);
            if (prepared) stats(new int[]{sent, queued + sending, skipped}, new int[]{R.string.stat_sent, R.string.stat_waiting, R.string.stat_skipped});
            else stats(new int[]{approved, pending, skipped}, new int[]{R.string.stat_approved, R.string.stat_to_review, R.string.stat_skipped});
            recipients(!prepared && !running);
        } catch (Exception error) { error(error); }
    }
    private static int rank(String status) {
        switch (status) { case "failed": return 0; case "uncertain": return 1; case "sending": return 2; case "pending": return 3; case "queued": return 4; case "approved": return 5; case "sent": return 6; default: return 7; }
    }
    private String status(String value) {
        switch (value) { case "approved": return getString(R.string.status_approved); case "queued": return getString(R.string.status_queued); case "sending": return getString(R.string.status_sending); case "sent": return getString(R.string.status_sent); case "failed": return getString(R.string.status_failed); case "uncertain": return getString(R.string.status_uncertain); case "skipped": return getString(R.string.status_skipped); default: return getString(R.string.status_pending); }
    }
    // Text and background color for each status pill.
    private static int[] colors(String value) {
        switch (value) {
            case "sent": return new int[]{GREEN, GREEN_SOFT};
            case "approved": return new int[]{0xff0e7490, 0xffe0f2f5};
            case "queued": return new int[]{0xff1d4ed8, 0xffe7eefd};
            case "sending": return new int[]{0xff6d28d9, 0xfff0eafd};
            case "failed": return new int[]{0xffb42318, 0xfffdecea};
            case "uncertain": return new int[]{WARN, WARN_SOFT};
            case "skipped": return new int[]{MUTED, BG};
            default: return new int[]{0xffa3246b, 0xfffbe7f1};
        }
    }
    private void recipients(boolean editable) {
        EditText search = field(null, ""); search.setHint(R.string.search_hint); search.setContentDescription(getString(R.string.search_hint)); ((LinearLayout.LayoutParams) search.getLayoutParams()).topMargin = dp(12);
        LinearLayout list = list(); TextView more = text("", 13, MUTED);
        List<Integer> order = new ArrayList<>(); for (int i = 0; i < rows().length(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> rank(rows().optJSONObject(i).optString("status"))));
        final int limit = 200;
        Runnable render = () -> {
            list.removeAllViews();
            String query = search.getText().toString().trim(), digits = query.replaceAll("[^0-9]", ""); int matches = 0;
            for (int i : order) {
                JSONObject row = rows().optJSONObject(i); String name = row.optString("name"), phone = row.optString("phone"), shownPhone = display(phone), state = row.optString("status");
                if (!query.isEmpty() && !name.toLowerCase().contains(query.toLowerCase()) && (digits.isEmpty() || !(shownPhone.replaceAll("[^0-9]", "").contains(digits) || phone.contains(digits)))) continue;
                if (++matches > limit) continue;
                final int index = i;
                entry(list, name, shownPhone, status(state), colors(state), editable ? () -> { try { row.put("status", "pending"); save(); review(index); } catch (Exception e) { error(e); } } : null);
            }
            list.setVisibility(matches == 0 ? View.GONE : View.VISIBLE);
            more.setText(matches == 0 ? getString(R.string.no_recipients_found) : matches > limit ? getString(R.string.showing_limit, limit, matches) : getString(R.string.sent_meaning));
        };
        search.addTextChangedListener(watcher(render)); render.run();
    }
    private void pick() { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"}), 12); }
    @Override protected void onActivityResult(int code, int result, Intent intent) {
        super.onActivityResult(code, result, intent);
        if (code != 12 || result != RESULT_OK || intent == null) return;
        screen = "import"; base(R.string.title_loading, null, null);
        ProgressBar spinner = new ProgressBar(this); spinner.setIndeterminateTintList(ColorStateList.valueOf(GREEN)); spinner.setPadding(0, dp(24), 0, 0); page.addView(spinner);
        new Thread(() -> { try (var stream = getContentResolver().openInputStream(intent.getData())) { List<List<String>> table = Spreadsheet.read(stream); runOnUiThread(() -> mapping(table)); } catch (Exception error) { runOnUiThread(() -> { home(); error(error); }); } }).start();
    }
    private static int number(EditText input) { try { return Integer.parseInt(input.getText().toString().trim()); } catch (NumberFormatException error) { return -1; } }
    void mapping(List<List<String>> table) {
        screen = "import";
        if (table.isEmpty()) { home(); error(new Exception(getString(R.string.file_empty))); return; }
        int[] detected = Spreadsheet.detect(table); int width = table.stream().mapToInt(List::size).max().orElse(1);
        base(R.string.title_columns, null, null);
        List<String> columns = new ArrayList<>(); for (int i = 0; i < width; i++) columns.add(getString(R.string.column_item, i + 1, cell(table.get(Math.min(detected[0], table.size()-1)), i)));
        LinearLayout outer = open(SURFACE);
        Spinner phone = select(getString(R.string.phone_column), columns, detected[1]); List<String> names = new ArrayList<>(); names.add(getString(R.string.no_name_column)); names.addAll(columns); Spinner name = select(getString(R.string.name_column), names, detected[2] + 1);
        EditText start = field(getString(R.string.first_row), String.valueOf(detected[0] + 1)); start.setInputType(2);
        close(outer);
        LinearLayout preview = list(); TextView count = text("", 13, MUTED);
        Runnable update = () -> {
            preview.removeAllViews();
            int first = number(start) - 1;
            if (first < 0 || first >= table.size()) { preview.setVisibility(View.GONE); count.setText(R.string.no_such_row); return; }
            preview.setVisibility(View.VISIBLE);
            for (int i = first; i < Math.min(table.size(), first + 5); i++) entry(preview, ContactRules.cleanName(cell(table.get(i), name.getSelectedItemPosition() - 1)), display(Phones.normalize(cell(table.get(i), phone.getSelectedItemPosition()), country)), null, null, null);
            int size = table.size() - first;
            count.setText(plural(R.plurals.rows_to_load, size) + (size > MAX_RECIPIENTS ? getString(R.string.max_warning, max()) : ""));
        };
        AdapterView.OnItemSelectedListener changed = new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { update.run(); } public void onNothingSelected(AdapterView<?> parent) {} };
        phone.setOnItemSelectedListener(changed); name.setOnItemSelectedListener(changed); start.addTextChangedListener(watcher(update)); update.run();
        button(getString(R.string.continue_to_review), () -> { try {
            int first = number(start) - 1;
            if (first < 0 || first >= table.size() || table.size() - first > MAX_RECIPIENTS) throw new Exception(getString(R.string.invalid_first_row, max()));
            JSONArray contacts = new JSONArray();
            for (int i = first; i < table.size(); i++) contacts.put(new JSONObject().put("name", ContactRules.cleanName(cell(table.get(i), name.getSelectedItemPosition()-1))).put("phone", Phones.normalize(cell(table.get(i), phone.getSelectedItemPosition()), country)).put("status", "pending"));
            Journal.clear(this); data.put("rows", contacts).put("prepared", false); save(); review(0);
        } catch (Exception error) { error(error); } });
    }
    private static String cell(List<String> row, int index) { return index < 0 || index >= row.size() ? "" : row.get(index); }
    // Returns null when the row can be approved.
    private String issue(int index, String name, String phone) throws Exception {
        if (!ContactRules.nameOk(name, script())) return getString(R.string.issue_script, getResources().getStringArray(R.array.scripts)[Arrays.asList(ContactRules.SCRIPTS).indexOf(script())]);
        if (!Phones.valid(phone, country)) return getString(R.string.issue_phone);
        String normalized = Phones.normalize(phone, country);
        for (int i = 0; i < rows().length(); i++) if (i != index && !rows().getJSONObject(i).optString("status").equals("skipped") && Phones.normalize(rows().getJSONObject(i).optString("phone"), country).equals(normalized)) return getString(R.string.issue_duplicate);
        return null;
    }
    // Pending rows that can be approved as they are.
    private List<Integer> validPending() throws Exception {
        HashMap<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < rows().length(); i++) { JSONObject row = rows().getJSONObject(i); if (!row.optString("status").equals("skipped")) counts.merge(Phones.normalize(row.optString("phone"), country), 1, Integer::sum); }
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < rows().length(); i++) { JSONObject row = rows().getJSONObject(i); if (row.optString("status").equals("pending") && ContactRules.nameOk(row.optString("name"), script()) && Phones.valid(row.optString("phone"), country) && counts.get(Phones.normalize(row.optString("phone"), country)) == 1) valid.add(i); }
        return valid;
    }
    private void review(int from) {
        screen = "review";
        try {
            int index = -1, remaining = 0;
            for (int i = 0; i < rows().length(); i++) if (rows().getJSONObject(i).optString("status").equals("pending")) { remaining++; if (index < 0 && i >= from) index = i; }
            if (index < 0 && remaining > 0) { review(0); return; }
            if (remaining == 0) { messages(); return; }
            final int current = index; JSONObject row = rows().getJSONObject(current); int total = rows().length();
            base(R.string.title_review, null, null);
            progress(total - remaining, total); text(getString(R.string.reviewed_progress, total - remaining, total), 13, MUTED);
            String rule = script();
            Spinner scripts = select(getString(R.string.name_rule), Arrays.asList(getResources().getStringArray(R.array.scripts)), Arrays.asList(ContactRules.SCRIPTS).indexOf(rule));
            scripts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (ContactRules.SCRIPTS[position].equals(rule)) return; try { data.put("script", ContactRules.SCRIPTS[position]); save(); review(current); } catch (Exception e) { error(e); } }
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            int ready = validPending().size();
            if (ready > 1) secondary(getString(R.string.approve_valid, ready), () -> { try { for (int i : validPending()) { JSONObject valid = rows().getJSONObject(i); valid.put("name", valid.optString("name").trim()).put("phone", Phones.normalize(valid.optString("phone"), country)).put("status", "approved"); } save(); review(0); } catch (Exception e) { error(e); } });
            List<Integer> noPhone = new ArrayList<>(); for (int i = 0; i < rows().length(); i++) if (rows().getJSONObject(i).optString("status").equals("pending") && !Phones.valid(rows().getJSONObject(i).optString("phone"), country)) noPhone.add(i);
            if (noPhone.size() > 1) secondary(getString(R.string.skip_invalid_phones, noPhone.size()), () -> { try { for (int i : noPhone) rows().getJSONObject(i).put("status", "skipped"); save(); review(0); } catch (Exception e) { error(e); } });
            LinearLayout outer = open(SURFACE);
            EditText name = field(getString(R.string.name_label), row.optString("name")); name.setHint(R.string.name_hint);
            EditText phone = field(getString(R.string.phone_label), display(row.optString("phone"))); phone.setInputType(3); phone.setTextDirection(View.TEXT_DIRECTION_LTR);
            TextView feedback = notice("", true);
            Button approve = button(getString(R.string.approve_next), () -> { try { if (issue(current, name.getText().toString(), phone.getText().toString()) != null) return; row.put("name", name.getText().toString().trim()).put("phone", Phones.normalize(phone.getText().toString(), country)).put("status", "approved"); save(); review(current + 1); } catch (Exception e) { error(e); } });
            Runnable validate = () -> { try {
                String problem = issue(current, name.getText().toString(), phone.getText().toString()); boolean ok = problem == null;
                feedback.setText(ok ? "✓ " + getString(name.getText().toString().trim().isEmpty() ? R.string.no_name_ready : R.string.ready) : "⚠ " + problem); style(feedback, ok); approve.setEnabled(ok);
                row.put("name", name.getText().toString()).put("phone", phone.getText().toString()); save();
            } catch (Exception e) { error(e); } };
            name.addTextChangedListener(watcher(validate)); phone.addTextChangedListener(watcher(validate)); validate.run();
            secondary(getString(R.string.skip_recipient), () -> { try { row.put("status", "skipped"); save(); review(current + 1); } catch (Exception e) { error(e); } });
            close(outer);
            LinearLayout next = null; int shownNext = 0;
            for (int i = current + 1; i < rows().length() && shownNext < 5; i++) {
                JSONObject candidate = rows().getJSONObject(i); if (!candidate.optString("status").equals("pending")) continue;
                if (next == null) next = list();
                boolean bad = !ContactRules.nameOk(candidate.optString("name"), rule); final int target = i;
                entry(next, candidate.optString("name").trim(), display(candidate.optString("phone")), bad ? getString(R.string.check_name) : null, colors("uncertain"), () -> review(target)); shownNext++;
            }
        } catch (Exception e) { error(e); }
    }
    void messages() {
        screen = "messages";
        try {
            int approvedCount = 0, nameless = 0; String longest = "", first = "";
            for (int i = 0; i < rows().length(); i++) {
                JSONObject row = rows().getJSONObject(i); String name = row.optString("name"), status = row.optString("status");
                if (status.equals("approved")) { approvedCount++; if (name.isEmpty()) nameless++; else if (first.isEmpty()) first = name; }
                if (!status.equals("skipped") && name.length() > longest.length()) longest = name;
            }
            final String sample = first.isEmpty() ? getString(R.string.example_name_1) : first, widest = longest;
            base(R.string.title_messages, null, null);
            text(plural(R.plurals.approved_count, approvedCount), 15, MUTED);
            JSONArray variants = data.getJSONArray("variants"); List<EditText> inputs = new ArrayList<>();
            LinearLayout outer;
            for (int i = 0; i < variants.length(); i++) {
                outer = open(SURFACE);
                EditText input = field(getString(R.string.variant_label, i + 1), variants.getString(i)); input.setMinLines(3); input.setGravity(Gravity.TOP | Gravity.START); inputs.add(input);
                chip(getString(R.string.insert_name), () -> { int at = input.hasFocus() ? Math.max(0, input.getSelectionStart()) : input.length(); input.getText().insert(at, "{{name}}"); input.requestFocus(); });
                TextView preview = text("", 15, INK); preview.setBackground(round(BG, 0, 12)); preview.setPadding(dp(12), dp(10), dp(12), dp(10)); ((LinearLayout.LayoutParams) preview.getLayoutParams()).topMargin = dp(10);
                TextView parts = text("", 13, MUTED);
                Runnable count = () -> {
                    preview.setText(ContactRules.render(input.getText().toString(), sample));
                    int[] size = SmsMessage.calculateLength(input.getText().toString().replace("{{name}}", widest), false);
                    parts.setText(size[0] == 1 ? getString(R.string.sms_one) : getString(R.string.sms_parts, size[0])); parts.setTextColor(size[0] == 1 ? MUTED : WARN);
                };
                input.addTextChangedListener(watcher(count)); count.run();
                close(outer);
            }
            final EditText generic;
            if (nameless > 0) {
                outer = open(SURFACE);
                generic = field(plural(R.plurals.generic_label, nameless), data.optString("generic", getString(R.string.default_generic))); generic.setMinLines(3); generic.setGravity(Gravity.TOP | Gravity.START);
                TextView genericParts = text("", 13, MUTED);
                Runnable countGeneric = () -> { int[] size = SmsMessage.calculateLength(generic.getText().toString(), false); genericParts.setText(size[0] == 1 ? getString(R.string.sms_one) : getString(R.string.sms_parts, size[0])); genericParts.setTextColor(size[0] == 1 ? MUTED : WARN); };
                generic.addTextChangedListener(watcher(countGeneric)); countGeneric.run();
                close(outer);
            } else generic = null;
            Runnable persist = () -> { try { JSONArray values = new JSONArray(); for (EditText input : inputs) values.put(input.getText().toString()); data.put("variants", values); if (generic != null) data.put("generic", generic.getText().toString()); save(); } catch (Exception e) { error(e); } };
            beforeLeave = persist;
            quiet(getString(inputs.size() == 3 ? R.string.add_fourth : R.string.remove_fourth), () -> { persist.run(); try { if (inputs.size() == 3) data.getJSONArray("variants").put(""); else data.getJSONArray("variants").remove(3); save(); messages(); } catch (Exception e) { error(e); } });
            outer = open(SURFACE);
            CheckBox consent = new CheckBox(this); consent.setText(R.string.consent); consent.setTextSize(17); consent.setTextColor(INK); consent.setButtonTintList(ColorStateList.valueOf(GREEN)); consent.setMinHeight(dp(48)); page.addView(consent);
            button(getString(R.string.prepare), () -> { try {
                persist.run(); if (!consent.isChecked()) throw new Exception(getString(R.string.err_consent));
                for (EditText input : inputs) { String value = input.getText().toString().trim(); if (value.isEmpty() || value.length() > 600 || value.replace("{{name}}", "").contains("{{")) throw new Exception(getString(R.string.err_variant)); }
                if (generic != null) { String value = generic.getText().toString().trim(); if (value.isEmpty() || value.length() > 600 || value.contains("{{")) throw new Exception(getString(R.string.err_generic)); }
                int count = 0; HashSet<String> phones = new HashSet<>();
                for (int i = 0; i < rows().length(); i++) { JSONObject row = rows().getJSONObject(i); if (row.optString("status").equals("pending")) throw new Exception(getString(R.string.err_review_first)); if (row.optString("status").equals("approved")) { if (!ContactRules.nameOk(row.getString("name"), script()) || !Phones.valid(row.getString("phone"), country) || !phones.add(Phones.normalize(row.getString("phone"), country))) throw new Exception(getString(R.string.err_invalid_rows)); count++; } }
                if (count == 0) throw new Exception(getString(R.string.err_none_approved));
                SecureRandom random = new SecureRandom();
                for (int i = 0; i < rows().length(); i++) { JSONObject row = rows().getJSONObject(i); if (row.optString("status").equals("approved")) row.put("status", "queued").put("text", row.getString("name").isEmpty() ? generic.getText().toString().trim() : ContactRules.render(inputs.get(random.nextInt(inputs.size())).getText().toString(), row.getString("name"))); }
                data.put("prepared", true); save(); beforeLeave = null; home();
            } catch (Exception e) { error(e); } });
            close(outer);
        } catch (Exception e) { error(e); }
    }
    private void permissions() {
        List<String> missing = new ArrayList<>(); for (String permission : new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE}) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (missing.isEmpty()) confirm(); else requestPermissions(missing.toArray(new String[0]), 11);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { super.onRequestPermissionsResult(code, permissions, results); if (code == 11) confirm(); }
    private void confirm() {
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) throw new Exception(getString(R.string.err_permissions));
            List<SubscriptionInfo> sims = getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList(); if (sims == null || sims.isEmpty()) throw new Exception(getString(R.string.err_no_sim));
            String[] labels = sims.stream().map(s -> getString(R.string.sim_label, s.getSimSlotIndex() + 1, s.getDisplayName())).toArray(String[]::new);
            new AlertDialog.Builder(this).setTitle(R.string.choose_sim).setItems(labels, (d, which) -> {
                try { int count = 0; for (int i = 0; i < rows().length(); i++) if (rows().getJSONObject(i).optString("status").equals("queued")) count++;
                    new AlertDialog.Builder(this).setTitle(plural(R.plurals.confirm_title, count)).setMessage(getString(R.string.confirm_message, labels[which], Math.max(1, Math.round(count * 4 / 60f)))).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.send, (dialog, w) -> { startForegroundService(new Intent(this, SmsService.class).putExtra("subscriptionId", sims.get(which).getSubscriptionId())); home(); }).show();
                } catch (Exception e) { error(e); }
            }).setNegativeButton(R.string.cancel, null).show();
        } catch (Exception e) { error(e); }
    }
    @Override public void onBackPressed() { if (screen.equals("home")) super.onBackPressed(); else leave(); }
}
