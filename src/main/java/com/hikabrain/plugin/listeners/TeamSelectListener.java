package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.game.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gère les clics sur l'item de sélection d'équipe (terracotta coloré) au lobby.
 * Permet aux joueurs de changer d'équipe en cliquant sur le terracotta.
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

        // Ne permettre le changement d'équipe que pendant l'état WAITING (lobby)
        if (gm.getState() != GameState.WAITING) {
            return;
        }

        // Récupérer l'équipe sélectionnée par l'item
        Team targetTeam = KitManager.getTeamFromSelectorItem(item);
        if (targetTeam == null) {
            return;
        }

        // Empêcher le clic droit de consommer l'item
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
        }

        // Vérifier si le joueur est déjà dans cette équipe
        Team currentTeam = gm.getTeam(player);
        if (currentTeam == targetTeam) {
            // Déjà dans cette équipe, ne rien faire
            return;
        }

        // Vérifier si l'équipe cible n'est pas pleine
        long targetTeamCount = gm.getPlayerCountForTeam(targetTeam);
        long currentTeamCount = gm.getPlayerCountForTeam(currentTeam);
        
        // Calculer le nombre max par équipe (approximatif)
        int maxPerTeam = plugin.getConfig().getInt("max-players", 16) / 2;
        if (targetTeamCount >= maxPerTeam) {
            player.sendMessage(org.bukkit.ChatColor.RED + "L'équipe " + targetTeam.getColoredName() + org.bukkit.ChatColor.RED + " est pleine !");
            return;
        }

        // Changer l'équipe
        gm.changePlayerTeam(player, targetTeam);
    }
}
