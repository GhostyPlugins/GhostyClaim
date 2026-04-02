package de.gergh0stface.ghostyclaim.manager;

import de.gergh0stface.ghostyclaim.GhostyClaim;
import de.gergh0stface.ghostyclaim.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages language files and message lookups.
 * Messages are stored in lang/{language}.yml and support & / hex colors.
 */
public class LangManager {

    private final GhostyClaim plugin;
    private FileConfiguration lang;
    private String prefix;

    public LangManager(GhostyClaim plugin) {
        this.plugin = plugin;
    }

    /**
     * Load (or reload) the language file based on the config setting.
     * Never throws — a missing or corrupt lang file degrades gracefully.
     */
    public void load() {
        String language = plugin.getConfig().getString("language", "en");
        File langDir  = new File(plugin.getDataFolder(), "lang");
        File langFile = new File(langDir, language + ".yml");

        langDir.mkdirs();

        // Export bundled lang files only when they're actually inside the JAR
        exportDefaultLang("en");
        exportDefaultLang("de");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Lang file not found: lang/" + language
                    + ".yml — falling back to 'en'.");
            langFile = new File(langDir, "en.yml");
        }

        // Load from disk (file may still not exist if JAR had no resources)
        if (langFile.exists()) {
            lang = YamlConfiguration.loadConfiguration(langFile);
        } else {
            plugin.getLogger().warning("No lang file available on disk. "
                    + "Messages will show raw keys. "
                    + "Place en.yml into plugins/GhostyClaim/lang/ to fix this.");
            lang = new YamlConfiguration();
        }

        // Merge with bundled defaults so missing keys always have a value
        InputStream defaultStream = plugin.getResource("lang/" + language + ".yml");
        if (defaultStream == null) defaultStream = plugin.getResource("lang/en.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            lang.setDefaults(defaults);
        }

        this.prefix = ColorUtil.colorize(
                lang.getString("prefix", "&8[&bGhostyClaim&8] &r"));
        plugin.getLogger().info("Language loaded: " + language);
    }

    /**
     * Get a translated message by key, with placeholder replacements.
     * Replacements are passed as pairs: "key1", "value1", "key2", "value2", ...
     */
    public String get(String key, Object... replacements) {
        String raw = lang.getString(key, "&cMissing message: " + key);
        raw = applyPlaceholders(raw, replacements);
        raw = raw.replace("{prefix}", prefix);
        return ColorUtil.colorize(raw);
    }

    /**
     * Get a list of translated messages (e.g. lore or multi-line messages).
     */
    public List<String> getList(String key, Object... replacements) {
        List<String> list = lang.getStringList(key);
        return list.stream()
                .map(line -> {
                    line = applyPlaceholders(line, replacements);
                    line = line.replace("{prefix}", prefix);
                    return ColorUtil.colorize(line);
                })
                .collect(Collectors.toList());
    }

    /**
     * Send a message to a player. Supports multi-line (split by \n).
     */
    public void send(Player player, String key, Object... replacements) {
        String msg = get(key, replacements);
        for (String line : msg.split("\n")) {
            player.sendMessage(line);
        }
    }

    /**
     * Send a list of messages to a player.
     */
    public void sendList(Player player, String key, Object... replacements) {
        for (String line : getList(key, replacements)) {
            player.sendMessage(line);
        }
    }

    /**
     * Get the colored prefix.
     */
    public String getPrefix() {
        return prefix;
    }

    // ─── Helpers ──────────────────────────────────────────────

    private String applyPlaceholders(String text, Object[] replacements) {
        if (replacements.length % 2 != 0) return text;
        for (int i = 0; i < replacements.length; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String value = String.valueOf(replacements[i + 1]);
            text = text.replace(placeholder, value);
        }
        return text;
    }

    private void exportDefaultLang(String langCode) {
        File file = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            // Only call saveResource if the resource actually exists inside the JAR
            if (plugin.getResource("lang/" + langCode + ".yml") != null) {
                plugin.saveResource("lang/" + langCode + ".yml", false);
            } else {
                plugin.getLogger().warning("Lang resource not found in JAR: lang/"
                        + langCode + ".yml — skipping export.");
            }
        }
    }

    /**
     * Build a placeholder map for reuse.
     */
    public static Map<String, String> placeholders(Object... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return map;
    }
}
