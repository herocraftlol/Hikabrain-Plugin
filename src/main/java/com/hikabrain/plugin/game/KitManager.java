package com.hikabrain.plugin.game;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fabrique et applique le kit de départ HikaBrain :
 * - Slot 0 : réservé au diamant admin (lobby uniquement)
 * - Slot 1 : épée en fer, incassable
 * - Slot 2 : pioche en fer, incassable
 * - Slot 3 : pomme dorée (regivée à chaque mort / point marqué)
 * - Offhand : 64 grès lisse (regivé à 64 dès qu'il en manque)
 * - Armure en cuir complète teintée selon l'équipe, incassable, avec le nom de l'équipe gravé
 */
public final class KitManager {

    // Slot 0 : réservé au diamant admin (lobby uniquement)
    public static final int FORCESTART_SLOT = 0;
    // Slots décalés pour laisser le slot 0 au diamant admin au lobby
    private static final int SWORD_SLOT = 1;
    private static final int PICKAXE_SLOT = 2;
    private static final int GAPPLE_SLOT = 3;

    public static final Material OFFHAND_BLOCK_MATERIAL = Material.SMOOTH_SANDSTONE;
    public static final int OFFHAND_BLOCK_AMOUNT = 64;

    /**
     * Clé persistante utilisée pour identifier de façon fiable l'item "forcer le démarrage"
     * dans un clic, plutôt que de comparer un nom affiché (fragile face aux traductions/styles).
     */
    private static NamespacedKey forceStartKey;

    private KitManager() {
    }

    public static void init(org.bukkit.plugin.Plugin plugin) {
        forceStartKey = new NamespacedKey(plugin, "force_start_item");
    }

    /**
     * Crée l'item diamant donné uniquement aux admins dans le lobby, qui permet de
     * forcer le démarrage immédiat de la partie en un clic (raccourci visuel pour /hb start).
     */
    public static ItemStack createForceStartItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Forcer le démarrage");
            meta.setLore(java.util.List.of(ChatColor.GRAY + "Clique pour lancer la partie immédiatement"));
            meta.getPersistentDataContainer().set(forceStartKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Détermine si l'ItemStack donné est bien l'item "forcer le démarrage" (et non un diamant
     * normal que le joueur aurait par ailleurs).
     */
    public static boolean isForceStartItem(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(forceStartKey, PersistentDataType.BYTE);
    }

    /**
     * Équipe entièrement un joueur avec le kit de départ complet (armure + outils + pomme + grès).
     * Utilisé au début de partie et à chaque round reset.
     */
    public static void giveFullKit(org.bukkit.entity.Player player, Team team) {
        PlayerInventory inv = player.getInventory();
        inv.clear();

        inv.setItem(SWORD_SLOT, makeUnbreakable(new ItemStack(Material.IRON_SWORD)));
        inv.setItem(PICKAXE_SLOT, makeUnbreakable(new ItemStack(Material.IRON_PICKAXE)));
        inv.setItem(GAPPLE_SLOT, new ItemStack(Material.GOLDEN_APPLE, 1));

        inv.setItemInOffHand(new ItemStack(OFFHAND_BLOCK_MATERIAL, OFFHAND_BLOCK_AMOUNT));

        equipArmor(player, team);
    }

    /**
     * Redonne uniquement la pomme dorée (slot 3), sans toucher au reste de l'équipement.
     * Utilisé à chaque mort et à chaque point marqué.
     */
    public static void regiveGoldenApple(org.bukkit.entity.Player player) {
        player.getInventory().setItem(GAPPLE_SLOT, new ItemStack(Material.GOLDEN_APPLE, 1));
    }

    /**
     * Vérifie que le joueur a bien 64 grès lisse en offhand, et complète si besoin.
     * Le joueur peut poser/casser ce bloc normalement ; on le réapprovisionne juste à 64.
     */
    public static void replenishOffhandBlocks(org.bukkit.entity.Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType() != OFFHAND_BLOCK_MATERIAL) {
            player.getInventory().setItemInOffHand(new ItemStack(OFFHAND_BLOCK_MATERIAL, OFFHAND_BLOCK_AMOUNT));
        } else if (offhand.getAmount() < OFFHAND_BLOCK_AMOUNT) {
            offhand.setAmount(OFFHAND_BLOCK_AMOUNT);
            player.getInventory().setItemInOffHand(offhand);
        }
    }

    private static void equipArmor(org.bukkit.entity.Player player, Team team) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);

        helmet = dyeAndLock(helmet, team);
        chestplate = dyeAndLock(chestplate, team);
        leggings = dyeAndLock(leggings, team);
        boots = dyeAndLock(boots, team);

        PlayerInventory inv = player.getInventory();
        inv.setHelmet(helmet);
        inv.setChestplate(chestplate);
        inv.setLeggings(leggings);
        inv.setBoots(boots);
    }

    private static ItemStack dyeAndLock(ItemStack armorPiece, Team team) {
        if (armorPiece.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(team.getArmorColor());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            armorPiece.setItemMeta(meta);
        }
        return armorPiece;
    }

    private static ItemStack makeUnbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }
}
