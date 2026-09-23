package com.hikabrain.plugin;

import com.hikabrain.plugin.commands.HikaBrainCommand;
import com.hikabrain.plugin.game.ArenaManager;
import com.hikabrain.plugin.game.KitManager;
import com.hikabrain.plugin.gui.ArenaGUI;
import com.hikabrain.plugin.gui.ArenaGUIListener;
import com.hikabrain.plugin.gui.TeamSelectGUI;
import com.hikabrain.plugin.gui.TeamSelectGUIListener;
import com.hikabrain.plugin.hologram.CategoryLeaderboardManager;
import com.hikabrain.plugin.hologram.StatsHologramManager;
import com.hikabrain.plugin.levels.LevelManager;
import com.hikabrain.plugin.cosmetics.CosmeticManager;
import com.hikabrain.plugin.cosmetics.CosmeticShopGUI;
import com.hikabrain.plugin.music.MusicManager;
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
import com.hikabrain.plugin.stats.HeadToHeadManager;
import com.hikabrain.plugin.stats.MatchHistoryManager;
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
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class HikaBrainPlugin extends JavaPlugin {

    private ArenaManager         arenaManager;
    private ScoreboardManager    scoreboardManager;
    private StatsManager         statsManager;
    private HeadToHeadManager    headToHeadManager;
    private MatchHistoryManager  matchHistoryManager;
    private LevelManager         levelManager;
    private MusicManager         musicManager;
    private CosmeticManager      cosmeticManager;
    private CosmeticShopGUI      cosmeticShopGUI;
    private ArenaGUI             arenaGUI;
    private TeamSelectGUI        teamSelectGUI;
    private CategoryLeaderboardManager leaderboardManager;
    private StatsHologramManager       statsHologramManager;
    private LeaderboardExportServer    leaderboardExportServer;

    private DuelArenaManager          duelArenaManager;
    private TournamentHistoryManager  tournamentHistoryManager;
    private TournamentHologramManager tournamentHologramManager;
    private TournamentManager         tournamentManager;
    private TournamentGUI             tournamentGUI;
    private TournamentRoomsGUI        tournamentRoomsGUI;
    private org.bukkit.configuration.file.FileConfiguration spaceConfig;
    private com.spaceship.plugin.game.ArenaManager spaceArenaManager;
    private com.spaceship.plugin.scoreboard.ScoreboardManager spaceScoreboardManager;
    private com.spaceship.plugin.stats.StatsManager spaceStatsManager;
    private com.spaceship.plugin.gui.ArenaGUI spaceArenaGUI;
    private com.spaceship.plugin.gui.TeamSelectGUI spaceTeamSelectGUI;
    private com.spaceship.plugin.hologram.CategoryLeaderboardManager spaceLeaderboardManager;
    private com.spaceship.plugin.hologram.LongestGamesLeaderboardManager spaceLongestGames;
    private com.spaceship.plugin.stats.GameHistoryManager spaceHistoryManager;
    private com.spaceship.plugin.tournament.TournamentManager spaceTournamentManager;
    private org.bukkit.configuration.file.FileConfiguration cabinConfig;
    private fr.cabintransport.manager.RouteManager cabinRouteManager;
    private fr.cabintransport.manager.JourneyManager cabinJourneyManager;
    private fr.cabintransport.manager.DiscoveryManager cabinDiscoveryManager;
    private com.hikabrain.plugin.lobby.LobbyManager lobbyManager;
    private fr.cabintransport.util.Messages cabinMessages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("spaceship-config.yml", false);
        this.spaceConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "spaceship-config.yml"));
        saveResource("cabin-config.yml", false);
        this.cabinConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "cabin-config.yml"));

        this.arenaManager      = new ArenaManager(this);
        this.arenaManager.loadAll();
        this.scoreboardManager = new ScoreboardManager(this);
        this.statsManager      = new StatsManager(this);
        this.headToHeadManager = new HeadToHeadManager(this);
        this.matchHistoryManager = new MatchHistoryManager(this);
        this.levelManager      = new LevelManager(this);
        this.musicManager      = new MusicManager(this);
        this.cosmeticManager   = new CosmeticManager(this);
        this.cosmeticShopGUI   = new CosmeticShopGUI(this);
        this.leaderboardManager = new CategoryLeaderboardManager(this);
        this.statsHologramManager = new StatsHologramManager(this);
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

        // Respawn instantané (mondes déjà chargés)
        for (World world : getServer().getWorlds()) {
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        }
        // ... et pour tout monde chargé PLUS TARD (voir WorldLoadListener)
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.listeners.WorldLoadListener(), this);

        // Commandes
        HikaBrainCommand commandExecutor = new HikaBrainCommand(this);
        getCommand("hb").setExecutor(commandExecutor);
        getCommand("hb").setTabCompleter(commandExecutor);
        getCommand("arenas").setExecutor((sender, command, label, args) -> {
            commandExecutor.onCommand(sender, command, label, new String[]{"arenas"});
            return true;
        });
        getCommand("cosmetics").setExecutor((sender, command, label, args) -> {
            commandExecutor.onCommand(sender, command, label, new String[]{"cosmetics"});
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
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.listeners.QuickChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.listeners.CosmeticShopListener(this), this);
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.listeners.CosmeticVisibilityListener(this), this);
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.listeners.CosmeticChatListener(this), this);
        getServer().getPluginManager().registerEvents(new com.hikabrain.plugin.lobby.LobbyJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaGUIListener(this, arenaGUI), this);
        getServer().getPluginManager().registerEvents(new TeamSelectGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new TournamentListener(this), this);
        getServer().getPluginManager().registerEvents(new TournamentGUIListener(this, tournamentGUI), this);
        getServer().getPluginManager().registerEvents(new TournamentRoomsGUIListener(this), this);

        // SpaceShip intégré dans le même plugin, avec ses propres gestionnaires et fichiers.
        spaceArenaManager = new com.spaceship.plugin.game.ArenaManager(this); spaceArenaManager.loadAll();
        spaceScoreboardManager = new com.spaceship.plugin.scoreboard.ScoreboardManager(this);
        spaceStatsManager = new com.spaceship.plugin.stats.StatsManager(this);
        spaceLeaderboardManager = new com.spaceship.plugin.hologram.CategoryLeaderboardManager(this);
        spaceLongestGames = new com.spaceship.plugin.hologram.LongestGamesLeaderboardManager(this);
        spaceHistoryManager = new com.spaceship.plugin.stats.GameHistoryManager(this);
        com.spaceship.plugin.game.KitManager.init(this);
        spaceArenaGUI = new com.spaceship.plugin.gui.ArenaGUI(this);
        spaceTeamSelectGUI = new com.spaceship.plugin.gui.TeamSelectGUI(this);
        spaceTournamentManager = new com.spaceship.plugin.tournament.TournamentManager(this); spaceTournamentManager.loadAll();
        com.spaceship.plugin.commands.SpaceShipCommand ssCommand = new com.spaceship.plugin.commands.SpaceShipCommand(this);
        getCommand("ss").setExecutor(ssCommand); getCommand("ss").setTabCompleter(ssCommand);
        getCommand("ssarenas").setExecutor((sender, command, label, args) -> { ssCommand.onCommand(sender, command, label, new String[]{"arenas"}); return true; });
        com.spaceship.plugin.tournament.TournamentCommand ssTournament = new com.spaceship.plugin.tournament.TournamentCommand(this);
        getCommand("sstournament").setExecutor(ssTournament); getCommand("sstournament").setTabCompleter(ssTournament);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.TeamSelectListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.ForceStartItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.LeaveItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.PlayerItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.gui.ArenaGUIListener(this, spaceArenaGUI), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.gui.TeamSelectGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.listeners.SpectatorListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spaceship.plugin.tournament.TournamentListener(this), this);

        // Cinématique de découverte du lobby intégrée au plugin principal.
        cabinRouteManager = new fr.cabintransport.manager.RouteManager(this);
        cabinRouteManager.load();
        cabinJourneyManager = new fr.cabintransport.manager.JourneyManager(this);
        cabinDiscoveryManager = new fr.cabintransport.manager.DiscoveryManager(this);
        lobbyManager = new com.hikabrain.plugin.lobby.LobbyManager(this);
        cabinMessages = new fr.cabintransport.util.Messages(this);
        fr.cabintransport.command.TransportCommand transportCommand = new fr.cabintransport.command.TransportCommand(this);
        getCommand("transport").setExecutor(transportCommand);
        getCommand("transport").setTabCompleter(transportCommand);
        getServer().getPluginManager().registerEvents(new fr.cabintransport.listener.JourneyListener(this), this);

        getLogger().info("HikaBrain activé ! (" + arenaManager.getNames().size() + " arène(s) chargée(s))");
    }

    @Override
    public void onDisable() {
        if (tournamentManager   != null) tournamentManager.shutdown();
        if (leaderboardExportServer != null) leaderboardExportServer.stop();
        if (tournamentHologramManager != null) tournamentHologramManager.shutdown();
        if (duelArenaManager    != null) duelArenaManager.saveAll();
        if (leaderboardManager != null) leaderboardManager.despawnAll();
        if (statsHologramManager != null) statsHologramManager.despawnAll();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (statsManager      != null) statsManager.saveStats();
        if (headToHeadManager != null) headToHeadManager.save();
        if (levelManager      != null) levelManager.save();
        if (arenaManager      != null) { arenaManager.stopAll(); arenaManager.saveAll(); }
        if (spaceTournamentManager != null) { spaceTournamentManager.shutdown(); spaceTournamentManager.saveAll(); }
        if (spaceLeaderboardManager != null) spaceLeaderboardManager.despawnAll();
        if (spaceLongestGames != null) spaceLongestGames.despawnAll();
        if (spaceScoreboardManager != null) spaceScoreboardManager.stop();
        if (spaceStatsManager != null) spaceStatsManager.saveStats();
        if (spaceArenaManager != null) { spaceArenaManager.stopAll(); spaceArenaManager.saveAll(); }
        if (cabinJourneyManager != null) cabinJourneyManager.shutdown();
        if (cabinRouteManager != null) cabinRouteManager.save();
        getLogger().info("HikaBrain désactivé.");
    }

    public ArenaManager         getArenaManager()      { return arenaManager; }
    public ArenaGUI             getArenaGUI()           { return arenaGUI; }
    public TeamSelectGUI        getTeamSelectGUI()      { return teamSelectGUI; }
    public ScoreboardManager    getScoreboardManager()  { return scoreboardManager; }
    public StatsManager         getStatsManager()       { return statsManager; }
    public HeadToHeadManager    getHeadToHeadManager()  { return headToHeadManager; }
    public MatchHistoryManager  getMatchHistoryManager(){ return matchHistoryManager; }
    public LevelManager         getLevelManager()       { return levelManager; }
    public MusicManager         getMusicManager()       { return musicManager; }
    public CosmeticManager      getCosmeticManager()    { return cosmeticManager; }
    public CosmeticShopGUI      getCosmeticShopGUI()    { return cosmeticShopGUI; }
    public CategoryLeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public StatsHologramManager       getStatsHologramManager() { return statsHologramManager; }

    public DuelArenaManager          getDuelArenaManager()          { return duelArenaManager; }
    public TournamentHistoryManager  getTournamentHistoryManager()  { return tournamentHistoryManager; }
    public TournamentHologramManager getTournamentHologramManager() { return tournamentHologramManager; }
    public TournamentManager         getTournamentManager()         { return tournamentManager; }
    public TournamentGUI             getTournamentGUI()             { return tournamentGUI; }
    public TournamentRoomsGUI        getTournamentRoomsGUI()        { return tournamentRoomsGUI; }
    public org.bukkit.configuration.file.FileConfiguration getSpaceConfig() { return spaceConfig; }
    public com.spaceship.plugin.game.ArenaManager getSpaceArenaManager() { return spaceArenaManager; }
    public com.spaceship.plugin.scoreboard.ScoreboardManager getSpaceScoreboardManager() { return spaceScoreboardManager; }
    public com.spaceship.plugin.stats.StatsManager getSpaceStatsManager() { return spaceStatsManager; }
    public com.spaceship.plugin.gui.ArenaGUI getSpaceArenaGUI() { return spaceArenaGUI; }
    public com.spaceship.plugin.gui.TeamSelectGUI getSpaceTeamSelectGUI() { return spaceTeamSelectGUI; }
    public com.spaceship.plugin.hologram.CategoryLeaderboardManager getSpaceLeaderboardManager() { return spaceLeaderboardManager; }
    public com.spaceship.plugin.hologram.LongestGamesLeaderboardManager getSpaceLongestGamesLeaderboardManager() { return spaceLongestGames; }
    public com.spaceship.plugin.stats.GameHistoryManager getSpaceGameHistoryManager() { return spaceHistoryManager; }
    public com.spaceship.plugin.tournament.TournamentManager getSpaceTournamentManager() { return spaceTournamentManager; }
    public org.bukkit.configuration.file.FileConfiguration getCabinConfig() { return cabinConfig; }
    public void saveCabinConfig() { try { cabinConfig.save(new File(getDataFolder(), "cabin-config.yml")); } catch (java.io.IOException e) { getLogger().warning("Impossible de sauvegarder cabin-config.yml: " + e.getMessage()); } }
    public fr.cabintransport.manager.RouteManager getRouteManager() { return cabinRouteManager; }
    public fr.cabintransport.manager.JourneyManager getJourneyManager() { return cabinJourneyManager; }
    public fr.cabintransport.manager.DiscoveryManager getDiscoveryManager() { return cabinDiscoveryManager; }
    public com.hikabrain.plugin.lobby.LobbyManager getLobbyManager() { return lobbyManager; }
    public fr.cabintransport.util.Messages getMessages() { return cabinMessages; }
}
