package fr.spide;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Clés persistantes utilisées pour marquer les items "spide" (arc et flèche donnés en jeu)
 * afin de les rendre indéplaçables / non-droppables (voir ItemLockListener) et de les
 * distinguer d'un arc ou d'une flèche ramassés normalement ailleurs sur le serveur.
 */
public final class ItemTags {

    public static NamespacedKey BOW;
    public static NamespacedKey ARROW;

    private ItemTags() {
    }

    public static void init(Plugin plugin) {
        BOW = new NamespacedKey(plugin, "spide-bow-item");
        ARROW = new NamespacedKey(plugin, "spide-arrow-item");
    }
}
