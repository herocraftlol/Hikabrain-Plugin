package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Détecte les déplacements des joueurs en partie pour savoir s'ils viennent
 * de pénétrer dans la zone de capture adverse (capture instantanée).
 * Gère également l'immobilisation pendant les comptes à rebours et la mort
 * automatique si un joueur sort de la zone de jeu.
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

        // Vérifier si le joueur essaie de bouger pendant un compte à rebours (COUNTDOWN ou ROUND_RESET)
        GameState state = gm.getState();
        if (state == GameState.COUNTDOWN || state == GameState.ROUND_RESET) {
            // On ignore les micro-mouvements (juste la tête qui tourne)
            if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                    && event.getFrom().getBlockY() == event.getTo().getBlockY()
                    && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
                return;
            }
            // Immobiliser le joueur - le ramener à sa position précédente
            event.setTo(event.getFrom());
            return;
        }

        // On ignore les micro-mouvements pendant la partie
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Vérifier si le joueur est hors de la zone de jeu (uniquement pendant PLAYING)
        if (state == GameState.PLAYING) {
            if (!gm.getArena().isInGameZone(player.getLocation())) {
                // Tuer le joueur et le téléporter à son spawn
                player.setHealth(0);
                return;
            }
        }

        // Gérer le mouvement normal (capture de zone)
        gm.handlePlayerMove(player);
    }
}
