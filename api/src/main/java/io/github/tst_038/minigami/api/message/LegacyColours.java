package io.github.tst_038.minigami.api.message;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The colour codes server owners know from other plugins, {@code &c}, {@code &l} and {@code &#ff8800} (or with
 * {@code §}), turned into the MiniMessage that every text is read as, so a text players read may use either. The rest of
 * the text, MiniMessage included, is kept as it is.
 */
public final class LegacyColours {

    private static final Pattern CODE = Pattern.compile("[&§](?:#([0-9a-fA-F]{6})|([0-9a-fk-orA-FK-OR]))");
    private static final String COLOURS = "0123456789abcdef";
    private static final String[] COLOUR_TAGS = { "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white" };
    private static final String DECORATIONS = "klmno";
    private static final String[] DECORATION_TAGS = { "obfuscated", "bold", "strikethrough", "underlined", "italic" };

    private LegacyColours() {
    }

    /** Whether the text opens with a code, which YAML would otherwise read as an anchor. */
    public static boolean startsWithCode(String text) {
        var matcher = CODE.matcher(text.strip());
        return matcher.lookingAt();
    }

    public static String toMiniMessage(String text) {
        var matcher = CODE.matcher(text);
        var out = new StringBuilder();
        // A colour code ends the bold, italic and so on before it, as it does in the legacy format; a MiniMessage colour tag doesn't.
        boolean decorated = false;
        while (matcher.find()) {
            String tag;
            if (matcher.group(1) != null) {
                tag = colour("#" + matcher.group(1).toLowerCase(Locale.ROOT), decorated);
                decorated = false;
            } else {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                if (code == 'r') {
                    tag = "<reset>";
                    decorated = false;
                } else if (DECORATIONS.indexOf(code) >= 0) {
                    tag = "<" + DECORATION_TAGS[DECORATIONS.indexOf(code)] + ">";
                    decorated = true;
                } else {
                    tag = colour(COLOUR_TAGS[COLOURS.indexOf(code)], decorated);
                    decorated = false;
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String colour(String name, boolean decorated) {
        return (decorated ? "<reset><" : "<") + name + ">";
    }
}
