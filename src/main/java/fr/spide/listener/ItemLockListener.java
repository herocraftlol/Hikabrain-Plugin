package fr.spide.listener;

import fr.spide.ItemTags;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * L'arc et la flèche donnés en jeu (voir GameManager#giveLoadout) sont tagués via
 * PersistentDataContainer ; ce listener les rend indéplaçables (inventaire), non-jetables
 * et empêche de les passer en main secondaire, en plus d'être incassables (Unbreakable,
 * réglé directement sur l'ItemMeta au moment de la création).
 */
public class ItemLockListener implements Listener {

    private boolean isLocked(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(ItemTags.BOW, PersistentDataType.BYTE)
                || meta.getPersistentDataContainer().has(ItemTags.ARROW, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isLocked(event.getMainHandItem()) || isLocked(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (isLocked(event.getCurrentItem()) || isLocked(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isLocked(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (isLocked(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
