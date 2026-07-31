package com.hikabrain.plugin.levels;

/**
 * Avantages cosmétiques débloquables en montant de niveau.
 *
 * IMPORTANT : ces avantages sont volontairement 100% cosmétiques, ils ne donnent
 * strictement AUCUN avantage en jeu (pas de dégâts, pas de vitesse, pas de vision...),
 * afin de ne jamais casser l'équité (le "fair") des parties HikaBrain.
 */
public enum Perk {

    PARTICLE_HEAD(
        "particle_head",
        3,
        "&bNuage de particules",
        "Un nuage de particules affichant ta tête flotte au-dessus de toi au tout début de chaque partie."
    ),
    VICTORY_STARS(
        "victory_stars",
        6,
        "&6Étincelles de victoire",
        "Des étincelles dorées tourbillonnent autour de toi quand ton équipe remporte la partie."
    ),
    PRESTIGE_STAR(
        "prestige_star",
        10,
        "&d\u2605 Étoile de prestige",
        "Une petite étoile colorée apparaît à côté de ton niveau affiché en jeu."
    );

    private final String id;
    private final int unlockLevel;
    private final String displayName;
    private final String description;

    Perk(String id, int unlockLevel, String displayName, String description) {
        this.id = id;
        this.unlockLevel = unlockLevel;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static Perk fromId(String id) {
        if (id == null) return null;
        for (Perk perk : values()) {
            if (perk.id.equalsIgnoreCase(id)) return perk;
        }
        return null;
    }
}
