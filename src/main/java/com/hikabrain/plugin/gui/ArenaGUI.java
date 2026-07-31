package com.hikabrain.plugin.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * GUI d'inventaire permettant au joueur de voir toutes les arènes disponibles
 * et d'en rejoindre une (ou une aléatoire via un bouton dédié).
 *
 * Structure :
 *   - Lignes 1 à 5 : une icône par arène (max 45 arènes affichées)
 *   - Ligne 6 entière : bouton "Rejoindre une arène aléatoire" (9 slots fusionnés visuellement)
 *
 * Taille du GUI = 54 slots (6 rangées × 9 colonnes).
 *
 * Un admin peut fixer l'emplacement précis d'une arène via /hb guislot <emplacement> <arène>
 * (voir {@link #assignSlot}) : ces assignations sont persistées dans gui-slots.yml. Les
 * arènes sans emplacement explicite continuent d'être placées automatiquement, dans l'ordre,
 * sur les emplacements encore libres.
 */
public class ArenaGUI {

    /** Titre affiché dans la barre du coffre. */
    public static final String GUI_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "⚔ Arènes disponibles";

    /** Nombre total de slots du GUI (6 rangées). */
    private static final int GUI_SIZE = 54;

    /** Index du premier slot de la dernière ligne (ligne 6 = index 45 à 53). */
    private static final int RANDOM_ROW_START = 45;

    private final HikaBrainPlugin plugin;
    private final File slotsFile;

    /** Emplacement (0-based) -> nom d'arène, assigné explicitement par un admin. */
    private final Map<Integer, String> slotAssignments = new LinkedHashMap<>();

    public ArenaGUI(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.slotsFile = new File(plugin.getDataFolder(), "gui-slots.yml");
        loadSlotAssignments();
    }

    /**
     * Nombre maximum d'emplacements assignables explicitement (1-based) : 1 à 45.
     * Les emplacements 46 à 54 (dernière ligne) sont réservés au bouton "arène aléatoire".
     */
    public static int getMaxAssignableSlot() {
        return RANDOM_ROW_START;
    }

    private void loadSlotAssignments() {
        slotAssignments.clear();
        if (!slotsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(slotsFile);
        ConfigurationSection section = config.getConfigurationSection("slots");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                String arenaName = section.getString(key);
                if (arenaName != null) {
                    slotAssignments.put(slot, arenaName);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void saveSlotAssignments() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<Integer, String> entry : slotAssignments.entrySet()) {
            config.set("slots." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(slotsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder gui-slots.yml : " + e.getMessage());
        }
    }

    /**
     * Assigne une arène à un emplacement précis (0-based en interne) du GUI /arenas.
     * Retire au préalable toute assignation précédente pointant vers la même arène
     * (une arène n'occupe qu'un seul emplacement à la fois). Écrase silencieusement
     * une éventuelle arène déjà assignée à cet emplacement.
     *
     * Renvoie false si l'emplacement est en dehors de la zone d'arènes (réservée à la
     * dernière ligne, le bouton "arène aléatoire").
     */
    public boolean assignSlot(int slot, String arenaName) {
        if (slot < 0 || slot >= RANDOM_ROW_START) return false;
        slotAssignments.values().removeIf(name -> name.equalsIgnoreCase(arenaName));
        slotAssignments.put(slot, arenaName);
        saveSlotAssignments();
        return true;
    }

    /**
     * Retire l'assignation explicite d'un emplacement (l'arène qui s'y trouvait, s'il y
     * en avait une, retombe alors dans le remplissage automatique). Renvoie false si cet
     * emplacement n'avait pas d'assignation explicite.
     */
    public boolean clearSlot(int slot) {
        boolean removed = slotAssignments.remove(slot) != null;
        if (removed) {
            saveSlotAssignments();
        }
        return removed;
    }

    /**
     * Ouvre le GUI pour le joueur donné.
     */
    public void open(Player player) {
        Inventory inv = buildInventory();
        player.openInventory(inv);
    }

    /**
     * Calcule le placement final de chaque arène dans le GUI (emplacement 0-based -> arène) :
     * d'abord les assignations explicites (/hb guislot), puis les arènes restantes remplies
     * automatiquement dans l'ordre sur les emplacements encore libres.
     */
    private Map<Integer, GameManager> computeSlotPlacement() {
        Collection<GameManager> allArenas = plugin.getArenaManager().getAll();

        Map<String, GameManager> byName = new LinkedHashMap<>();
        for (GameManager gm : allArenas) {
            byName.put(gm.getName(), gm);
        }

        Map<Integer, GameManager> placement = new LinkedHashMap<>();
        Set<String> placedArenaNames = new HashSet<>();

        for (Map.Entry<Integer, String> entry : slotAssignments.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= RANDOM_ROW_START) continue; // sécurité si le fichier a été édité à la main
            GameManager gm = byName.get(entry.getValue());
            if (gm == null) continue; // l'arène assignée a peut-être été supprimée depuis
            placement.put(slot, gm);
            placedArenaNames.add(gm.getName());
        }

        int cursor = 0;
        for (GameManager gm : allArenas) {
            if (placedArenaNames.contains(gm.getName())) continue;
            while (cursor < RANDOM_ROW_START && placement.containsKey(cursor)) {
                cursor++;
            }
            if (cursor >= RANDOM_ROW_START) break; // plus de place
            placement.put(cursor, gm);
            cursor++;
        }

        return placement;
    }

    /**
     * Construit et retourne l'inventaire rempli.
     */
    public Inventory buildInventory() {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        Map<Integer, GameManager> placement = computeSlotPlacement();
        for (Map.Entry<Integer, GameManager> entry : placement.entrySet()) {
            GameManager gm = entry.getValue();
            inv.setItem(entry.getKey(), buildArenaItem(gm, gm.getMaxPlayers()));
        }

        // Remplir les slots vides des lignes 1-5 avec du verre noir
        ItemStack filler = buildFiller();
        for (int i = 0; i < RANDOM_ROW_START; i++) {
            if (!placement.containsKey(i)) {
                inv.setItem(i, filler);
            }
        }

        // Ligne 6 entière : bouton arène aléatoire
        Collection<GameManager> allArenas = plugin.getArenaManager().getAll();
        ItemStack randomBtn = buildRandomButton(allArenas);
        for (int i = RANDOM_ROW_START; i < GUI_SIZE; i++) {
            inv.setItem(i, randomBtn);
        }

        return inv;
    }

    /**
     * Crée l'icône représentant une arène.
     * - Arène jouable (WAITING/COUNTDOWN) → émeraude verte
     * - Arène en cours (PLAYING) → tête de joueur rouge (barrière)
     * - Arène non configurée → caillou gris
     */
    private ItemStack buildArenaItem(GameManager gm, int maxPlayers) {
        String name = gm.getName();
        GameState state = gm.getState();
        int current = gm.getPlayerCount();

        Material mat;
        String displayName;
        String statusLine;
        ChatColor statusColor;

        if (!gm.getArena().isFullyConfigured() || state == GameState.NOT_CONFIGURED) {
            mat = Material.GRAY_STAINED_GLASS_PANE;
            displayName = ChatColor.GRAY + "" + ChatColor.BOLD + "✖ " + capitalize(name);
            statusLine = ChatColor.GRAY + "Non configurée";
            statusColor = ChatColor.GRAY;
        } else if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) {
            mat = Material.RED_STAINED_GLASS_PANE;
            displayName = ChatColor.RED + "" + ChatColor.BOLD + "⚔ " + capitalize(name);
            statusLine = ChatColor.RED + "Partie en cours";
            statusColor = ChatColor.RED;
        } else if (current >= maxPlayers) {
            mat = Material.ORANGE_STAINED_GLASS_PANE;
            displayName = ChatColor.GOLD + "" + ChatColor.BOLD + "⚠ " + capitalize(name);
            statusLine = ChatColor.GOLD + "Pleine";
            statusColor = ChatColor.GOLD;
        } else {
            mat = Material.LIME_STAINED_GLASS_PANE;
            displayName = ChatColor.GREEN + "" + ChatColor.BOLD + "✔ " + capitalize(name);
            statusLine = ChatColor.GREEN + "Disponible";
            statusColor = ChatColor.GREEN;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Joueurs : " + statusColor + current + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + maxPlayers);
        lore.add(ChatColor.GRAY + "Statut  : " + statusLine);
        if (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING) {
            lore.add(ChatColor.GRAY + "Spectateurs : " + ChatColor.AQUA + gm.getSpectatorCount());
        }
        lore.add("");

        boolean joinable = gm.getArena().isFullyConfigured()
                && state != GameState.PLAYING
                && state != GameState.ROUND_RESET
                && state != GameState.STARTING
                && state != GameState.ENDING
                && state != GameState.NOT_CONFIGURED
                && current < maxPlayers;

        boolean spectatable = gm.getArena().isFullyConfigured()
                && (state == GameState.PLAYING || state == GameState.ROUND_RESET || state == GameState.STARTING);

        if (joinable) {
            lore.add(ChatColor.YELLOW + "▶ Cliquez pour rejoindre !");
        } else if (spectatable) {
            lore.add(ChatColor.AQUA + "\uD83D\uDC41 Cliquez pour regarder en spectateur !");
        } else {
            lore.add(ChatColor.RED + "✖ Indisponible");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Bouton de la dernière ligne : rejoindre une arène aléatoire (priorité aux arènes avec joueurs).
     */
    private ItemStack buildRandomButton(Collection<GameManager> allArenas) {
        long joinableCount = allArenas.stream()
                .filter(gm -> gm.getArena().isFullyConfigured()
                        && gm.getState() != GameState.PLAYING
                        && gm.getState() != GameState.ROUND_RESET
                        && gm.getState() != GameState.STARTING
                        && gm.getState() != GameState.ENDING
                        && gm.getState() != GameState.NOT_CONFIGURED
                        && gm.getPlayerCount() < gm.getMaxPlayers())
                .count();

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Rejoindre une arène aléatoire");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Vous serez envoyé dans une arène");
        lore.add(ChatColor.GRAY + "disponible, en priorité celles");
        lore.add(ChatColor.GRAY + "qui ont déjà des joueurs.");
        lore.add("");
        if (joinableCount > 0) {
            lore.add(ChatColor.GREEN + "" + joinableCount + " arène(s) disponible(s)");
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Cliquez pour jouer !");
        } else {
            lore.add(ChatColor.RED + "Aucune arène disponible pour le moment.");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Case de remplissage pour les slots vides (lignes 1-5).
     */
    private ItemStack buildFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Met la première lettre en majuscule.
     */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Retourne le nom de l'arène à partir de son slot dans le GUI.
     * Retourne null si le slot est en dehors de la zone des arènes.
     */
    public String getArenaNameAt(int slot) {
        if (slot < 0 || slot >= RANDOM_ROW_START) return null;
        GameManager gm = computeSlotPlacement().get(slot);
        return gm != null ? gm.getName() : null;
    }

    /**
     * Retourne true si le slot cliqué correspond au bouton "aléatoire" (ligne 6).
     */
    public static boolean isRandomButton(int slot) {
        return slot >= RANDOM_ROW_START && slot < GUI_SIZE;
    }
}
