package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.chat.QuickMessage;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.game.KitManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gère les restrictions sur les items du kit (lobby et jeu).
 * - Empêche de lâcher l'item de sélection d'équipe
 * - Empêche de déplacer l'item de sélection d'équipe dans l'inventaire
 * - Empêche de swapper les items (touche F)
 * - Empêche de déplacer, droppée ou perdre l'épée, la pioche, la pomme dorée
 *   et les blocs du kit de jeu, sous aucun prétexte (clic, glisser-déposer,
 *   touche numéro, échange offhand, ou mort)
 * - Empêche de déplacer ou droppée les blocs de message rapide (voir QuickMessage),
 *   sous les mêmes conditions que les items du kit
 */
public class PlayerItemListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerItemListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Un item "protégé" pendant la partie est soit un item du kit normal (épée, pioche,
     * pomme dorée, blocs), soit un bloc de message rapide (présent uniquement pendant le
     * temps d'attente/la victoire, voir QuickMessage) — dans les deux cas, ni drop ni
     * déplacement dans l'inventaire ne doivent être possibles.
     */
    private boolean isProtectedItem(ItemStack item) {
        return KitManager.isProtectedKitItem(item) || QuickMessage.fromItem(item) != null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Spectateur : ne jamais laisser lâcher l'item "quitter le mode spectateur"
        if (plugin.getArenaManager().findSpectatorArenaOf(player) != null) {
            if (KitManager.isSpectatorLeaveItem(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
            }
            return;
        }

        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        
        if (gm == null) return;
        
        ItemStack item = event.getItemDrop().getItemStack();
        
        // En lobby : empêcher de lâcher l'item de sélection d'équipe et l'item quitter
        if (gm.getState() == GameState.WAITING) {
            if (KitManager.isTeamSelectorItem(item) || KitManager.isForceStartItem(item) || KitManager.isLeaveItem(item)) {
                event.setCancelled(true);
            }
        }
        
        // En jeu (kit normal OU blocs de message rapide) : empêcher de lâcher, sous aucun prétexte
        if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET ||
            gm.getState() == GameState.COUNTDOWN || gm.getState() == GameState.STARTING
            || gm.getState() == GameState.ENDING) {
            if (isProtectedItem(item)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Spectateur : ne jamais laisser déplacer l'item "quitter le mode spectateur"
        if (plugin.getArenaManager().findSpectatorArenaOf(player) != null) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && KitManager.isSpectatorLeaveItem(clicked)) {
                event.setCancelled(true);
            }
            return;
        }

        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        
        if (gm == null) return;
        
        ItemStack item = event.getCurrentItem();
        
        // En lobby : empêcher de déplacer l'item de sélection d'équipe et l'item quitter
        if (gm.getState() == GameState.WAITING) {
            if (KitManager.isTeamSelectorItem(item) || KitManager.isForceStartItem(item) || KitManager.isLeaveItem(item)) {
                event.setCancelled(true);
            }
            return;
        }
        
        // En jeu (kit normal OU blocs de message rapide) : empêcher de déplacer, sous
        // aucun prétexte. On vérifie l'item du slot cliqué, l'item déjà sur le curseur
        // (au cas où), et, pour un clic "touche numéro" ou "échange offhand", l'item du
        // slot réellement impliqué dans l'échange (qui n'est pas forcément getCurrentItem()).
        if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET ||
            gm.getState() == GameState.COUNTDOWN || gm.getState() == GameState.STARTING
            || gm.getState() == GameState.ENDING) {

            boolean touchesProtectedItem = isProtectedItem(item)
                || isProtectedItem(event.getCursor());

            if (!touchesProtectedItem && event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                touchesProtectedItem = isProtectedItem(hotbarItem);
            }

            if (!touchesProtectedItem && event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = player.getInventory().getItemInOffHand();
                touchesProtectedItem = isProtectedItem(offhandItem);
            }

            if (touchesProtectedItem) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        if (gm == null) return;

        if (gm.getState() != GameState.PLAYING && gm.getState() != GameState.ROUND_RESET
            && gm.getState() != GameState.COUNTDOWN && gm.getState() != GameState.STARTING
            && gm.getState() != GameState.ENDING) {
            return;
        }

        // Empêche de faire glisser un item protégé (kit ou message rapide) vers un autre
        // slot, y compris en répartissant la pile sur plusieurs cases.
        if (isProtectedItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findArenaOf(player);
        
        if (gm == null) return;
        
        if (gm.getState() == GameState.WAITING || gm.getState() == GameState.PLAYING || 
            gm.getState() == GameState.ROUND_RESET || gm.getState() == GameState.COUNTDOWN
            || gm.getState() == GameState.STARTING || gm.getState() == GameState.ENDING) {
            event.setCancelled(true);
        }
    }
}
