package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.KitManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Gère la détection des kills et deaths pour les statistiques (globales + individuelles),
 * ainsi que la restriction de visibilité du message de mort/kill à la seule arène
 * concernée (joueurs + spectateurs présents), au lieu d'une diffusion à tout le serveur.
 */
public class PlayerDeathListener implements Listener {

    private final HikaBrainPlugin plugin;

    public PlayerDeathListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        GameManager gm = plugin.getArenaManager().findArenaOf(victim);

        if (gm == null) return;
        if (!gm.isPlaying(victim)) return;

        // L'épée, la pioche, la pomme dorée et les blocs du kit ne doivent jamais
        // se retrouver au sol, même en mourant (ils sont regivés au round reset).
        event.getDrops().removeIf(KitManager::isProtectedKitItem);

        restrictDeathMessageToArena(event, gm);

        int teamSize = Math.max(gm.getPlayerCountForTeam(com.hikabrain.plugin.game.Team.RED),
                                gm.getPlayerCountForTeam(com.hikabrain.plugin.game.Team.BLUE));

        Player killer = victim.getKiller();

        if (killer != null && gm.isPlaying(killer) && !killer.equals(victim)) {
            plugin.getStatsManager().addKill(gm.getTeam(killer), teamSize);
            plugin.getStatsManager().addPlayerKill(killer.getUniqueId(), killer.getName(), teamSize);
            gm.addKill(gm.getTeam(killer));
            gm.addPlayerKill(killer.getUniqueId());
        }

        plugin.getStatsManager().addDeath(gm.getTeam(victim), teamSize);
        plugin.getStatsManager().addPlayerDeath(victim.getUniqueId(), victim.getName(), teamSize);
        gm.addDeath(gm.getTeam(victim));
        gm.addPlayerDeath(victim.getUniqueId());
    }

    /**
     * Empêche le message de mort/kill par défaut d'être diffusé à tout le serveur :
     * on l'envoie nous-mêmes uniquement aux joueurs et spectateurs présents dans
     * l'arène concernée, puis on le journalise dans la console avec le nom de
     * l'arène pour que le staff garde une trace de tous les kills/morts.
     */
    private void restrictDeathMessageToArena(PlayerDeathEvent event, GameManager gm) {
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) {
            return;
        }

        // On coupe la diffusion par défaut (tout le serveur) : le message ne sera
        // envoyé qu'aux destinataires que l'on choisit nous-mêmes ci-dessous.
        event.deathMessage(null);

        for (Player viewer : gm.getPresentPlayers()) {
            viewer.sendMessage(deathMessage);
        }

        String rawMessage = LegacyComponentSerializer.legacySection().serialize(deathMessage);
        plugin.getLogger().info("[Arène " + gm.getName() + "] " + ChatColor.stripColor(rawMessage));
    }
}
