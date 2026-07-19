package com.hikabrain.plugin.tournament.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Gère les clics dans le GUI des tournois : rejoint automatiquement les tournois
 * solo (1v1/FFA) en un clic ; pour les formats en équipe, redirige vers la commande.
 */
public class TournamentGUIListener implements Listener {

    private final HikaBrainPlugin plugin;
    private final TournamentGUI gui;

    public TournamentGUIListener(HikaBrainPlugin plugin, TournamentGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TournamentGUI.GUI_TITLE)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getDisplayName().isEmpty()) return;

        String tournamentName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        Tournament tournament = plugin.getTournamentManager().get(tournamentName);
        if (tournament == null) return;

        if (tournament.getTeamSize() == 1) {
            TournamentManager.JoinResult result = plugin.getTournamentManager().join(tournament.getName(), player, null);
            switch (result) {
                case OK: MessageUtil.send(player, "&aTu as rejoint le tournoi " + tournament.getName() + " !"); break;
                case ALREADY_REGISTERED: MessageUtil.send(player, "&cTu es déjà inscrit."); break;
                case FULL: MessageUtil.send(player, "&cCe tournoi est complet."); break;
                case NOT_OPEN: MessageUtil.send(player, "&cLes inscriptions sont fermées."); break;
                default: break;
            }
            player.closeInventory();
        } else {
            MessageUtil.send(player, "&eCe tournoi nécessite une équipe : utilise &f/tournament join "
                    + tournament.getName() + " <tag_equipe>");
        }
    }
}
