package de.gergh0stface.ghostyclaim.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for translating & color codes and &#RRGGBB hex codes.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    /**
     * Translates & color codes and &#RRGGBB hex codes in the given string.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        text = translateHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Translates &#RRGGBB hex codes into Minecraft color format.
     */
    private static String translateHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Strips all color codes from the given string.
     */
    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Colorizes a string and replaces newline literals.
     */
    public static String colorizeLines(String text) {
        return colorize(text).replace("\\n", "\n");
    }
}
