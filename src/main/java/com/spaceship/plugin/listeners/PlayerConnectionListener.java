package com.spaceship.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.spaceship.plugin.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Gère le départ propre d'un joueur qui quitte le serveur en pleine partie.
 */
public class PlayerConnectionListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerConnectionListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameManager gameManager = plugin.getSpaceArenaManager().findArenaOf(player);
        if (gameManager != null) {
            gameManager.removePlayer(player);
        }
        GameManager spectating = plugin.getSpaceArenaManager().findSpectatingArenaOf(player);
        if (spectating != null) {
            spectating.removeSpectator(player);
        }
        // Supprimer le scoreboard du joueur
        if (plugin.getSpaceScoreboardManager() != null) {
            plugin.getSpaceScoreboardManager().removeScoreboard(player);
        }
    }
}
