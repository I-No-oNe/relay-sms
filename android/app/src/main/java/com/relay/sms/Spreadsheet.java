package com.relay.sms;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

final class Spreadsheet {
    static final class ImportError extends IllegalArgumentException {
        final int message;

        ImportError(int message) {
            this.message = message;
        }
    }

    static boolean looksLikePhone(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return value.trim().matches("\\+?[0-9\\s\\-().]+")
                && digits.length() >= 7
                && digits.length() <= 15;
    }

    static List<List<String>> read(InputStream input) throws Exception {
        byte[] bytes = bounded(input, 5_000_000);
        if (bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K') return xlsx(bytes);
        if (bytes.length > 0 && (bytes[0] & 255) == 208) throw new ImportError(R.string.err_xls);
        return csv(new String(bytes, StandardCharsets.UTF_8).replace("\ufeff", ""));
    }

    private static byte[] bounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new ImportError(R.string.err_too_large);
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static XmlPullParser parser(byte[] bytes) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new ByteArrayInputStream(bytes), "UTF-8");
        return parser;
    }

    private static List<List<String>> xlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                byte[] data = bounded(zip, 15_000_000 - expanded);
                expanded += data.length;
                if (entry.getName().equals("xl/sharedStrings.xml")
                        || entry.getName().matches("xl/worksheets/sheet[0-9]+.xml"))
                    files.put(entry.getName(), data);
            }
        }
        List<String> strings = new ArrayList<>();
        if (files.containsKey("xl/sharedStrings.xml")) {
            XmlPullParser xml = parser(files.get("xl/sharedStrings.xml"));
            StringBuilder text = new StringBuilder();
            for (int event = xml.getEventType();
                    event != XmlPullParser.END_DOCUMENT;
                    event = xml.next()) {
                if (event == XmlPullParser.START_TAG && xml.getName().equals("si"))
                    text = new StringBuilder();
                if (event == XmlPullParser.START_TAG && xml.getName().equals("t"))
                    text.append(xml.nextText());
                if (event == XmlPullParser.END_TAG && xml.getName().equals("si"))
                    strings.add(text.toString());
            }
        }
        byte[] sheet = files.get("xl/worksheets/sheet1.xml");
        if (sheet == null) throw new ImportError(R.string.err_no_sheet);
        XmlPullParser xml = parser(sheet);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = null;
        String type = "";
        int column = 0;
        String value = "";
        for (int event = xml.getEventType();
                event != XmlPullParser.END_DOCUMENT;
                event = xml.next()) {
            if (event == XmlPullParser.START_TAG) {
                switch (xml.getName()) {
                    case "row":
                        row = new ArrayList<>();
                        break;
                    case "c":
                        String ref = xml.getAttributeValue(null, "r");
                        column = 0;
                        if (ref != null)
                            for (char c : ref.toCharArray()) {
                                if (c < 'A' || c > 'Z') break;
                                column = column * 26 + c - 'A' + 1;
                            }
                        column = Math.max(0, column - 1);
                        if (column > 100) throw new ImportError(R.string.err_columns);
                        type = xml.getAttributeValue(null, "t");
                        value = "";
                        break;
                    case "v":
                    case "t":
                        value += xml.nextText();
                        break;
                }
            } else if (event == XmlPullParser.END_TAG && xml.getName().equals("c") && row != null) {
                if ("s".equals(type) && !value.isEmpty())
                    value = strings.get(Integer.parseInt(value));
                while (row.size() <= column) row.add("");
                row.set(column, value);
            } else if (event == XmlPullParser.END_TAG
                    && xml.getName().equals("row")
                    && row != null) {
                if (row.stream().anyMatch(v -> !v.trim().isEmpty())) rows.add(row);
                if (rows.size() > MainActivity.MAX_RECIPIENTS + 20)
                    throw new ImportError(R.string.err_too_many);
            }
        }
        return rows;
    }

    static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        char delimiter = text.split("\\n", 2)[0].contains(";") ? ';' : ',';
        for (int i = 0; i <= text.length(); i++) {
            char c = i == text.length() ? '\n' : text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (!quoted && (c == delimiter || c == '\n')) {
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (c == '\n') {
                    if (row.stream().anyMatch(v -> !v.isEmpty())) rows.add(row);
                    row = new ArrayList<>();
                }
            } else if (c != '\r') cell.append(c);
        }
        if (quoted) throw new ImportError(R.string.err_csv_quote);
        if (rows.size() > MainActivity.MAX_RECIPIENTS + 20)
            throw new ImportError(R.string.err_too_many);
        return rows;
    }

    static int[] detect(List<List<String>> rows) {
        for (int r = 0; r < Math.min(20, rows.size()); r++) {
            int phone = -1, name = -1;
            for (int c = 0; c < rows.get(r).size(); c++) {
                String header = rows.get(r).get(c).toLowerCase().trim();
                if (header.matches(
                        ".*(phone|mobile|number|cell|teléfono|telefono|téléphone|telefon|телефон|номер|هاتف|جوال|טלפון|נייד|מספר).*"))
                    phone = c;
                if (header.matches(
                        "name|full name|first name|contact|nombre|nom|имя|اسم|שם|שם מלא|שם פרטי|איש"
                            + " קשר"))
                    name = c;
            }
            if (phone >= 0)
                return new int[] {
                    r + 1,
                    phone,
                    name >= 0 || r + 1 >= rows.size()
                            ? name
                            : nameColumn(rows.get(r + 1), phone, -1)
                };
        }
        for (int r = 0; r < rows.size(); r++)
            for (int c = 0; c < rows.get(r).size(); c++)
                if (looksLikePhone(rows.get(r).get(c)))
                    return new int[] {
                        r,
                        c,
                        nameColumn(rows.get(r), c, rows.get(r).size() > 1 ? (c == 0 ? 1 : 0) : -1)
                    };
        return new int[] {0, 0, -1};
    }

    private static int nameColumn(List<String> row, int phone, int fallback) {
        for (int c = 0; c < row.size(); c++)
            if (c != phone && row.get(c).matches(".*\\p{L}.*")) return c;
        return fallback;
    }
}
