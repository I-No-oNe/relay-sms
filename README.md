# Relay SMS

Android app that sends personalized SMS to a contact list from the phone's own SIM. Works fully offline.

- Import `.xlsx` or CSV (name and phone columns, up to 1,000 rows)
- Review recipients before sending, optionally requiring names in a specific alphabet
- Rows without a name get a general message
- Write 3-4 message variants with `{{name}}`, one is picked per recipient
- Pick a SIM, send, stop at any time

Phone numbers are parsed using the SIM's country. Available in English and Hebrew. Android 8+.

## Build

Requires Java 21 and Android SDK 35.

```sh
cd android
./gradlew :app:assembleDebug
```

Tests:

```sh
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

## Translations

Strings live in `android/app/src/main/res/values*/strings.xml`. Add a `values-<language>` folder to add a language.
