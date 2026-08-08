package com.hikabrain.plugin.cosmetics;

/**
 * Catégorie d'un cosmétique. Un joueur peut avoir AU MAXIMUM un cosmétique équipé PAR
 * catégorie en même temps (équiper un nouveau chapeau retire l'ancien, mais n'affecte
 * pas la particule ou le tag équipés par ailleurs).
 */
public enum CosmeticCategory {
    HAT("Chapeaux", "\uD83C\uDFA9"),
    PARTICLE("Particules", "\u2728"),
    TRAIL("Traînées", "\uD83D\uDCAB"),
    TAG("Titres", "\uD83C\uDFF7"),
    ENTRANCE("Entrées", "\uD83D\uDCA5");

    private final String displayName;
    private final String icon;

    CosmeticCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
