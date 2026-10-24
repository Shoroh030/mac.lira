package com.example.myjarvis;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TelegramDraftCommand {
    private static final Pattern COMMAND = Pattern.compile(
            "(?iu)^(?:лира\\s+)?(?:открой|открыть)\\s+чат\\s+(?:в\\s+)?"
                    + "(?:телеграм(?:м)?|telegram)\\s+с\\s+(.+?)\\s+"
                    + "(?:и\\s+)?(?:напиши|отправь)\\s+(.+)$");
    private static final Pattern USERNAME = Pattern.compile("@?([A-Za-z0-9_]{5,32})");

    private TelegramDraftCommand() {
    }

    @Nullable
    static Draft parse(String phrase) {
        Matcher matcher = COMMAND.matcher(phrase.trim());
        if (!matcher.matches()) {
            return null;
        }
        String recipient = matcher.group(1).trim();
        String message = matcher.group(2).trim();
        if (recipient.isEmpty() || message.isEmpty()) {
            return null;
        }
        return new Draft(recipient, message);
    }

    static Intent openTelegramDraft(Draft draft, String packageName) {
        Matcher username = USERNAME.matcher(draft.recipient);
        if (username.matches() && draft.recipient.startsWith("@")) {
            String encodedText = URLEncoder.encode(draft.message, StandardCharsets.UTF_8);
            Uri uri = Uri.parse("https://t.me/" + username.group(1) + "?text=" + encodedText);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, draft.message);
        share.setPackage(packageName);
        return Intent.createChooser(share, "Выбери чат «" + draft.recipient + "» в Telegram");
    }

    static final class Draft {
        final String recipient;
        final String message;

        Draft(String recipient, String message) {
            this.recipient = recipient;
            this.message = message;
        }
    }
}
