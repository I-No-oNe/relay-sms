package com.relay.sms;

import static org.junit.Assert.*;

import org.junit.Test;

public final class ContactRulesTest {
    @Test
    public void namesFollowTheChosenScript() {
        assertTrue(ContactRules.nameOk("Alex Morgan", "Latin"));
        assertFalse(ContactRules.nameOk("Дмитрий", "Latin"));
        assertTrue(ContactRules.nameOk("Дмитрий", "Cyrillic"));
        assertTrue(ContactRules.nameOk("سارة", "Arabic"));
        assertTrue(ContactRules.nameOk("נועה", "Hebrew"));
        assertFalse(ContactRules.nameOk("Noa נועה", "Hebrew"));
        assertTrue(ContactRules.nameOk("Anything 123", "any"));
        assertTrue(ContactRules.nameOk("", "Latin"));
    }

    @Test
    public void defaultScriptComesFromLanguage() {
        assertEquals("any", ContactRules.defaultScript("en"));
        assertEquals("Cyrillic", ContactRules.defaultScript("ru"));
        assertEquals("Hebrew", ContactRules.defaultScript("iw"));
    }

    @Test
    public void whatsappNamesAreCleaned() {
        assertEquals("Alex Morgan", ContactRules.cleanName("\u200fAlex 🌸 Morgan\u200e"));
        assertEquals("Sam", ContactRules.cleanName("Sam 👍🏽"));
        assertEquals("", ContactRules.cleanName("❤️"));
    }

    @Test
    public void replacementIsLiteral() {
        assertEquals("Hi Sam", ContactRules.render("Hi {{name}}", "Sam"));
    }
}
