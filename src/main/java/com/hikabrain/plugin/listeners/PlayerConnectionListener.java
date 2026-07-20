package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
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
        GameManager gameManager = plugin.getArenaManager().findArenaOf(player);
        if (gameManager != null) {
            gameManager.removePlayer(player);
        }
        GameManager spectating = plugin.getArenaManager().findSpectatorArenaOf(player);
        if (spectating != null) {
            spectating.removeSpectator(player);
        }
        // Supprimer le scoreboard du joueur
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().removeScoreboard(player);
        }
    }
}
