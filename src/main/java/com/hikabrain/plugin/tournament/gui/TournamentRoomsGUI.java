package com.hikabrain.plugin.tournament.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.BracketMatch;
import com.hikabrain.plugin.tournament.MatchStatus;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI des "salles" d'un tournoi en cours : liste les matchs de la phase actuelle
 * (poules/huitièmes/quarts/demies/finale) et permet en un clic de :
 *  - rejoindre son propre match s'il est en cours (bouton mis en avant, en haut) ;
 *  - se téléporter en spectateur sur n'importe quel autre match en cours.
 *
 * Ce GUI n'a de sens que pendant qu'un tournoi est IN_PROGRESS ; il est ouvert via
 * "/tournament rooms <nom>" (ou automatiquement proposé au démarrage du tournoi).
 * Accessible à tout joueur connecté : les participants y retrouvent leur match, les
 * autres (spectateurs) peuvent choisir n'importe quel match à observer.
 */
public class TournamentRoomsGUI {

    public static final String GUI_TITLE_PREFIX = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "🎮 Salles - ";
    private static final int GUI_SIZE = 54;

    private final HikaBrainPlugin plugin;

    public TournamentRoomsGUI(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public static String titleFor(Tournament tournament) {
        String raw = GUI_TITLE_PREFIX + tournament.getName();
        return raw.length() > 32 ? raw.substring(0, 32) : raw;
    }

    /** true si ce GUI peut être ouvert pour ce tournoi dans son état actuel. */
    public boolean canOpen(Tournament tournament) {
        return tournament != null && tournament.getState() == TournamentState.IN_PROGRESS;
    }

    public void open(Player player, Tournament tournament) {
        player.openInventory(build(tournament, player));
    }

    public Inventory build(Tournament tournament, Player viewer) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, titleFor(tournament));

        List<BracketMatch> round = tournament.getCurrentRound();
        int slot = 0;

        // Bouton "mon match" mis en avant en première position si le joueur est engagé
        // dans un match en cours de ce tournoi.
        BracketMatch myMatch = tournament.findOngoingMatchOf(viewer.getUniqueId());
        if (myMatch != null) {
            inv.setItem(0, buildMyMatchItem(myMatch));
            slot = 1;
        }

        if (round != null) {
            for (BracketMatch match : round) {
                if (match == myMatch) continue;
                if (slot >= GUI_SIZE) break;
                if (match.getStatus() == MatchStatus.ONGOING || match.getStatus() == MatchStatus.PENDING) {
                    inv.setItem(slot++, buildSpectateItem(tournament, match));
                }
            }
        }

        ItemStack filler = filler();
        for (int i = slot; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }
        return inv;
    }

    private ItemStack buildMyMatchItem(BracketMatch match) {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "▶ Rejoindre mon match");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + match.getDisplayVersus());
            lore.add(ChatColor.GRAY + "Arène : " + ChatColor.WHITE + match.getArenaName());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Clique pour te téléporter dans l'arène");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildSpectateItem(Tournament tournament, BracketMatch match) {
        boolean ongoing = match.getStatus() == MatchStatus.ONGOING;
        ItemStack item = new ItemStack(ongoing ? Material.COMPASS : Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((ongoing ? ChatColor.AQUA : ChatColor.GRAY) + match.getDisplayVersus());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "État : " + (ongoing ? ChatColor.GREEN + "En cours" : ChatColor.YELLOW + "En attente d'arène"));
            if (match.getArenaName() != null) {
                lore.add(ChatColor.GRAY + "Arène : " + ChatColor.WHITE + match.getArenaName());
            }
            lore.add("");
            lore.add(ongoing ? ChatColor.YELLOW + "Clique pour observer en spectateur"
                    : ChatColor.DARK_GRAY + "Pas encore commencé");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }
}
