package com.relay.sms;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

public final class SpreadsheetTest {
    @Test
    public void whatsappExporterHeader() {
        assertArrayEquals(
                new int[] {1, 0, 1},
                Spreadsheet.detect(
                        Arrays.asList(
                                Arrays.asList("Number", "Name"),
                                Arrays.asList("+12015550123", "Alex"))));
    }

    @Test
    public void headerlessSheetPicksNameColumn() {
        assertArrayEquals(
                new int[] {0, 1, 2},
                Spreadsheet.detect(
                        Arrays.asList(Arrays.asList("17", "(201) 555-0123", "Alex Morgan"))));
    }

    @Test
    public void unnamedHeaderFallsBackToTextColumn() {
        assertArrayEquals(
                new int[] {1, 0, 2},
                Spreadsheet.detect(
                        Arrays.asList(
                                Arrays.asList("Phone", "City", "Client"),
                                Arrays.asList("2015550123", "", "Alex"))));
    }

    @Test
    public void phoneShapes() {
        assertTrue(Spreadsheet.looksLikePhone("+44 20 7946 0958"));
        assertFalse(Spreadsheet.looksLikePhone("Room 1015555"));
        assertFalse(Spreadsheet.looksLikePhone("17"));
    }
}
