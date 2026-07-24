package com.hikabrain.plugin.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.Team;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Écoute les clics dans le GUI de sélection d'équipe et applique le changement
 * d'équipe demandé, en revérifiant à chaque clic que la partie est toujours en
 * lobby d'attente et que l'équipe visée a encore de la place.
 */
public class TeamSelectGUIListener implements Listener {

    private final HikaBrainPlugin plugin;

    public TeamSelectGUIListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Vérifier que c'est bien notre GUI (par le titre)
        if (!TeamSelectGUI.GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        // Annuler toujours le clic pour éviter de prendre des items
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        Team targetTeam;
        if (slot == TeamSelectGUI.RED_SLOT) {
            targetTeam = Team.RED;
        } else if (slot == TeamSelectGUI.BLUE_SLOT) {
            targetTeam = Team.BLUE;
        } else {
            // Clic sur une case de remplissage : on ignore.
            return;
        }

        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) {
            player.closeInventory();
            return;
        }

        // La partie a pu démarrer entre l'ouverture du GUI et ce clic : on revérifie.
        // Le changement d'équipe reste autorisé pendant WAITING et COUNTDOWN.
        if (gm.getState() != GameState.WAITING && gm.getState() != GameState.COUNTDOWN) {
            MessageUtil.send(player, "&cTu ne peux plus changer d'équipe maintenant.");
            player.closeInventory();
            return;
        }

        Team currentTeam = gm.getTeam(player);
        if (currentTeam == targetTeam) {
            MessageUtil.send(player, "&eTu es déjà dans l'équipe " + targetTeam.getColoredName() + "&e !");
            return;
        }

        int limit = TeamSelectGUI.teamLimit(gm);
        int targetCount = gm.getPlayerCountForTeam(targetTeam);
        if (targetCount >= limit) {
            MessageUtil.send(player, "&cL'équipe " + targetTeam.getColoredName() + "&c est déjà complète ("
                    + targetCount + "/" + limit + ") !");
            // On rafraîchit le GUI pour refléter l'état actuel des équipes.
            player.openInventory(plugin.getTeamSelectGUI().build(gm));
            return;
        }

        if (gm.changePlayerTeam(player, targetTeam)) {
            MessageUtil.send(player, "&aTu as rejoint l'équipe " + targetTeam.getColoredName() + "&a !");
            player.closeInventory();
        } else {
            MessageUtil.send(player, "&cImpossible de changer d'équipe !");
            player.closeInventory();
        }
    }
}
