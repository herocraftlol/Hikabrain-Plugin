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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GUI d'inventaire permettant au joueur de voir toutes les arènes disponibles
 * et d'en rejoindre une (ou une aléatoire via un bouton dédié).
 *
 * Structure d'UNE page (54 slots = 6 rangées × 9 colonnes) :
 *   - Lignes 1 à 5 (slots 0-44) : une icône par arène (45 par page)
 *   - Ligne 6 : slot 45 = page précédente, slots 46-52 = bouton "arène aléatoire"
 *     (fusionné visuellement, fonctionne sur TOUTES les arènes, peu importe la page
 *     affichée), slot 53 = page suivante
 *
 * Le GUI peut afficher PLUSIEURS PAGES : au-delà de 45 arènes, une nouvelle page est
 * automatiquement créée. Un admin peut aussi fixer explicitement l'emplacement précis
 * d'une arène sur une page donnée via /hb guislot <page> <emplacement 1-45> <arène>
 * (voir {@link #assignSlot}) : ces assignations sont persistées dans gui-slots.yml. Les
 * arènes sans emplacement explicite continuent d'être placées automatiquement, dans
 * l'ordre, sur les emplacements encore libres (en débordant sur la page suivante si la
 * page courante est pleine).
 */
public class ArenaGUI {

    /** Base du titre affiché dans la barre du coffre (sans le numéro de page). */
    public static final String GUI_TITLE_BASE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "⚔ Arènes disponibles";

    /** Conservé pour compatibilité : titre de la page 1 quand il n'y a qu'une seule page. */
    public static final String GUI_TITLE = GUI_TITLE_BASE;

    /** Nombre total de slots du GUI (6 rangées). */
    private static final int GUI_SIZE = 54;

    /** Nombre d'arènes affichables par page (lignes 1 à 5). */
    private static final int PAGE_SIZE = 45;

    /** Slots de la dernière ligne : navigation + bouton aléatoire. */
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_RANDOM_START = 46;
    private static final int SLOT_RANDOM_END = 52; // inclus
    private static final int SLOT_NEXT_PAGE = 53;

    private static final Pattern PAGE_TITLE_PATTERN = Pattern.compile("\\((\\d+)/(\\d+)\\)");

    private final HikaBrainPlugin plugin;
    private final File slotsFile;

    /** Page (0-based) -> Emplacement (0-based) -> nom d'arène, assigné explicitement par un admin. */
    private final Map<Integer, Map<Integer, String>> slotAssignments = new LinkedHashMap<>();

    public ArenaGUI(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.slotsFile = new File(plugin.getDataFolder(), "gui-slots.yml");
        loadSlotAssignments();
    }

    /**
     * Nombre maximum d'emplacements assignables explicitement par page (1-based) : 1 à 45.
     */
    public static int getMaxAssignableSlot() {
        return PAGE_SIZE;
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void loadSlotAssignments() {
        slotAssignments.clear();
        if (!slotsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(slotsFile);
        ConfigurationSection pagesSection = config.getConfigurationSection("pages");
        if (pagesSection == null) return;
        for (String pageKey : pagesSection.getKeys(false)) {
            int page;
            try {
                page = Integer.parseInt(pageKey);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection slotsSection = pagesSection.getConfigurationSection(pageKey);
            if (slotsSection == null) continue;
            Map<Integer, String> pageMap = new LinkedHashMap<>();
            for (String slotKey : slotsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    String arenaName = slotsSection.getString(slotKey);
                    if (arenaName != null) pageMap.put(slot, arenaName);
                } catch (NumberFormatException ignored) {
                }
            }
            if (!pageMap.isEmpty()) slotAssignments.put(page, pageMap);
        }
    }

    private void saveSlotAssignments() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<Integer, Map<Integer, String>> pageEntry : slotAssignments.entrySet()) {
            for (Map.Entry<Integer, String> slotEntry : pageEntry.getValue().entrySet()) {
                config.set("pages." + pageEntry.getKey() + "." + slotEntry.getKey(), slotEntry.getValue());
            }
        }
        try {
            config.save(slotsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder gui-slots.yml : " + e.getMessage());
        }
    }

    /**
     * Assigne une arène à un emplacement précis (0-based en interne) d'une page précise
     * (0-based en interne) du GUI /arenas. Retire au préalable toute assignation
     * précédente pointant vers la même arène, où qu'elle soit (une arène n'occupe qu'un
     * seul emplacement à la fois). Écrase silencieusement une éventuelle arène déjà
     * assignée à cet emplacement.
     *
     * Renvoie false si l'emplacement est en dehors de la zone d'arènes d'une page
     * (réservée à la dernière ligne : navigation + bouton "arène aléatoire").
     */
    public boolean assignSlot(int page, int slot, String arenaName) {
        if (page < 0 || slot < 0 || slot >= PAGE_SIZE) return false;
        for (Map<Integer, String> pageMap : slotAssignments.values()) {
            pageMap.values().removeIf(name -> name.equalsIgnoreCase(arenaName));
        }
        slotAssignments.computeIfAbsent(page, k -> new LinkedHashMap<>()).put(slot, arenaName);
        saveSlotAssignments();
        return true;
    }

    /**
     * Retire l'assignation explicite d'un emplacement d'une page (l'arène qui s'y
     * trouvait, s'il y en avait une, retombe alors dans le remplissage automatique).
     * Renvoie false si cet emplacement n'avait pas d'assignation explicite.
     */
    public boolean clearSlot(int page, int slot) {
        Map<Integer, String> pageMap = slotAssignments.get(page);
        if (pageMap == null) return false;
        boolean removed = pageMap.remove(slot) != null;
        if (pageMap.isEmpty()) slotAssignments.remove(page);
        if (removed) saveSlotAssignments();
        return removed;
    }

    // ── Ouverture ──────────────────────────────────────────────────────────────

    /** Ouvre la première page du GUI pour le joueur donné. */
    public void open(Player player) {
        open(player, 0);
    }

    /** Ouvre une page précise (0-based) du GUI pour le joueur donné. */
    public void open(Player player, int page) {
        Inventory inv = buildInventory(page);
        player.openInventory(inv);
    }

    // ── Placement des arènes ──────────────────────────────────────────────────

    /**
     * Calcule le placement final de TOUTES les arènes sur TOUTES les pages :
     * d'abord les assignations explicites (/hb guislot), puis les arènes restantes
     * remplies automatiquement dans l'ordre, page par page, sur les emplacements
     * encore libres (en débordant sur la page suivante si besoin).
     */
    private Map<Integer, Map<Integer, GameManager>> computeFullPlacement() {
        Collection<GameManager> allArenas = plugin.getArenaManager().getAll();

        Map<String, GameManager> byName = new LinkedHashMap<>();
        for (GameManager gm : allArenas) {
            byName.put(gm.getName(), gm);
        }

        Map<Integer, Map<Integer, GameManager>> placement = new LinkedHashMap<>();
        Set<String> placedArenaNames = new HashSet<>();

        for (Map.Entry<Integer, Map<Integer, String>> pageEntry : slotAssignments.entrySet()) {
            int page = pageEntry.getKey();
            for (Map.Entry<Integer, String> slotEntry : pageEntry.getValue().entrySet()) {
                int slot = slotEntry.getKey();
                if (slot < 0 || slot >= PAGE_SIZE) continue; // sécurité si le fichier a été édité à la main
                GameManager gm = byName.get(slotEntry.getValue());
                if (gm == null) continue; // l'arène assignée a peut-être été supprimée depuis
                placement.computeIfAbsent(page, k -> new LinkedHashMap<>()).put(slot, gm);
                placedArenaNames.add(gm.getName());
            }
        }

        int page = 0;
        int slot = 0;
        for (GameManager gm : allArenas) {
            if (placedArenaNames.contains(gm.getName())) continue;
            while (placement.getOrDefault(page, Collections.emptyMap()).containsKey(slot)) {
                slot++;
                if (slot >= PAGE_SIZE) { slot = 0; page++; }
            }
            placement.computeIfAbsent(page, k -> new LinkedHashMap<>()).put(slot, gm);
            slot++;
            if (slot >= PAGE_SIZE) { slot = 0; page++; }
        }

        return placement;
    }

    /** Nombre total de pages (au moins 1), en tenant compte des pages réservées par un admin. */
    private int getTotalPages() {
        int maxPage = 0;
        for (Integer p : computeFullPlacement().keySet()) maxPage = Math.max(maxPage, p);
        for (Integer p : slotAssignments.keySet()) maxPage = Math.max(maxPage, p);
        return maxPage + 1;
    }

    // ── Construction de l'inventaire ─────────────────────────────────────────

    /** Titre affiché pour une page donnée (numéro de page seulement si plusieurs pages). */
    private String titleFor(int page, int totalPages) {
        if (totalPages <= 1) return GUI_TITLE_BASE;
        return GUI_TITLE_BASE + ChatColor.RESET + ChatColor.GRAY + " (" + (page + 1) + "/" + totalPages + ")";
    }

    /**
     * Construit et retourne l'inventaire rempli pour la page donnée (0-based, bornée
     * automatiquement à l'intervalle valide).
     */
    public Inventory buildInventory(int page) {
        int totalPages = getTotalPages();
        if (page < 0) page = 0;
        if (page > totalPages - 1) page = totalPages - 1;

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, titleFor(page, totalPages));

        Map<Integer, GameManager> pagePlacement = computeFullPlacement().getOrDefault(page, Collections.emptyMap());
        for (Map.Entry<Integer, GameManager> entry : pagePlacement.entrySet()) {
            GameManager gm = entry.getValue();
            inv.setItem(entry.getKey(), buildArenaItem(gm, gm.getMaxPlayers()));
        }

        // Remplir les slots vides des lignes 1-5 avec du verre noir
        ItemStack filler = buildFiller();
        for (int i = 0; i < PAGE_SIZE; i++) {
            if (!pagePlacement.containsKey(i)) {
                inv.setItem(i, filler);
            }
        }

        // Dernière ligne : navigation + bouton arène aléatoire
        inv.setItem(SLOT_PREV_PAGE, page > 0 ? buildPageButton(false) : filler);

        Collection<GameManager> allArenas = plugin.getArenaManager().getAll();
        ItemStack randomBtn = buildRandomButton(allArenas);
        for (int i = SLOT_RANDOM_START; i <= SLOT_RANDOM_END; i++) {
            inv.setItem(i, randomBtn);
        }

        inv.setItem(SLOT_NEXT_PAGE, page < totalPages - 1 ? buildPageButton(true) : filler);

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
     * Fonctionne toujours sur TOUTES les arènes du serveur, peu importe la page affichée.
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

    /** Bouton de navigation "page précédente"/"page suivante". */
    private ItemStack buildPageButton(boolean next) {
        ItemStack item = new ItemStack(next ? Material.ARROW : Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(next
                ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Page suivante ▶"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ Page précédente");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Case de remplissage pour les slots vides (lignes 1-5, et navigation désactivée).
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
     * Retourne le nom de l'arène à partir de son slot dans le GUI, pour une page donnée.
     * Retourne null si le slot est en dehors de la zone des arènes.
     */
    public String getArenaNameAt(int page, int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) return null;
        GameManager gm = computeFullPlacement().getOrDefault(page, Collections.emptyMap()).get(slot);
        return gm != null ? gm.getName() : null;
    }

    /** Retourne true si le slot cliqué correspond au bouton "aléatoire". */
    public static boolean isRandomButton(int slot) {
        return slot >= SLOT_RANDOM_START && slot <= SLOT_RANDOM_END;
    }

    /** Retourne true si le slot cliqué correspond au bouton "page précédente". */
    public static boolean isPrevPageButton(int slot) {
        return slot == SLOT_PREV_PAGE;
    }

    /** Retourne true si le slot cliqué correspond au bouton "page suivante". */
    public static boolean isNextPageButton(int slot) {
        return slot == SLOT_NEXT_PAGE;
    }

    /** Retourne true si le titre donné correspond bien à une fenêtre du GUI d'arènes (toute page). */
    public static boolean isArenaGuiTitle(String title) {
        return title != null && title.startsWith(GUI_TITLE_BASE);
    }

    /** Extrait le numéro de page (0-based) depuis le titre d'une fenêtre du GUI. Page 0 par défaut. */
    public static int parsePageFromTitle(String title) {
        if (title == null) return 0;
        Matcher matcher = PAGE_TITLE_PATTERN.matcher(title);
        if (!matcher.find()) return 0;
        try {
            return Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
