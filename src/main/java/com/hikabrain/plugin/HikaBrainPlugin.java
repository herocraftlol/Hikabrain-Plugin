package com.hikabrain.plugin;

import com.hikabrain.plugin.commands.HikaBrainCommand;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.gui.ArenaGUI;
import com.hikabrain.plugin.gui.ArenaGUIListener;
import com.hikabrain.plugin.gui.TeamSelectGUI;
import com.hikabrain.plugin.gui.TeamSelectGUIListener;
import com.hikabrain.plugin.hologram.CategoryLeaderboardManager;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.listeners.ArenaChatListener;
import com.hikabrain.plugin.listeners.ArenaProtectionListener;
import com.hikabrain.plugin.listeners.BlockPlaceListener;
import com.hikabrain.plugin.listeners.ForceStartItemListener;
import com.hikabrain.plugin.listeners.LeaveItemListener;
import com.hikabrain.plugin.listeners.PlayerConnectionListener;
import com.hikabrain.plugin.listeners.PlayerDamageListener;
import com.hikabrain.plugin.listeners.PlayerDeathListener;
import com.hikabrain.plugin.listeners.PlayerItemListener;
import com.hikabrain.plugin.listeners.PlayerMoveListener;
import com.hikabrain.plugin.listeners.PlayerPvpListener;
import com.hikabrain.plugin.listeners.TeamSelectListener;
import com.hikabrain.plugin.scoreboard.ScoreboardManager;
import com.hikabrain.plugin.stats.StatsManager;
import com.hikabrain.plugin.tournament.DuelArenaManager;
import com.hikabrain.plugin.tournament.TournamentCommand;
import com.hikabrain.plugin.tournament.TournamentListener;
import com.hikabrain.plugin.tournament.TournamentManager;
import com.hikabrain.plugin.tournament.gui.TournamentGUI;
import com.hikabrain.plugin.tournament.gui.TournamentGUIListener;
import com.hikabrain.plugin.tournament.gui.TournamentRoomsGUI;
import com.hikabrain.plugin.tournament.gui.TournamentRoomsGUIListener;
import com.hikabrain.plugin.tournament.history.TournamentHistoryManager;
import com.hikabrain.plugin.tournament.hologram.TournamentHologramManager;
import com.hikabrain.plugin.web.LeaderboardExportServer;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class HikaBrainPlugin extends JavaPlugin {

    private ArenaManager         arenaManager;
    private ScoreboardManager    scoreboardManager;
    private StatsManager         statsManager;
    private LevelManager         levelManager;
    private ArenaGUI             arenaGUI;
    private TeamSelectGUI        teamSelectGUI;
    private CategoryLeaderboardManager leaderboardManager;
    private LeaderboardExportServer    leaderboardExportServer;

    private DuelArenaManager          duelArenaManager;
    private TournamentHistoryManager  tournamentHistoryManager;
    private TournamentHologramManager tournamentHologramManager;
    private TournamentManager         tournamentManager;
    private TournamentGUI             tournamentGUI;
    private TournamentRoomsGUI        tournamentRoomsGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager      = new ArenaManager(this);
        this.arenaManager.loadAll();
        this.scoreboardManager = new ScoreboardManager(this);
        this.statsManager      = new StatsManager(this);
        this.levelManager      = new LevelManager(this);
        this.leaderboardManager = new CategoryLeaderboardManager(this);
        this.leaderboardExportServer = new LeaderboardExportServer(this);
        this.leaderboardExportServer.start();
        KitManager.init(this);

        this.arenaGUI = new ArenaGUI(this);
        this.teamSelectGUI = new TeamSelectGUI(this);

        // Système de tournoi
        this.duelArenaManager = new DuelArenaManager(this);
        this.duelArenaManager.loadAll();
        this.tournamentHistoryManager = new TournamentHistoryManager(this);
        this.tournamentHologramManager = new TournamentHologramManager(this);
        this.tournamentManager = new TournamentManager(this, duelArenaManager, tournamentHistoryManager, tournamentHologramManager);
        this.tournamentGUI = new TournamentGUI(this);
        this.tournamentRoomsGUI = new TournamentRoomsGUI(this);

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

        TournamentCommand tournamentCommand = new TournamentCommand(this);
        getCommand("tournament").setExecutor(tournamentCommand);
        getCommand("tournament").setTabCompleter(tournamentCommand);

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerPvpListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaChatListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamSelectListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ForceStartItemListener(this), this);
        getServer().getPluginManager().registerEvents(new LeaveItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerItemListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaGUIListener(this, arenaGUI), this);
        getServer().getPluginManager().registerEvents(new TeamSelectGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new TournamentListener(this), this);
        getServer().getPluginManager().registerEvents(new TournamentGUIListener(this, tournamentGUI), this);
        getServer().getPluginManager().registerEvents(new TournamentRoomsGUIListener(this), this);

        getLogger().info("HikaBrain activé ! (" + arenaManager.getNames().size() + " arène(s) chargée(s))");
    }

    @Override
    public void onDisable() {
        if (tournamentManager   != null) tournamentManager.shutdown();
        if (leaderboardExportServer != null) leaderboardExportServer.stop();
        if (tournamentHologramManager != null) tournamentHologramManager.shutdown();
        if (duelArenaManager    != null) duelArenaManager.saveAll();
        if (leaderboardManager != null) leaderboardManager.despawnAll();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (statsManager      != null) statsManager.saveStats();
        if (levelManager      != null) levelManager.save();
        if (arenaManager      != null) { arenaManager.stopAll(); arenaManager.saveAll(); }
        getLogger().info("HikaBrain désactivé.");
    }

    public ArenaManager         getArenaManager()      { return arenaManager; }
    public ArenaGUI             getArenaGUI()           { return arenaGUI; }
    public TeamSelectGUI        getTeamSelectGUI()      { return teamSelectGUI; }
    public ScoreboardManager    getScoreboardManager()  { return scoreboardManager; }
    public StatsManager         getStatsManager()       { return statsManager; }
    public LevelManager         getLevelManager()       { return levelManager; }
    public CategoryLeaderboardManager getLeaderboardManager() { return leaderboardManager; }

    public DuelArenaManager          getDuelArenaManager()          { return duelArenaManager; }
    public TournamentHistoryManager  getTournamentHistoryManager()  { return tournamentHistoryManager; }
    public TournamentHologramManager getTournamentHologramManager() { return tournamentHologramManager; }
    public TournamentManager         getTournamentManager()         { return tournamentManager; }
    public TournamentGUI             getTournamentGUI()             { return tournamentGUI; }
    public TournamentRoomsGUI        getTournamentRoomsGUI()        { return tournamentRoomsGUI; }
}
