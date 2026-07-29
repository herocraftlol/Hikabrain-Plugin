package fr.spide;

import fr.spide.command.SpideCommand;
import fr.spide.listener.ArrowListener;
import fr.spide.listener.GuiListener;
import fr.spide.listener.ItemLockListener;
import fr.spide.listener.PlayerStateListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Spide extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        ItemTags.init(this);

        gameManager = new GameManager(this);
        gameManager.load();

        SpideCommand spideCommand = new SpideCommand(gameManager);
        getCommand("sp").setExecutor(spideCommand);
        getCommand("sp").setTabCompleter(spideCommand);

        getServer().getPluginManager().registerEvents(new GuiListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new ArrowListener(this, gameManager), this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new ItemLockListener(), this);

        getLogger().info("Spide activé.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.save();
        }
        getLogger().info("Spide désactivé.");
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
