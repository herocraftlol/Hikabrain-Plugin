package com.hikabrain.plugin;

import com.hikabrain.plugin.commands.HikaBrainCommand;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.gui.ArenaGUI;
import com.hikabrain.plugin.gui.ArenaGUIListener;
import com.hikabrain.plugin.hologram.CategoryLeaderboardManager;
import com.hikabrain.plugin.listeners.ArenaProtectionListener;
import com.hikabrain.plugin.listeners.BlockPlaceListener;
import com.hikabrain.plugin.listeners.ForceStartItemListener;
import com.hikabrain.plugin.listeners.LeaveItemListener;
import com.hikabrain.plugin.listeners.PlayerConnectionListener;
import com.hikabrain.plugin.listeners.PlayerDamageListener;
import com.hikabrain.plugin.listeners.PlayerDeathListener;
import com.hikabrain.plugin.listeners.PlayerItemListener;
import com.hikabrain.plugin.listeners.PlayerMoveListener;
import com.hikabrain.plugin.listeners.TeamSelectListener;
import com.hikabrain.plugin.scoreboard.ScoreboardManager;
import com.hikabrain.plugin.stats.StatsManager;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class HikaBrainPlugin extends JavaPlugin {

    private ArenaManager         arenaManager;
    private ScoreboardManager    scoreboardManager;
    private StatsManager         statsManager;
    private ArenaGUI             arenaGUI;
    private CategoryLeaderboardManager leaderboardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager      = new ArenaManager(this);
        this.arenaManager.loadAll();
        this.scoreboardManager = new ScoreboardManager(this);
        this.statsManager      = new StatsManager(this);
        this.leaderboardManager = new CategoryLeaderboardManager(this);
        KitManager.init(this);

        this.arenaGUI = new ArenaGUI(this);

        // Respawn instantané
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        }

        // Commandes
        HikaBrainCommand commandExecutor = new HikaBrainCommand(this);
        getCommand("hb").setExecutor(commandExecutor);
        getCommand("hb").setTabCompleter(commandExecutor);
        getCommand("arenas").setExecutor((sender, command, label, args) -> {
            commandExecutor.onCommand(sender, command, label, new String[]{"arenas"});
            return true;
        });

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamSelectListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ForceStartItemListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaveItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerItemListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaGUIListener(this, arenaGUI), this);

        getLogger().info("HikaBrain activé ! (" + arenaManager.getNames().size() + " arène(s) chargée(s))");
    }

    @Override
    public void onDisable() {
        if (leaderboardManager != null) leaderboardManager.despawnAll();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (statsManager      != null) statsManager.saveStats();
        if (arenaManager      != null) { arenaManager.stopAll(); arenaManager.saveAll(); }
        getLogger().info("HikaBrain désactivé.");
    }

    public ArenaManager         getArenaManager()      { return arenaManager; }
    public ArenaGUI             getArenaGUI()           { return arenaGUI; }
    public ScoreboardManager    getScoreboardManager()  { return scoreboardManager; }
    public StatsManager         getStatsManager()       { return statsManager; }
    public CategoryLeaderboardManager getLeaderboardManager() { return leaderboardManager; }
}
