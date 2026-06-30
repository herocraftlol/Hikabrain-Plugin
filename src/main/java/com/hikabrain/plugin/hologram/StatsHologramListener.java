package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.event.Listener;

/**
 * Anciennement utilisé pour détecter les clics sur l'onglet de mode du leaderboard.
 * Depuis la refonte en leaderboard unique (tous les modes affichés en même temps),
 * ce listener n'a plus d'action à gérer. Il reste présent pour la compatibilité
 * avec l'enregistrement dans HikaBrainPlugin.
 */
public class StatsHologramListener implements Listener {

    public StatsHologramListener(HikaBrainPlugin plugin, StatsHologramManager hologramManager) {
        // Aucune logique nécessaire : le leaderboard est désormais statique (tous modes visibles).
    }
}
