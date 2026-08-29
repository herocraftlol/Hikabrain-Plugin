package com.hikabrain.plugin.cosmetics;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GUI de la boutique de cosmétiques :
 *  - Page "catégories" : une icône par catégorie (chapeaux, particules, traînées,
 *    titres, entrées), avec le solde dépensable du joueur toujours affiché.
 *  - Page "catégorie" : la liste des cosmétiques de cette catégorie, avec pour chacun
 *    son prix, son niveau requis, et s'il est possédé/équipé.
 */
public class CosmeticShopGUI {

    public static final String TITLE_CATEGORIES = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2728 Boutique Cosmétique";
    private static final String TITLE_CATEGORY_PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2728 Boutique: ";
    private static final Pattern CATEGORY_TITLE_PATTERN = Pattern.compile(Pattern.quote(ChatColor.stripColor(TITLE_CATEGORY_PREFIX)) + "(.+)");

    private static final int GUI_SIZE = 54;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_BALANCE = 49;

    private final HikaBrainPlugin plugin;

    public CosmeticShopGUI(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CATEGORIES);

        int[] slots = {10, 12, 14, 16, 13};
        CosmeticCategory[] categories = CosmeticCategory.values();
        for (int i = 0; i < categories.length && i < slots.length; i++) {
            inv.setItem(slots[i], buildCategoryItem(player, categories[i]));
        }

        inv.setItem(22, buildBalanceItem(player));

        player.openInventory(inv);
    }

    public void openCategory(Player player, CosmeticCategory category) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, TITLE_CATEGORY_PREFIX + category.getDisplayName());

        List<Cosmetic> items = CosmeticRegistry.byCategory(category);
        for (int i = 0; i < items.size() && i < 45; i++) {
            inv.setItem(i, buildCosmeticItem(player, items.get(i)));
        }

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = items.size(); i < 45; i++) {
            inv.setItem(i, filler);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "\u25C0 Retour aux catégories");
            back.setItemMeta(backMeta);
        }
        inv.setItem(SLOT_BACK, back);
        inv.setItem(SLOT_BALANCE, buildBalanceItem(player));

        player.openInventory(inv);
    }

    private ItemStack buildCategoryItem(Player player, CosmeticCategory category) {
        List<Cosmetic> items = CosmeticRegistry.byCategory(category);
        long ownedCount = items.stream().filter(c -> plugin.getCosmeticManager().isOwned(player.getUniqueId(), c.getId())).count();

        ItemStack item = new ItemStack(iconFor(category));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + category.getIcon() + " " + category.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Possédés: " + ChatColor.WHITE + ownedCount + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + items.size());
            lore.add("");
            lore.add(ChatColor.YELLOW + "\u25B6 Cliquer pour parcourir");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material iconFor(CosmeticCategory category) {
        return switch (category) {
            case HAT -> Material.LEATHER_HELMET;
            case PARTICLE -> Material.BLAZE_POWDER;
            case TRAIL -> Material.FEATHER;
            case TAG -> Material.NAME_TAG;
            case ENTRANCE -> Material.FIREWORK_ROCKET;
        };
    }

    private ItemStack buildBalanceItem(Player player) {
        LevelManager lm = plugin.getLevelManager();
        int balance = lm.getSpendableBalance(player.getUniqueId());
        int level = lm.getLevel(player.getUniqueId());

        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2728 Ton solde");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Points disponibles: " + ChatColor.YELLOW + balance);
            lore.add(ChatColor.GRAY + "Niveau: " + ChatColor.AQUA + level);
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Les points s'obtiennent en jouant");
            lore.add(ChatColor.DARK_GRAY + "des parties HikaBrain !");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildCosmeticItem(Player player, Cosmetic cosmetic) {
        var cosmeticManager = plugin.getCosmeticManager();
        boolean owned = cosmeticManager.isOwned(player.getUniqueId(), cosmetic.getId());
        boolean equipped = owned && cosmetic.equals(cosmeticManager.getEquipped(player.getUniqueId(), cosmetic.getCategory()));
        int level = plugin.getLevelManager().getLevel(player.getUniqueId());
        int balance = plugin.getLevelManager().getSpendableBalance(player.getUniqueId());

        ItemStack item = new ItemStack(cosmetic.getIconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cosmetic.getRarity().getColor() + "" + ChatColor.BOLD + MessageUtil.format(cosmetic.getDisplayName()));

            if (cosmetic.getLeatherColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
                leatherMeta.setColor(cosmetic.getLeatherColor().getColor());
            }

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(cosmetic.getRarity().getColor() + "\u2726 " + cosmetic.getRarity().getLabel());
            lore.add("");
            for (String line : wrapLore(cosmetic.getEffectDescription(), 34)) {
                lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + line);
            }
            lore.add("");
            if (equipped) {
                lore.add(ChatColor.GREEN + "\u2714 Équipé");
                lore.add(ChatColor.GRAY + "Cliquer pour déséquiper");
            } else if (owned) {
                lore.add(ChatColor.GREEN + "\u2714 Possédé");
                lore.add(ChatColor.YELLOW + "\u25B6 Cliquer pour équiper");
            } else {
                lore.add(ChatColor.GRAY + "Prix: " + (balance >= cosmetic.getPrice() ? ChatColor.GREEN : ChatColor.RED) + cosmetic.getPrice() + " pts");
                lore.add(ChatColor.GRAY + "Niveau requis: " + (level >= cosmetic.getUnlockLevel() ? ChatColor.GREEN : ChatColor.RED) + cosmetic.getUnlockLevel());
                lore.add("");
                if (level < cosmetic.getUnlockLevel()) {
                    lore.add(ChatColor.RED + "\u2716 Niveau insuffisant");
                } else if (balance < cosmetic.getPrice()) {
                    lore.add(ChatColor.RED + "\u2716 Points insuffisants");
                } else {
                    lore.add(ChatColor.YELLOW + "\u25B6 Cliquer pour acheter");
                }
            }
            meta.setLore(lore);

            if (equipped) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Découpe un texte en plusieurs lignes de lore, sans couper un mot en deux. */
    private static List<String> wrapLore(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    // ── Identification depuis le titre (pour le listener) ────────────────────────

    public static boolean isCategoriesTitle(String title) {
        return TITLE_CATEGORIES.equals(title);
    }

    public static CosmeticCategory parseCategoryFromTitle(String title) {
        if (title == null) return null;
        Matcher matcher = CATEGORY_TITLE_PATTERN.matcher(ChatColor.stripColor(title));
        if (!matcher.matches()) return null;
        String name = matcher.group(1);
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (category.getDisplayName().equals(name)) return category;
        }
        return null;
    }
}
