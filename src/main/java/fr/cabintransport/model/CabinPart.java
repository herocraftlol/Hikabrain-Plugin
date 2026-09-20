package fr.cabintransport.model;

import org.bukkit.Material;

/**
 * Décrit un élément décoratif de la cabine (plateforme, poteau...)
 * positionné en coordonnées locales relatives au siège :
 *  - side    : décalage latéral (gauche/droite)
 *  - up      : décalage vertical
 *  - forward : décalage avant/arrière (dans le sens du trajet)
 */
public record CabinPart(double side, double up, double forward,
                         float scaleX, float scaleY, float scaleZ,
                         Material material) {
}
