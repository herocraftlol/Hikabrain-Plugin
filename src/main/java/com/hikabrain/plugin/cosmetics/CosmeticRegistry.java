package com.hikabrain.plugin.cosmetics;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hikabrain.plugin.cosmetics.Cosmetic.Rarity.*;

/**
 * Catalogue complet des cosmétiques achetables (environ 50, voir décompte en bas de
 * fichier). Les prix et niveaux requis sont volontairement élevés et progressifs — ce
 * n'est PAS censé être facile à obtenir : les cosmétiques les plus stylés (légendaires)
 * représentent plusieurs SEMAINES de jeu régulier, réservés aux joueurs assidus/élites,
 * pas juste quelques bonnes parties.
 *
 * Repères de prix (voir le barème de points dans LevelManager : ~1-15 pts/action, un
 * joueur assidu gagne grossièrement 300-600 points par jour de jeu réel) :
 *   - Commun    : 200 à 600 pts     (quelques parties à 1-2 jours)
 *   - Rare      : 700 à 2 200 pts    (une petite semaine de jeu régulier)
 *   - Épique    : 2 500 à 5 000 pts  (plusieurs semaines, joueur assidu)
 *   - Légendaire: 6 000 à 15 000 pts (plusieurs semaines à mois, réservé à l'élite)
 * Chaque cosmétique exige AUSSI un niveau minimum (voir LevelManager), pour empêcher de
 * "farmer" les points sur une courte période intense sans avoir vraiment le niveau/
 * l'ancienneté de jeu attendue.
 */
public final class CosmeticRegistry {

    private static final List<Cosmetic> ALL = new ArrayList<>();
    private static final Map<String, Cosmetic> BY_ID = new LinkedHashMap<>();

    private static void register(Cosmetic cosmetic) {
        ALL.add(cosmetic);
        BY_ID.put(cosmetic.getId(), cosmetic);
    }

