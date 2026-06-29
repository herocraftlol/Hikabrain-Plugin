package com.hikabrain.plugin.hologram;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.stats.StatsManager.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/**
 * Écoute les clics sur l'ArmorStand "tab" de l'hologramme pour changer de mode.
 *
 * Clic gauche ou droit sur la ligne des onglets → passe au mode suivant.
 * Le joueur voit un message de confirmation (discret, en action bar).
 */
public class StatsHologramListener implements Listener {

    private final HikaBrainPlugin      plugin;
    private final StatsHologramManager hologramManager;

    public StatsHologramListener(HikaBrainPlugin plugin, StatsHologramManager hologramManager) {
        this.plugin          = plugin;
        this.hologramManager = hologramManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;

        ArmorStand as = (ArmorStand) event.getRightClicked();
        if (!as.getUniqueId().equals(hologramManager.getTabEntityUUID())) return;

        // Annule l'interaction Minecraft par défaut (pas d'équipement, pas de son)
        event.setCancelled(true);

        Player    player = event.getPlayer();
        GameMode  next   = nextMode(hologramManager.getCurrentMode());
        hologramManager.setMode(next);

        // Feedback discret en action bar
        player.sendActionBar(
                net.kyori.adventure.text.Component.text("Mode : " + next.getLabel())
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
        );
    }

    private GameMode nextMode(GameMode current) {
        GameMode[] vals = GameMode.values();
        return vals[(current.ordinal() + 1) % vals.length];
    }
}
