package com.example.myjarvis;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PhoneCallCommands {
    private PhoneCallCommands() {
    }

    static String targetFrom(String phrase) {
        String command = phrase.trim().replaceFirst("(?iu)^лира\\s+", "");
        for (String prefix : List.of("позвони", "позвонить", "набери", "набрать")) {
            if (command.equalsIgnoreCase(prefix)) {
                return "";
            }
            if (command.regionMatches(true, 0, prefix, 0, prefix.length())
                    && Character.isWhitespace(command.charAt(prefix.length()))) {
                return command.substring(prefix.length()).trim()
                        .replaceFirst("(?iu)^(?:по\\s+)?номеру\\s+", "");
            }
        }
        return null;
    }

    static boolean isPhoneNumber(String target) {
        String candidate = target.trim();
        return candidate.matches("[+()\\d\\s.-]+")
                && candidate.replaceAll("\\D", "").length() >= 3;
    }

    static List<Contact> findContacts(Context context, String name) {
        String query = name.trim();
        Map<String, Contact> matches = new LinkedHashMap<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + query + "%"},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")) {
            if (cursor == null) {
                return List.of();
            }
            int nameColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER);
            String normalizedQuery = normalize(query);
            while (cursor.moveToNext()) {
                String displayName = cursor.getString(nameColumn);
                String number = cursor.getString(numberColumn);
                if (displayName == null || number == null
                        || !normalize(displayName).contains(normalizedQuery)) {
                    continue;
                }
                String key = normalize(number);
                matches.putIfAbsent(key, new Contact(displayName, number));
            }
        }
        return new ArrayList<>(matches.values());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static final class Contact {
        final String name;
        final String number;

        Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }
}
