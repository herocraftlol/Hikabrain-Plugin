package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Détecte l'utilisation de l'item "quitter la partie" (barrier block) donné à tous
 * les joueurs dans le slot 8 du lobby d'attente, ainsi que l'item "quitter le mode
 * spectateur" (boussole) donné aux spectateurs.
 * Un clic déclenche directement /hb leave (ou /hb unspectate), comme raccourci visuel.
 */
public class LeaveItemListener implements Listener {

    private final HikaBrainPlugin plugin;

    public LeaveItemListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (KitManager.isSpectatorLeaveItem(event.getItem())) {
            event.setCancelled(true);
            GameManager gm = plugin.getArenaManager().findSpectatorArenaOf(player);
            if (gm == null) {
                MessageUtil.send(player, "&cTu n'es pas en mode spectateur en ce moment.");
                return;
            }
            gm.removeSpectator(player);
            return;
        }

        if (!KitManager.isLeaveItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);

        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) {
            MessageUtil.send(player, "&cTu n'es dans aucune arène en ce moment.");
            return;
        }

        // L'item n'est disponible qu'en lobby ; on vérifie l'état par sécurité
        if (gm.getState() != GameState.WAITING && gm.getState() != GameState.COUNTDOWN) {
            MessageUtil.send(player, "&cTu ne peux pas quitter la partie en ce moment.");
            return;
        }

        gm.removePlayer(player);
    }
}
