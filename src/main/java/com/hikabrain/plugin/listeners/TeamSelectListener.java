package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.KitManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gère les clics sur l'item de sélection d'équipe (terracotta coloré) au lobby.
 * Un clic ouvre le GUI de sélection d'équipe (voir {@link com.hikabrain.plugin.gui.TeamSelectGUI}),
 * qui permet au joueur de choisir explicitement l'équipe rouge ou bleue selon les places
 * encore disponibles et la limite par équipe (calculée à partir du format de la partie :
 * 2v2, 3v3, 4v4...).
 */
public class TeamSelectListener implements Listener {

    private final HikaBrainPlugin plugin;

    public TeamSelectListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Vérifier si l'item cliqué est l'item de sélection d'équipe
        if (!KitManager.isTeamSelectorItem(item)) {
            return;
        }

        // Vérifier si le joueur est dans une arène
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) {
            return;
        }

        // Empêcher le clic de déclencher une autre action (placement de bloc, etc.)
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
        }

        // Ne permettre l'ouverture du GUI de sélection d'équipe que pendant l'état WAITING (lobby)
        if (gm.getState() != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas changer d'équipe pendant la partie !");
            return;
        }

        // Ouvrir le GUI de sélection d'équipe
        plugin.getTeamSelectGUI().open(player, gm);
    }
}
