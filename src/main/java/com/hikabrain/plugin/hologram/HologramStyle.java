package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Apparence PARTAGÉE par tous les hologrammes du plugin (statistiques personnelles et
 * classements top 10), lue depuis config.yml section "hologram-style" — voir
 * {@link #load(HikaBrainPlugin)}. Une seule source de vérité pour que tous les
 * hologrammes restent visuellement cohérents, et facilement personnalisables sans
 * toucher au code.
 */
public class HologramStyle {

    private final Color backgroundColor; // null = fond par défaut vanilla
    private final boolean transparentBackground; // true = pas de fond du tout ("none")
    private final boolean seeThrough;
    private final boolean shadow;
    private final Display.Billboard billboard;
    private final int lineWidth;
    private final double defaultScale;

    private HologramStyle(Color backgroundColor, boolean transparentBackground, boolean seeThrough,
                           boolean shadow, Display.Billboard billboard, int lineWidth, double defaultScale) {
        this.backgroundColor = backgroundColor;
        this.transparentBackground = transparentBackground;
        this.seeThrough = seeThrough;
        this.shadow = shadow;
        this.billboard = billboard;
        this.lineWidth = lineWidth;
        this.defaultScale = defaultScale;
    }

    /** Charge le style depuis config.yml (section "hologram-style"), avec des valeurs par défaut sûres. */
    public static HologramStyle load(HikaBrainPlugin plugin) {
        var cfg = plugin.getConfig();
        String backgroundRaw = cfg.getString("hologram-style.background", "default");
        boolean seeThrough = cfg.getBoolean("hologram-style.see-through", false);
        boolean shadow = cfg.getBoolean("hologram-style.shadow", true);
        String billboardRaw = cfg.getString("hologram-style.billboard", "CENTER");
        int lineWidth = cfg.getInt("hologram-style.line-width", 200);
        double defaultScale = cfg.getDouble("hologram-style.scale", 1.0);

        Color backgroundColor = null;
        boolean transparentBackground = false;

        if ("none".equalsIgnoreCase(backgroundRaw)) {
            transparentBackground = true;
        } else if (!"default".equalsIgnoreCase(backgroundRaw)) {
            backgroundColor = parseArgb(backgroundRaw, plugin);
        }

        Display.Billboard billboard;
        try {
            billboard = Display.Billboard.valueOf(billboardRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[HikaBrain] 'hologram-style.billboard' invalide ('" + billboardRaw + "'), utilisation de CENTER par défaut.");
            billboard = Display.Billboard.CENTER;
        }

        return new HologramStyle(backgroundColor, transparentBackground, seeThrough, shadow, billboard, lineWidth, defaultScale);
    }

    /** Parse une couleur au format "#AARRGGBB" (ou "#RRGGBB", alpha 255 implicite). */
    private static Color parseArgb(String hex, HikaBrainPlugin plugin) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            if (clean.length() == 6) clean = "FF" + clean; // pas d'alpha fourni -> opaque
            long value = Long.parseLong(clean, 16);
            int alpha = (int) ((value >> 24) & 0xFF);
            int red   = (int) ((value >> 16) & 0xFF);
            int green = (int) ((value >> 8) & 0xFF);
            int blue  = (int) (value & 0xFF);
            return Color.fromARGB(alpha, red, green, blue);
        } catch (Exception e) {
            plugin.getLogger().warning("[HikaBrain] 'hologram-style.background' invalide ('" + hex + "'), utilisation du fond par défaut.");
            return null;
        }
    }

    public double getDefaultScale() {
        return defaultScale;
    }

    /**
     * Applique ce style à un TextDisplay fraîchement créé (fond, transparence, ombre,
     * orientation, largeur de ligne), plus une échelle précise (indépendante du style
     * partagé, pour permettre des tailles différentes par hologramme).
     */
    public void apply(TextDisplay display, double scale) {
        if (transparentBackground) {
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setDefaultBackground(false);
        } else if (backgroundColor != null) {
            display.setBackgroundColor(backgroundColor);
            display.setDefaultBackground(false);
        } else {
            display.setDefaultBackground(true);
        }

        display.setSeeThrough(seeThrough);
        display.setShadowed(shadow);
        display.setBillboard(billboard);
        display.setLineWidth(lineWidth);
        display.setPersistent(true);
        display.setInvulnerable(true);

        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f((float) scale, (float) scale, (float) scale),
                new Quaternionf()
        ));
    }
}
