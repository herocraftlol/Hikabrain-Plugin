package fr.spide.gui;

import fr.spide.model.MapState;
import fr.spide.model.SpideMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI de sélection de map (/sp gui) : un double coffre (54 cases).
 * Les maps sont placées de haut en bas puis de gauche à droite (ordre "colonne par colonne").
 * Case vide -> vitrail gris. Map disponible -> vitrail vert clair (lime). En maintenance -> orange.
 * Occupée -> rouge.
 *
 * Chaque item de map affiche, en plus de son état, le nom de la map, le nombre de places
 * (joueurs connectés / places totales), le nombre d'équipes et le nombre de points requis
 * pour gagner, afin que le joueur ait toutes les infos utiles sans avoir à cliquer.
 */
public class MapGUI implements InventoryHolder {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    private final Inventory inventory;
    private final Map<Integer, String> slotToMap = new HashMap<>();

    public MapGUI(List<SpideMap> maps) {
        inventory = Bukkit.createInventory(this, SIZE, Component.text("Sélection de map - Spide"));

        ItemStack empty = pane(Material.GRAY_STAINED_GLASS_PANE, "§7Emplacement vide", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, empty);
        }

        int index = 0;
        for (SpideMap map : maps) {
            int column = index / ROWS;
            int row = index % ROWS;
            if (column >= 9) break; // GUI plein (54 maps max)
            int slot = row * 9 + column;

            inventory.setItem(slot, buildMapItem(map));
            slotToMap.put(slot, map.getName());
            index++;
        }
    }

    private ItemStack buildMapItem(SpideMap map) {
        Material material;
        String stateLine;
        switch (map.getState()) {
            case AVAILABLE:
                material = Material.LIME_STAINED_GLASS_PANE;
                stateLine = "§aDisponible §7— §fClique pour rejoindre";
                break;
            case OCCUPIED:
                material = Material.RED_STAINED_GLASS_PANE;
                stateLine = "§cPartie en cours §7— §fClique pour spectate";
                break;
            case MAINTENANCE:
            default:
                material = Material.ORANGE_STAINED_GLASS_PANE;
                stateLine = "§6En maintenance";
                break;
        }

        List<String> lore = new ArrayList<>();
        lore.add(stateLine);
        lore.add("");
        lore.add("§7Joueurs : §f" + map.getTotalCurrentPlayers() + "§7/§f" + map.getTotalRequiredPlayers()
                + " §7(min §f" + map.getEffectiveMinPlayers() + "§7, max §f" + map.getEffectiveMaxPlayers() + "§7)");
        lore.add("§7Équipes : §f" + map.getTeams().size());
        lore.add("§7Points pour gagner : §f" + map.getPointsToWin());
        if (map.getState() == MapState.OCCUPIED) {
            lore.add("§7Spectateurs : §f" + map.getSpectators().size());
        }

        return pane(material, "§f§l" + map.getName(), lore);
    }

    private ItemStack pane(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName).decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        item.setItemMeta(meta);
        return item;
    }

    public Inventory getInventoryToOpen() {
        return inventory;
    }

    public String getMapAt(int slot) {
        return slotToMap.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
