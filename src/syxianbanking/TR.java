package syxianbanking;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Internationalization (i18n) system for the mod.
 *
 * Translations are loaded lazily on the first call to s(). Two fallback layers
 * ensure no key ever appears without a display string:
 *   1. Language detected from the player's LauncherSettings.txt.
 *   2. English (en.properties) — always loaded as the base fallback.
 *   3. If even the fallback lacks the key, the key itself is returned as text.
 *
 * To add a new language, create lang/<code>.properties and register the code in normalize().
 */
public final class TR {
    private static final String     DEFAULT  = "en";
    private static final Properties FALLBACK = new Properties(); // always English
    private static final Properties ACTIVE   = new Properties(); // player's language
    private static boolean loaded;

    private TR() {}

    /** Returns the localized string for key, or the key itself if no translation is found. */
    public static CharSequence s(String key) {
        ensureLoaded();
        String value = ACTIVE.getProperty(key);
        if (value == null) value = FALLBACK.getProperty(key);
        return value == null ? key : value;
    }

    // Loads property files on first access. The game runs single-threaded so no synchronization needed.
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load(FALLBACK, DEFAULT);
        String language = detectLanguage();
        if (!DEFAULT.equals(language)) load(ACTIVE, language);
    }

    private static void load(Properties target, String language) {
        String path = "/syxianbanking/lang/" + language + ".properties";
        try (InputStream in = TR.class.getResourceAsStream(path)) {
            if (in != null) target.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    // Reads the language configured in the Songs of Syx launcher settings file.
    // Returns "en" on any failure (missing file, unexpected format, etc.).
    private static String detectLanguage() {
        String appData = System.getenv("APPDATA");
        Path settings  = appData == null || appData.isEmpty()
                ? Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "songsofsyx", "settings", "LauncherSettings.txt")
                : Paths.get(appData, "songsofsyx", "settings", "LauncherSettings.txt");
        try {
            String text = new String(Files.readAllBytes(settings), StandardCharsets.UTF_8);
            int key = text.indexOf("LANGUAGE");
            if (key < 0) return DEFAULT;
            int q1 = text.indexOf('"', key);
            int q2 = q1 < 0 ? -1 : text.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return DEFAULT;
            return normalize(text.substring(q1 + 1, q2));
        } catch (IOException ignored) {
            return DEFAULT;
        }
    }

    // Maps the launcher language code to the filename supported by the mod.
    // Unsupported languages fall back to English.
    private static String normalize(String language) {
        if (language == null || language.isEmpty()) return DEFAULT;
        switch (language) {
            case "cs": case "de": case "es-ES": case "fr": case "hu":
            case "it": case "ja": case "ko":   case "nl": case "pl":
            case "pt-BR": case "ru": case "tr": case "uk":
            case "zh-CN": case "zh-TW":
                return language;
        }
        if (language.startsWith("es")) return "es-ES";
        if (language.startsWith("pt")) return "pt-BR";
        if (language.startsWith("zh")) return language.contains("TW") || language.contains("tw") ? "zh-TW" : "zh-CN";
        return DEFAULT;
    }
}
