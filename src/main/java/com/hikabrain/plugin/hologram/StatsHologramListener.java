package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager.GameMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Détecte les clics droit sur l'ArmorStand "tabs" de l'hologramme de stats.
 *
 * N'importe quel joueur peut cliquer pour changer de mode (1v1 → 2v2 → 3v3 → 4v4 → 1v1…).
 * L'ArmorStand est identifié d'abord par son UUID en mémoire, puis en fallback par
 * son tag PDC "hikabrain:hologram_tab" pour robustesse après un rechargement de plugin.
 */
public class StatsHologramListener implements Listener {

    private final HikaBrainPlugin      plugin;
    private final StatsHologramManager hologramManager;
    /** Clé PDC spécifique à l'ArmorStand "tabs" pour fallback d'identification. */
    private final NamespacedKey        tabKey;

    public StatsHologramListener(HikaBrainPlugin plugin, StatsHologramManager hologramManager) {
        this.plugin          = plugin;
        this.hologramManager = hologramManager;
        this.tabKey          = new NamespacedKey(plugin, "hologram_tab");
    }

    /**
     * Retourne la clé PDC utilisée pour marquer l'ArmorStand "tabs".
     * Appelée par StatsHologramManager pour poser le tag lors de la création.
     */
    public NamespacedKey getTabKey() { return tabKey; }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand as)) return;

        // Identification : UUID en mémoire OU tag PDC tab
        boolean isTab = as.getUniqueId().equals(hologramManager.getTabEntityUUID())
                || "tab".equals(as.getPersistentDataContainer().get(tabKey, PersistentDataType.STRING));

        if (!isTab) return;

        event.setCancelled(true);

        GameMode next = nextMode(hologramManager.getCurrentMode());
        hologramManager.setMode(next);

        Player player = event.getPlayer();
        player.sendActionBar(
                Component.text("Statistiques ")
                         .color(NamedTextColor.GRAY)
                         .append(Component.text(next.getLabel())
                                 .color(NamedTextColor.YELLOW))
        );
    }

    private static GameMode nextMode(GameMode current) {
        GameMode[] vals = GameMode.values();
        return vals[(current.ordinal() + 1) % vals.length];
    }
}
