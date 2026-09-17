package com.relay.sms;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import java.util.Locale;

final class Phones {
    static String country(Context context) {
        TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
        String iso = telephony == null ? "" : telephony.getSimCountryIso();
        if ((iso == null || iso.isEmpty()) && telephony != null) iso = telephony.getNetworkCountryIso();
        if (iso == null || iso.isEmpty()) iso = Locale.getDefault().getCountry();
        return iso.toUpperCase(Locale.ROOT);
    }
    // E.164 form, or null when the number isn't valid.
    static String e164(String raw, String country) {
        String value = raw.trim();
        if (value.startsWith("00")) value = "+" + value.substring(2);
        String result = PhoneNumberUtils.formatNumberToE164(value, country);
        if (result == null && !value.startsWith("+")) result = PhoneNumberUtils.formatNumberToE164("+" + value.replaceAll("[^0-9]", ""), country);
        return result;
    }
    static String normalize(String raw, String country) { String result = e164(raw, country); return result == null ? raw.trim() : result; }
    static boolean valid(String raw, String country) { return e164(raw, country) != null; }
    static String display(String phone, String country) { String result = PhoneNumberUtils.formatNumber(phone, country); return result == null ? phone : result; }
}
