package com.hikabrain.plugin;

import com.hikabrain.plugin.commands.HikaBrainCommand;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.listeners.ArenaProtectionListener;
import com.hikabrain.plugin.listeners.ForceStartItemListener;
import com.hikabrain.plugin.listeners.PlayerConnectionListener;
import com.hikabrain.plugin.listeners.PlayerDamageListener;
import com.hikabrain.plugin.listeners.PlayerDeathListener;
import com.hikabrain.plugin.listeners.PlayerMoveListener;
import com.hikabrain.plugin.listeners.TeamSelectListener;
import com.hikabrain.plugin.scoreboard.ScoreboardManager;
import com.hikabrain.plugin.stats.StatsManager;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class HikaBrainPlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private ScoreboardManager scoreboardManager;
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(this);
        this.arenaManager.loadAll();
        this.scoreboardManager = new ScoreboardManager(this);
        this.statsManager = new StatsManager(this);
        KitManager.init(this);

        // Respawn instantané (sans écran de mort à cliquer) pour une meilleure expérience minijeu.
        // Appliqué globalement à tous les mondes du serveur.
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        }

        // Commandes
        HikaBrainCommand commandExecutor = new HikaBrainCommand(this);
        getCommand("hb").setExecutor(commandExecutor);
        getCommand("hb").setTabCompleter(commandExecutor);

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamSelectListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ForceStartItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerItemListener(this), this);

        getLogger().info("HikaBrain activé ! (" + arenaManager.getNames().size() + " arène(s) chargée(s))");
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (statsManager != null) {
            statsManager.saveStats();
        }
        if (arenaManager != null) {
            arenaManager.stopAll();
            arenaManager.saveAll();
        }
        getLogger().info("HikaBrain désactivé.");
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }
}
