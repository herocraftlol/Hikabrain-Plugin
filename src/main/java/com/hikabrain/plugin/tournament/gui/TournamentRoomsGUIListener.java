package com.hikabrain.plugin.tournament.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.BracketMatch;
import com.hikabrain.plugin.tournament.MatchStatus;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Gère les clics dans le GUI des salles d'un tournoi : "Rejoindre mon match" téléporte
 * le joueur dans sa propre arène, cliquer sur un autre match l'envoie en spectateur.
 */
public class TournamentRoomsGUIListener implements Listener {

    private final HikaBrainPlugin plugin;

    public TournamentRoomsGUIListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = stripped(event.getView().getTitle());
        if (!title.startsWith("🎮 Salles - ")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getDisplayName().isEmpty()) return;

        String tournamentName = title.substring("🎮 Salles - ".length());
        TournamentManager manager = plugin.getTournamentManager();
        Tournament tournament = manager.get(tournamentName);
        if (tournament == null) {
            MessageUtil.send(player, "&cCe tournoi n'existe plus.");
            player.closeInventory();
            return;
        }

        String displayName = stripped(meta.getDisplayName());

        if (displayName.equals("▶ Rejoindre mon match")) {
            boolean ok = manager.rejoinMyDuelMatch(player);
            if (!ok) {
                MessageUtil.send(player, "&cImpossible de rejoindre ton match pour l'instant (peut-être un match HikaBrain, déjà dans l'arène).");
            }
            player.closeInventory();
            return;
        }

        List<BracketMatch> round = tournament.getCurrentRound();
        if (round == null) return;
        for (BracketMatch match : round) {
            if (!stripped(match.getDisplayVersus()).equals(displayName)) continue;
            if (match.getStatus() != MatchStatus.ONGOING) {
                MessageUtil.send(player, "&eCe match n'a pas encore commencé.");
                return;
            }
            boolean ok = manager.spectateMatch(tournament, match, player);
            if (!ok) {
                MessageUtil.send(player, "&cImpossible de rejoindre ce match en spectateur.");
            }
            player.closeInventory();
            return;
        }
    }

    private String stripped(String raw) {
        return ChatColor.stripColor(raw);
    }
}