    static {
        // ═══════════════════════ CHAPEAUX (15) ═══════════════════════
        // Casquettes en cuir teint : simples et abordables (communes/rares)
        register(Cosmetic.leatherHat("hat_red",    "&cCasquette Rouge",    COMMON, 200, 0, DyeColor.RED));
        register(Cosmetic.leatherHat("hat_blue",   "&9Casquette Bleue",    COMMON, 200, 0, DyeColor.BLUE));
        register(Cosmetic.leatherHat("hat_lime",   "&aCasquette Verte",    COMMON, 200, 0, DyeColor.LIME));
        register(Cosmetic.leatherHat("hat_yellow", "&eCasquette Jaune",    COMMON, 300, 1, DyeColor.YELLOW));
        register(Cosmetic.leatherHat("hat_black",  "&8Casquette Noire",    COMMON, 300, 1, DyeColor.BLACK));
        register(Cosmetic.leatherHat("hat_white",  "&fCasquette Blanche",  COMMON, 300, 1, DyeColor.WHITE));
        register(Cosmetic.leatherHat("hat_pink",   "&dCasquette Rose",     RARE,   500, 2, DyeColor.PINK));
        register(Cosmetic.leatherHat("hat_orange", "&6Casquette Orange",   RARE,   500, 2, DyeColor.ORANGE));
        register(Cosmetic.leatherHat("hat_purple", "&5Casquette Violette", RARE,   650, 3, DyeColor.PURPLE));
        register(Cosmetic.leatherHat("hat_cyan",   "&bCasquette Cyan",     RARE,   650, 3, DyeColor.CYAN));
        // Têtes de mobs : plus stylées, donc plus chères
        register(Cosmetic.hat("hat_zombie",  "&2Tête de Zombie",           RARE,      1100, 4,  Material.ZOMBIE_HEAD));
        register(Cosmetic.hat("hat_skeleton","&7Tête de Squelette",        RARE,      1300, 5,  Material.SKELETON_SKULL));
        register(Cosmetic.hat("hat_wither",  "&8Tête de Wither Squelette", EPIC,      3200, 10, Material.WITHER_SKELETON_SKULL));
        register(Cosmetic.hat("hat_creeper", "&2Tête de Creeper",          EPIC,      3600, 11, Material.CREEPER_HEAD));
        register(Cosmetic.hat("hat_dragon",  "&5&lTête de Dragon",         LEGENDARY, 11000, 20, Material.DRAGON_HEAD));

        // ═══════════════════════ PARTICULES (15) ═══════════════════════
        register(Cosmetic.particle("p_flame",     "&cAura de Flammes",     COMMON, 350,  1,  Particle.FLAME,           Material.BLAZE_POWDER));
        register(Cosmetic.particle("p_soulflame", "&bFeu d'Âme",           COMMON, 400,  1,  Particle.SOUL_FIRE_FLAME, Material.SOUL_TORCH));
        register(Cosmetic.particle("p_heart",     "&dCœurs Flottants",     COMMON, 400,  2,  Particle.HEART,           Material.POPPY));
        register(Cosmetic.particle("p_note",      "&aNotes de Musique",    COMMON, 450,  2,  Particle.NOTE,            Material.NOTE_BLOCK));
        register(Cosmetic.particle("p_smoke",     "&7Fumée Mystique",      COMMON, 400,  2,  Particle.LARGE_SMOKE,     Material.CAMPFIRE));
        register(Cosmetic.dustParticle("p_pink",  "&dPoussière Rose",      COMMON, 450,  2,  Color.fromRGB(255, 105, 180), Material.PINK_DYE));
        register(Cosmetic.particle("p_cloud",     "&fAura Nuageuse",       RARE,   800,  3,  Particle.CLOUD,           Material.WHITE_WOOL));
        register(Cosmetic.particle("p_ash",       "&8Cendres",             RARE,   850,  4,  Particle.ASH,             Material.GUNPOWDER));
        register(Cosmetic.particle("p_portal",    "&5Aura du Néant",       RARE,   1000, 5,  Particle.PORTAL,          Material.OBSIDIAN));
        register(Cosmetic.particle("p_witch",     "&2Sorcellerie",         RARE,   950,  4,  Particle.WITCH,           Material.CAULDRON));
        register(Cosmetic.dustParticle("p_navy",  "&1Poussière de Nuit",   RARE,   1200, 6,  Color.NAVY,               Material.LAPIS_LAZULI));
        register(Cosmetic.particle("p_endrod",    "&fBâton Runique",       EPIC,   2600, 9,  Particle.END_ROD,         Material.END_ROD));
        register(Cosmetic.particle("p_dragonbr",  "&5Souffle de Dragon",   EPIC,   2900, 10, Particle.DRAGON_BREATH,   Material.DRAGON_BREATH));
        register(Cosmetic.particle("p_firework",  "&6Feu d'Artifice",      EPIC,   3200, 11, Particle.FIREWORK,        Material.FIREWORK_ROCKET));
        register(Cosmetic.particle("p_totem",     "&e&lTotem Divin",       LEGENDARY, 8500, 18, Particle.TOTEM_OF_UNDYING, Material.TOTEM_OF_UNDYING));

        // ═══════════════════════ TRAÎNÉES (10) ═══════════════════════
        register(Cosmetic.trail("t_cloud",    "&fTraînée Céleste",   COMMON, 500,  2,  Particle.CLOUD,         Material.FEATHER));
        register(Cosmetic.trail("t_flame",    "&cTraînée de Feu",    RARE,   900,  3,  Particle.FLAME,         Material.BLAZE_ROD));
        register(Cosmetic.trail("t_enchant",  "&dTraînée Enchantée", RARE,   1000, 4,  Particle.ENCHANT,       Material.ENCHANTING_TABLE));
        register(Cosmetic.trail("t_lava",     "&6Traînée de Lave",   RARE,   1100, 5,  Particle.LAVA,          Material.MAGMA_BLOCK));
        register(Cosmetic.dustTrail("t_rainbow", "&5Traînée Arc-en-ciel", EPIC, 2600, 9,  Color.FUCHSIA,       Material.FIREWORK_STAR));
        register(Cosmetic.trail("t_endrod",   "&fTraînée Stellaire", EPIC,   2300, 8,  Particle.END_ROD,       Material.NETHER_STAR));
        register(Cosmetic.trail("t_squidink", "&8Traînée d'Ombre",   EPIC,   2400, 9,  Particle.SQUID_INK,     Material.INK_SAC));
        register(Cosmetic.dustTrail("t_gold", "&6Traînée Dorée",     RARE,   1300, 6,  Color.YELLOW,           Material.GOLD_NUGGET));
        register(Cosmetic.trail("t_witch",    "&2Traînée Toxique",   RARE,   1200, 6,  Particle.WITCH,         Material.SPIDER_EYE));
        register(Cosmetic.trail("t_dragonbr", "&5&lTraînée Draconique", LEGENDARY, 7500, 17, Particle.DRAGON_BREATH, Material.DRAGON_HEAD));

        // ═══════════════════════ TITRES (5) ═══════════════════════
        register(Cosmetic.tag("tag_frost",     "&bGivré",           RARE,      2200,  8,  " &b❄ Givré ❄",           Material.PACKED_ICE));
        register(Cosmetic.tag("tag_legend",    "&6Légende",         EPIC,      4200,  12, " &6★ Légende ★",         Material.GOLD_INGOT));
        register(Cosmetic.tag("tag_hunter",    "&4Chasseur",        EPIC,      4600,  13, " &4☠ Chasseur ☠",        Material.IRON_SWORD));
        register(Cosmetic.tag("tag_king",      "&e&lRoi de l'Arène",LEGENDARY, 9500,  19, " &e&l♛ Roi de l'Arène ♛",Material.GOLDEN_HELMET));
        register(Cosmetic.tag("tag_invincible","&c&lInvincible",    LEGENDARY, 13000, 22, " &c&l\uD83D\uDD25 Invincible \uD83D\uDD25", Material.NETHERITE_INGOT));

        // ═══════════════════════ ENTRÉES (5) ═══════════════════════
        register(Cosmetic.entrance("e_confetti", "&dConfettis",           COMMON, 400,  1,  Particle.FIREWORK,       Material.FIREWORK_ROCKET));
        register(Cosmetic.entrance("e_flame",    "&cCercle de Feu",       RARE,   900,  4,  Particle.FLAME,          Material.FIRE_CHARGE));
        register(Cosmetic.entrance("e_flash",    "&fÉclair Aveuglant",    RARE,   1000, 5,  Particle.FLASH,          Material.GLOWSTONE_DUST));
        register(Cosmetic.entrance("e_totem",    "&e&lBénédiction Divine",EPIC,   3400, 11, Particle.TOTEM_OF_UNDYING, Material.TOTEM_OF_UNDYING));
        register(Cosmetic.entrance("e_portal",   "&5Portail Dimensionnel",EPIC,   3000, 10, Particle.PORTAL,         Material.ENDER_PEARL));
    }

    // 15 chapeaux + 15 particules + 10 traînées + 5 titres + 5 entrées = 50 cosmétiques

    private CosmeticRegistry() {
    }

    public static List<Cosmetic> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<Cosmetic> byCategory(CosmeticCategory category) {
        List<Cosmetic> result = new ArrayList<>();
        for (Cosmetic c : ALL) {
            if (c.getCategory() == category) result.add(c);
        }
        return result;
    }

    public static Cosmetic get(String id) {
        return BY_ID.get(id);
    }
}
