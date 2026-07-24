package com.hikabrain.plugin.listeners;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;

/**
 * Restreint la visibilité du chat lorsqu'un joueur se trouve dans une arène
 * HikaBrain (en tant que joueur ou spectateur) : seules les personnes présentes
 * dans cette même arène (joueurs + spectateurs) reçoivent le message, au lieu de
 * tout le serveur.
 *
 * On écoute io.papermc.paper.event.player.AsyncChatEvent (l'API de chat moderne
 * de Paper) plutôt que l'ancien org.bukkit.event.player.AsyncPlayerChatEvent,
 * déprécié depuis la 1.19 et dont le Set<Player> de destinataires n'est plus
 * fiablement pris en compte par le pipeline de rendu de Paper. On ne touche pas
 * au format/rendu du message (donc pas à la coloration du pseudo par équipe,
 * déjà gérée ailleurs via le displayName du joueur) : on ne fait que restreindre
 * les destinataires (viewers).
 *
 * Le message reste néanmoins toujours visible dans la console du serveur
 * (et donc dans les logs), avec le nom de l'arène, afin que le staff puisse
 * continuer à modérer/suivre le chat de chaque arène.
 */
public class ArenaChatListener implements Listener {

    private final HikaBrainPlugin plugin;

    public ArenaChatListener(HikaBrainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        GameManager gm = plugin.getArenaManager().findAnyArenaOf(sender);

        if (gm == null) {
            return;
        }

        Set<Player> present = gm.getPresentPlayers();
        event.viewers().removeIf(viewer -> !(viewer instanceof Player player) || !present.contains(player));

        // On journalise nous-mêmes le message dans la console, avec le nom de
        // l'arène, pour garantir que le staff le voit toujours même si le
        // message n'est plus diffusé à tout le serveur en jeu.
        String rawMessage = LegacyComponentSerializer.legacySection().serialize(event.message());
        String formatted = sender.getDisplayName() + ": " + rawMessage;
        plugin.getLogger().info("[Arène " + gm.getName() + "] " + ChatColor.stripColor(formatted));
    }
}
