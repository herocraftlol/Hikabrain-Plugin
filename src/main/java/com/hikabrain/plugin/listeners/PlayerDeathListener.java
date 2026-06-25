package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Gère la détection des kills et deaths pour les statistiques.
 */
public class PlayerDeathListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerDeathListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Détecte quand un joueur meurt en partie et enregistre le kill/death.
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        GameManager gm = plugin.getArenaManager().findArenaOf(victim);
        
        if (gm == null) {
            return;
        }

        // Vérifier que la victime est bien dans une équipe
        if (!gm.isPlaying(victim)) {
            return;
        }

        // Trouver le tueur
        Player killer = victim.getKiller();
        
        if (killer != null && gm.isPlaying(killer) && !killer.equals(victim)) {
            // Le kill est comptabilisé pour l'équipe du tueur
            plugin.getStatsManager().addKill(gm.getTeam(killer));
        }

        // Le death est comptabilisé pour l'équipe de la victime
        plugin.getStatsManager().addDeath(gm.getTeam(victim));
    }
}
