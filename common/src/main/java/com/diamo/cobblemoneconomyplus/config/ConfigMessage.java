package com.diamo.cobblemoneconomyplus.config;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A problem found in a config file, kept as a translation key plus its English text. Chat shows it in each
 * player's language ({@link #component()}: the key under {@code config.cobblemon_economy_plus.}, with the English
 * text as the fallback), the server log and tests use the English text ({@link #text()}).
 *
 * <p>The English text uses Minecraft's translation placeholders only: {@code %s}, {@code %1$s}, and {@code %%}.
 * Arguments may themselves be messages (for example the reason inside "skipped shop #2: ...").
 */
public record ConfigMessage(String key, String english, List<Object> args) {
    public static final String PREFIX = "config.cobblemon_economy_plus.";

    public ConfigMessage {
        List<Object> copy = new ArrayList<>(args.size());
        for (Object arg : args) {
            copy.add(arg instanceof ConfigMessage ? arg : String.valueOf(arg));
        }
        args = List.copyOf(copy);
    }

    public static ConfigMessage of(String key, String english, Object... args) {
        return new ConfigMessage(key, english, List.of(args.length == 0 ? new Object[0] : nonNull(args)));
    }

    /** Text that has no translation (for example a message from the JSON library), shown as it is. */
    public static ConfigMessage raw(String text) {
        return of("raw", "%s", text);
    }

    /** "shops.json: <this>". */
    public ConfigMessage inFile(String file) {
        return of("in_file", "%s: %s", file, this);
    }

    /** The English text, for the server log. */
    public String text() {
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            Object arg = args.get(i);
            values[i] = arg instanceof ConfigMessage message ? message.text() : arg;
        }
        return String.format(Locale.ROOT, english, values);
    }

    /** The message in the viewer's language (English when the key is not translated). */
    public Component component() {
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            Object arg = args.get(i);
            values[i] = arg instanceof ConfigMessage message ? message.component() : arg;
        }
        return Component.translatableWithFallback(PREFIX + key, english, values);
    }

    @Override
    public String toString() {
        return text();
    }

    private static Object[] nonNull(Object[] args) {
        Object[] copy = args.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] == null) {
                copy[i] = "null";
            }
        }
        return copy;
    }
}
