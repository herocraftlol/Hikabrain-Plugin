package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.cosmetics.Cosmetic;
import com.hikabrain.plugin.cosmetics.CosmeticCategory;
import com.hikabrain.plugin.cosmetics.CosmeticManager;
import com.hikabrain.plugin.cosmetics.CosmeticRegistry;
import com.hikabrain.plugin.cosmetics.CosmeticShopGUI;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gère les clics dans le GUI de la boutique de cosmétiques (voir {@link CosmeticShopGUI}).
 */
public class CosmeticShopListener implements Listener {

    private final HikaBrainPlugin plugin;

    public CosmeticShopListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isCategories = CosmeticShopGUI.isCategoriesTitle(title);
        CosmeticCategory category = isCategories ? null : CosmeticShopGUI.parseCategoryFromTitle(title);

        if (!isCategories && category == null) return; // pas notre GUI
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (isCategories) {
            int slot = event.getRawSlot();
            CosmeticCategory[] categories = CosmeticCategory.values();
            int[] slots = {10, 12, 14, 16, 13};
            for (int i = 0; i < categories.length && i < slots.length; i++) {
                if (slots[i] == slot) {
                    plugin.getCosmeticShopGUI().openCategory(player, categories[i]);
                    return;
                }
            }
            return;
        }

        // Vue "catégorie"
        int slot = event.getRawSlot();
        if (slot == 45) { // bouton retour
            plugin.getCosmeticShopGUI().openCategories(player);
            return;
        }
        if (slot >= 46 || slot >= CosmeticRegistry.byCategory(category).size()) return; // filler/hors-liste

        Cosmetic cosmetic = CosmeticRegistry.byCategory(category).get(slot);
        handleCosmeticClick(player, cosmetic);
        // Rafraîchit la vue pour refléter l'achat/équipement immédiatement
        plugin.getCosmeticShopGUI().openCategory(player, category);
    }

    private void handleCosmeticClick(Player player, Cosmetic cosmetic) {
        CosmeticManager manager = plugin.getCosmeticManager();
        boolean owned = manager.isOwned(player.getUniqueId(), cosmetic.getId());

        if (owned) {
            boolean equipped = cosmetic.equals(manager.getEquipped(player.getUniqueId(), cosmetic.getCategory()));
            if (equipped) {
                manager.unequip(player, cosmetic.getCategory());
                MessageUtil.send(player, "&7Cosmétique déséquipé : " + MessageUtil.format(cosmetic.getDisplayName()));
            } else {
                manager.equip(player, cosmetic);
                MessageUtil.send(player, "&aCosmétique équipé : " + MessageUtil.format(cosmetic.getDisplayName())
                        + " &7(visible uniquement hors des arènes HikaBrain)");
            }
            return;
        }

        CosmeticManager.PurchaseResult result = manager.purchase(player, cosmetic);
        switch (result) {
            case SUCCESS -> {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                MessageUtil.send(player, "&aAchat réussi : " + MessageUtil.format(cosmetic.getDisplayName())
                        + " &7(&e-" + cosmetic.getPrice() + " pts&7). Clique à nouveau dessus pour l'équiper !");
            }
            case LEVEL_TOO_LOW -> MessageUtil.send(player, "&cNiveau insuffisant : il te faut le niveau &e"
                    + cosmetic.getUnlockLevel() + " &cpour débloquer ce cosmétique.");
            case INSUFFICIENT_FUNDS -> MessageUtil.send(player, "&cPoints insuffisants : il te manque &e"
                    + (cosmetic.getPrice() - plugin.getLevelManager().getSpendableBalance(player.getUniqueId())) + " points&c.");
            case ALREADY_OWNED -> { /* ne devrait pas arriver ici, déjà filtré au-dessus */ }
        }
    }
}
