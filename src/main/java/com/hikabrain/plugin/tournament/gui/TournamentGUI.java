package com.hikabrain.plugin.tournament.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.tournament.Tournament;
import com.hikabrain.plugin.tournament.TournamentManager;
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
 * GUI d'inventaire listant tous les tournois connus, avec leur état et le nombre
 * d'inscrits. Cliquer sur un tournoi en inscription y inscrit le joueur (formats
 * solo uniquement) ; sinon affiche ses infos dans le chat.
 */
public class TournamentGUI {

    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "🏆 Tournois";
    private static final int GUI_SIZE = 54;

    private final HikaBrainPlugin plugin;

    public TournamentGUI(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        player.openInventory(build());
    }

    public Inventory build() {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        TournamentManager manager = plugin.getTournamentManager();

        int slot = 0;
        for (Tournament t : manager.getAll()) {
            if (slot >= GUI_SIZE) break;
            inv.setItem(slot, buildItem(t));
            slot++;
        }
        ItemStack filler = filler();
        for (int i = slot; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }
        return inv;
    }

    private ItemStack buildItem(Tournament t) {
        Material material;
        switch (t.getState()) {
            case REGISTRATION: material = Material.EMERALD; break;
            case IN_PROGRESS: material = Material.CLOCK; break;
            case FINISHED: material = Material.GOLDEN_APPLE; break;
            default: material = Material.BARRIER; break;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + t.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Format : " + ChatColor.WHITE + t.getFormat().getLabel());
            lore.add(ChatColor.GRAY + "Places : " + ChatColor.WHITE + t.getRegistered().size() + "/" + t.getMaxSlots());
            lore.add(ChatColor.GRAY + "État : " + ChatColor.WHITE + stateLabel(t.getState()));
            if (t.getState() == TournamentState.REGISTRATION) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "Clique pour rejoindre" + (t.getTeamSize() > 1 ? " (équipe requise en commande)" : ""));
            } else if (t.getState() == TournamentState.FINISHED && t.getChampion() != null) {
                lore.add("");
                lore.add(ChatColor.GOLD + "🏆 " + t.getChampion().getDisplayName());
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String stateLabel(TournamentState state) {
        switch (state) {
            case REGISTRATION: return "Inscriptions ouvertes";
            case IN_PROGRESS: return "En cours";
            case FINISHED: return "Terminé";
            default: return "Annulé";
        }
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }
}
