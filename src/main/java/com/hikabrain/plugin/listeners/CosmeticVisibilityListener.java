package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Applique les cosmétiques au moment où un joueur se connecte (s'il n'est pas déjà
 * engagé dans une arène — cas normal), et nettoie ses tâches actives à la déconnexion.
 *
 * Le reste de la logique (retirer les cosmétiques en rejoignant une arène, les
 * réappliquer en la quittant) est géré directement dans GameManager, aux points d'entrée
 * et de sortie de chaque arène (lobby d'attente, partie, spectateur) — voir
 * CosmeticManager#applyCosmetics / #removeCosmetics.
 */
public class CosmeticVisibilityListener implements Listener {

    private final HikaBrainPlugin plugin;

    public CosmeticVisibilityListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Un joueur qui se connecte n'est, par définition, dans aucune arène HikaBrain :
        // ses cosmétiques équipés doivent donc être actifs dès son arrivée dans le hub.
        plugin.getCosmeticManager().applyCosmetics(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Filet de sécurité : arrête proprement toute tâche de particule/traînée active
        // pour ce joueur, pour ne jamais laisser une tâche tourner dans le vide.
        plugin.getCosmeticManager().removeCosmetics(event.getPlayer());
    }
}
