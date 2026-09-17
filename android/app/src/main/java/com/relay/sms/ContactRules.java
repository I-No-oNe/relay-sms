package com.relay.sms;

final class ContactRules {
    static final String[] SCRIPTS = {"any", "Hebrew", "Latin", "Arabic", "Cyrillic"};
    static boolean nameOk(String name, String script) {
        String value = name.trim();
        if (value.isEmpty() || script.equals("any")) return true;
        return value.matches("[\\p{Is" + script + "}\\p{M} .'\\-\u05F3\u05F4]+") && value.matches(".*\\p{Is" + script + "}.*");
    }
    static String defaultScript(String language) {
        switch (language) {
            case "he": case "iw": return "Hebrew";
            case "ar": return "Arabic";
            case "ru": case "uk": case "be": case "bg": case "sr": case "mk": return "Cyrillic";
            default: return "any";
        }
    }
    // Remove emoji and invisible marks that WhatsApp names often contain.
    static String cleanName(String raw) { return raw.replaceAll("[\\p{So}\\p{Sk}\\p{Cf}\\p{Cs}\\p{Co}\\uFE0E\\uFE0F\\u20E3]", " ").replaceAll("\\s+", " ").trim(); }
    static String render(String text, String name) { return text.replace("{{name}}", name); }
}
