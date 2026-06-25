package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Gère les déplacements des joueurs :
 * - Capture de zone adverse
 * - Immobilisation pendant ROUND_RESET uniquement (pas pendant COUNTDOWN)
 * - Mort automatique si le joueur sort de la zone de jeu
 */
public class PlayerMoveListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerMoveListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findArenaOf(player);

        if (gm == null) {
            return;
        }

        GameState state = gm.getState();

        // FREEZE pendant ROUND_RESET uniquement (après un point marqué)
        // Les joueurs sont libres pendant COUNTDOWN (30 secondes de lobby)
        if (state == GameState.ROUND_RESET) {
            // On ignore les micro-mouvements (juste la tête qui tourne)
            if (isSameBlock(event.getFrom(), event.getTo())) {
                return;
            }
            // Immobiliser le joueur
            event.setTo(event.getFrom());
            return;
        }

        // On ignore les micro-mouvements
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        // Pendant PLAYING : vérifier la capture AVANT la mort hors zone
        if (state == GameState.PLAYING) {
            gm.handlePlayerMove(player);
            
            // Ensuite vérifier si le joueur sort de la zone (après le scoring)
            if (!gm.getArena().isInGameZone(player.getLocation())) {
                player.setHealth(0);
            }
        }
    }

    /**
     * Vérifie si deux locations sont dans le même bloc
     */
    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
