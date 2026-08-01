package com.hikabrain.plugin.gui;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import com.hikabrain.plugin.game.GameState;
import com.hikabrain.plugin.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Écoute les clics dans le GUI d'arènes et effectue les actions correspondantes :
 * - Clic sur une arène disponible → /hb join <nom>
 * - Clic sur une arène en cours de partie → /hb spectate <nom>
 * - Clic sur le bouton aléatoire → /hb joinrandom
 * - Clic sur les flèches de navigation → change de page du GUI (voir {@link ArenaGUI})
 */
public class ArenaGUIListener implements Listener {

    private final HikaBrainPlugin plugin;
    private final ArenaGUI arenaGUI;

    public ArenaGUIListener(HikaBrainPlugin plugin, ArenaGUI arenaGUI) {
        this.plugin = plugin;
        this.arenaGUI = arenaGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Vérifier que c'est bien notre GUI (par le titre, quelle que soit la page affichée)
        String title = event.getView().getTitle();
        if (!ArenaGUI.isArenaGuiTitle(title)) return;

        // Annuler toujours le clic pour éviter de prendre des items
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();
        int page = ArenaGUI.parsePageFromTitle(title);

        // Clic sur une flèche de navigation
        if (ArenaGUI.isPrevPageButton(slot)) {
            arenaGUI.open(player, page - 1);
            return;
        }
        if (ArenaGUI.isNextPageButton(slot)) {
            arenaGUI.open(player, page + 1);
            return;
        }

        // Clic sur le bouton "arène aléatoire" (fonctionne sur toutes les pages/arènes)
        if (ArenaGUI.isRandomButton(slot)) {
            player.closeInventory();
            GameManager best = plugin.getArenaManager().findBestArenaForRandomJoin();
            if (best == null) {
                MessageUtil.send(player, "&cAucune arène disponible pour le moment.");
                return;
            }
            tryJoin(player, best);
            return;
        }

        // Clic sur une arène spécifique (lignes 1-5)
        String arenaName = arenaGUI.getArenaNameAt(page, slot);
        if (arenaName == null) return; // Slot vide / filler

        GameManager gm = plugin.getArenaManager().get(arenaName);
        if (gm == null) {
            MessageUtil.send(player, "&cCette arène n'existe plus.");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Si une partie est en cours sur cette arène, un clic propose de la regarder
        // en spectateur plutôt que de la rejoindre (indisponible pour jouer).
        if (gm.getState() == GameState.PLAYING || gm.getState() == GameState.ROUND_RESET
                || gm.getState() == GameState.STARTING) {
            trySpectate(player, gm);
            return;
        }

        tryJoin(player, gm);
    }

    /**
     * Tente de faire rejoindre le joueur dans l'arène donnée,
     * en déléguant à la logique existante de GameManager.
     */
    private void tryJoin(Player player, GameManager gm) {
        if (!gm.getArena().isFullyConfigured()) {
            MessageUtil.send(player, "&cCette arène n'est pas encore configurée.");
            return;
        }

        // Vérifier si le joueur est déjà dans une arène
        GameManager current = plugin.getArenaManager().findArenaOf(player);
        if (current != null) {
            if (current.getName().equals(gm.getName())) {
                MessageUtil.send(player, "&cTu es déjà dans cette arène !");
            } else {
                MessageUtil.send(player, "&cTu es déjà dans une arène (&7" + current.getName() + "&c). Fais &7/hb leave &cpour en partir.");
            }
            return;
        }

        GameManager currentSpectate = plugin.getArenaManager().findSpectatorArenaOf(player);
        if (currentSpectate != null) {
            MessageUtil.send(player, "&cTu es en mode spectateur (&7" + currentSpectate.getName() + "&c). Fais &7/hb unspectate &cd'abord.");
            return;
        }

        // Déléguer au GameManager
        gm.addPlayer(player);
    }

    /**
     * Tente de faire passer le joueur en mode spectateur sur l'arène donnée.
     */
    private void trySpectate(Player player, GameManager gm) {
        GameManager current = plugin.getArenaManager().findArenaOf(player);
        if (current != null) {
            MessageUtil.send(player, "&cTu es déjà dans une partie (&7" + current.getName() + "&c). Fais &7/hb leave &cd'abord.");
            return;
        }

        GameManager currentSpectate = plugin.getArenaManager().findSpectatorArenaOf(player);
        if (currentSpectate != null) {
            if (currentSpectate.getName().equals(gm.getName())) {
                MessageUtil.send(player, "&cTu regardes déjà cette arène en spectateur.");
                return;
            }
            currentSpectate.removeSpectator(player);
        }

        gm.addSpectator(player);
    }
}
