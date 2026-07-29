package fr.spide.listener;

import fr.spide.GameManager;
import fr.spide.gui.MapGUI;
import fr.spide.model.MapState;
import fr.spide.model.SpideMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {

    private final GameManager gameManager;

    public GuiListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MapGUI gui)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= MapGUI.SIZE) return;

        String mapName = gui.getMapAt(slot);
        if (mapName == null) return;

        SpideMap map = gameManager.getMap(mapName);
        if (map == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;
        player.closeInventory();

        if (map.getState() == MapState.AVAILABLE) {
            if (!gameManager.joinAsPlayer(player, map)) {
                player.sendMessage("§cImpossible de rejoindre cette map (équipes complètes).");
            }
        } else if (map.getState() == MapState.OCCUPIED) {
            gameManager.joinAsSpectator(player, map);
        } else {
            player.sendMessage("§6Cette map est en maintenance.");
        }
    }
}
