package fr.spide.model;

/**
 * État d'une map, utilisé pour la couleur du vitrail dans le GUI (/sp gui) :
 *  - MAINTENANCE : orange, la map est en cours de configuration (pas encore jouable)
 *  - AVAILABLE   : vert, la map est prête et peut accueillir une partie
 *  - OCCUPIED    : rouge, une partie est en cours sur cette map
 *
 * Les emplacements gris vides du double coffre ne correspondent à aucune map
 * (ce sont juste des cases non utilisées du GUI).
 */
public enum MapState {
    MAINTENANCE,
    AVAILABLE,
    OCCUPIED
}
