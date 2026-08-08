package com.hikabrain.plugin.cosmetics;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Un cosmétique achetable à la boutique (voir {@link CosmeticRegistry} pour la liste
 * complète, et {@link CosmeticManager} pour l'achat/l'équipement).
 *
 * Purement visuel — aucun cosmétique ne donne le moindre avantage en jeu, et ils ne sont
 * de toute façon visibles QUE hors de toute arène HikaBrain (voir CosmeticManager).
 */
public class Cosmetic {

    /** Rareté : détermine la couleur d'affichage et sert de repère visuel de prix/prestige. */
    public enum Rarity {
        COMMON("Commun", org.bukkit.ChatColor.GRAY),
        RARE("Rare", org.bukkit.ChatColor.AQUA),
        EPIC("Épique", org.bukkit.ChatColor.LIGHT_PURPLE),
        LEGENDARY("Légendaire", org.bukkit.ChatColor.GOLD);

        private final String label;
        private final org.bukkit.ChatColor color;

        Rarity(String label, org.bukkit.ChatColor color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() { return label; }
        public org.bukkit.ChatColor getColor() { return color; }
    }

    private final String id;
    private final String displayName;
    private final CosmeticCategory category;
    private final Rarity rarity;
    private final int price;
    private final int unlockLevel;

    // Paramètres spécifiques à la catégorie (seuls certains sont utilisés selon le type)
    private Material iconMaterial = Material.PAPER;
    private DyeColor leatherColor;
    private Particle particle;
    private Color particleColor;
    private String tagText;

    private Cosmetic(String id, String displayName, CosmeticCategory category, Rarity rarity, int price, int unlockLevel) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.rarity = rarity;
        this.price = price;
        this.unlockLevel = unlockLevel;
    }

    // ── Fabriques par catégorie ──────────────────────────────────────────────────

    public static Cosmetic hat(String id, String name, Rarity rarity, int price, int level, Material material) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.HAT, rarity, price, level);
        c.iconMaterial = material;
        return c;
    }

    public static Cosmetic leatherHat(String id, String name, Rarity rarity, int price, int level, DyeColor color) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.HAT, rarity, price, level);
        c.iconMaterial = Material.LEATHER_HELMET;
        c.leatherColor = color;
        return c;
    }

    public static Cosmetic particle(String id, String name, Rarity rarity, int price, int level, Particle particle, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.PARTICLE, rarity, price, level);
        c.particle = particle;
        c.iconMaterial = icon;
        return c;
    }

    public static Cosmetic dustParticle(String id, String name, Rarity rarity, int price, int level, Color color, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.PARTICLE, rarity, price, level);
        c.particle = Particle.DUST;
        c.particleColor = color;
        c.iconMaterial = icon;
        return c;
    }

    public static Cosmetic trail(String id, String name, Rarity rarity, int price, int level, Particle particle, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.TRAIL, rarity, price, level);
        c.particle = particle;
        c.iconMaterial = icon;
        return c;
    }

    public static Cosmetic dustTrail(String id, String name, Rarity rarity, int price, int level, Color color, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.TRAIL, rarity, price, level);
        c.particle = Particle.DUST;
        c.particleColor = color;
        c.iconMaterial = icon;
        return c;
    }

    public static Cosmetic tag(String id, String name, Rarity rarity, int price, int level, String tagText, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.TAG, rarity, price, level);
        c.tagText = tagText;
        c.iconMaterial = icon;
        return c;
    }

    public static Cosmetic entrance(String id, String name, Rarity rarity, int price, int level, Particle particle, Material icon) {
        Cosmetic c = new Cosmetic(id, name, CosmeticCategory.ENTRANCE, rarity, price, level);
        c.particle = particle;
        c.iconMaterial = icon;
        return c;
    }

    // ── Accesseurs ────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public CosmeticCategory getCategory() { return category; }
    public Rarity getRarity() { return rarity; }
    public int getPrice() { return price; }
    public int getUnlockLevel() { return unlockLevel; }
    public Material getIconMaterial() { return iconMaterial; }
    public DyeColor getLeatherColor() { return leatherColor; }
    public Particle getParticle() { return particle; }
    public Color getParticleColor() { return particleColor; }
    public String getTagText() { return tagText; }
}
