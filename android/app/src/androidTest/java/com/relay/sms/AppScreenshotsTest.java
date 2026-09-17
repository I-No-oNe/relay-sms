package com.relay.sms;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class AppScreenshotsTest {
    @Rule
    public ActivityTestRule<MainActivity> activity =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Before
    public void freshCampaign() {
        File files = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        new File(files, "campaign.json").delete();
        new File(files, "campaign.json.bak").delete();
        activity.launchActivity(null);
    }

    private void shot(String name) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        // The CI emulator sometimes shows a launcher "not responding" dialog.
        UiObject2 anr = UiDevice.getInstance(instrumentation).findObject(By.text("Wait"));
        if (anr != null) anr.click();
        Thread.sleep(700); // let the frame draw
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder = new File(activity.getActivity().getExternalFilesDir(null), "screenshots");
        folder.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }

    private <T extends View> T find(View view, Class<T> type, String text) {
        if (type.isInstance(view)
                && (text == null || ((Button) view).getText().toString().equals(text)))
            return type.cast(view);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                T found = find(((ViewGroup) view).getChildAt(i), type, text);
                if (found != null) return found;
            }
        return null;
    }

    private View root() {
        return activity.getActivity().getWindow().getDecorView();
    }

    private void click(String title) {
        activity.getActivity()
                .runOnUiThread(
                        () -> {
                            Button button = find(root(), Button.class, title);
                            assertNotNull(title, button);
                            assertTrue(button.isEnabled());
                            button.performClick();
                        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private int count(String status) throws Exception {
        JSONArray rows = LocalStore.load(activity.getActivity()).getJSONArray("rows");
        int n = 0;
        for (int i = 0; i < rows.length(); i++)
            if (rows.getJSONObject(i).getString("status").equals(status)) n++;
        return n;
    }

    private String text(int id, Object... args) {
        return activity.getActivity().getString(id, args);
    }

    @Test
    public void reviewFlowAndScreenshots() throws Exception {
        shot("home-phone");
        activity.getActivity()
                .runOnUiThread(
                        () ->
                                activity.getActivity()
                                        .mapping(
                                                Arrays.asList(
                                                        Arrays.asList("Name", "Phone"),
                                                        Arrays.asList(
                                                                "Alex Morgan", "(201) 555-0123"),
                                                        Arrays.asList("Дмитрий", "201-555-0145"),
                                                        Arrays.asList("", "2015550167"))));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        shot("import-phone");
        click(text(R.string.continue_to_review));
        activity.getActivity()
                .runOnUiThread(
                        () ->
                                find(root(), Spinner.class, null)
                                        .setSelection(2)); // Latin letters only
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        shot("review-phone");
        click(text(R.string.approve_next));
        activity.getActivity()
                .runOnUiThread(
                        () ->
                                assertFalse(
                                        find(root(), Button.class, text(R.string.approve_next))
                                                .isEnabled()));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        shot("wrong-script-phone");
        click(text(R.string.skip_recipient));
        shot("no-name-phone");
        click(text(R.string.approve_next));
        shot("messages-phone");
        assertEquals(2, count("approved"));
        assertEquals(1, count("skipped"));
        activity.getActivity()
                .runOnUiThread(() -> find(root(), CheckBox.class, null).setChecked(true));
        click(text(R.string.prepare));
        shot("ready-phone");
        assertEquals(2, count("queued"));
        assertEquals(
                text(R.string.default_generic),
                LocalStore.load(activity.getActivity())
                        .getJSONArray("rows")
                        .getJSONObject(2)
                        .getString("text"));
        assertEquals(
                "+12015550123",
                LocalStore.load(activity.getActivity())
                        .getJSONArray("rows")
                        .getJSONObject(0)
                        .getString("phone"));
    }

    // Same format as whatsapp-contacts-exporter.
    @Test
    public void readsWhatsappExporterXlsx() throws Exception {
        String sheet =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet"
                    + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row"
                    + " r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t"
                    + " xml:space=\"preserve\">Number</t></is></c><c r=\"B1\""
                    + " t=\"inlineStr\"><is><t xml:space=\"preserve\">Name</t></is></c></row><row"
                    + " r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t"
                    + " xml:space=\"preserve\">+12015550123</t></is></c><c r=\"B2\""
                    + " t=\"inlineStr\"><is><t xml:space=\"preserve\">Alex"
                    + " 🌸</t></is></c></row><row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t"
                    + " xml:space=\"preserve\"></t></is></c><c r=\"B3\" t=\"inlineStr\"><is><t"
                    + " xml:space=\"preserve\">Hidden</t></is></c></row></sheetData></worksheet>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<List<String>> table = Spreadsheet.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(Arrays.asList("Number", "Name"), table.get(0));
        assertEquals(Arrays.asList("+12015550123", "Alex 🌸"), table.get(1));
        assertArrayEquals(new int[] {1, 0, 1}, Spreadsheet.detect(table));
        assertEquals("Alex", ContactRules.cleanName(table.get(1).get(1)));
    }

    @Test
    public void sevenHundredRecipients() throws Exception {
        List<List<String>> table = new ArrayList<>();
        table.add(Arrays.asList("Name", "Phone"));
        for (int i = 0; i < 700; i++)
            table.add(Arrays.asList("Guest", i % 50 == 0 ? "12" : String.format("201555%04d", i)));
        activity.getActivity().runOnUiThread(() -> activity.getActivity().mapping(table));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        click(text(R.string.continue_to_review));
        shot("bulk-700-phone");
        click(text(R.string.skip_invalid_phones, 14));
        click(text(R.string.approve_valid, 686));
        assertEquals(686, count("approved"));
        assertEquals(14, count("skipped"));
    }
}
