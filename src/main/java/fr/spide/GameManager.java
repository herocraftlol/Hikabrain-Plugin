package fr.spide;

import fr.spide.model.LocUtil;
import fr.spide.model.MapState;
import fr.spide.model.SpideMap;
import fr.spide.model.Team;
import fr.spide.model.TeamColorRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {

    private final Spide plugin;
    private final Map<String, SpideMap> maps = new LinkedHashMap<>();
    private final TeamColorRegistry colorRegistry = new TeamColorRegistry();
    private final ScoreboardManager scoreboardManager = new ScoreboardManager();
    private Location hub;

    // joueur -> map dans laquelle il se trouve actuellement (lobby ou partie)
    private final Map<UUID, String> playerMap = new HashMap<>();
    // joueur -> map dans laquelle il est spectateur
    private final Map<UUID, String> spectatorMap = new HashMap<>();

    // Débounce : évite de résoudre une manche plusieurs fois si plusieurs éliminations
    // arrivent sur le même tick (ou presque), ce qui provoquait des points en trop /
    // des joueurs coincés en spectateur sans fin de partie propre.
    private final Set<String> pendingRoundCheck = new HashSet<>();

    // Décompte de lancement de partie (10s) en cours par map, une fois le minimum de
    // joueurs atteint.
    private final Map<String, BukkitTask> countdowns = new HashMap<>();

    private File file;
    private FileConfiguration config;

    public GameManager(Spide plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        file = new File(plugin.getDataFolder(), "maps.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible de créer maps.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("hub")) {
            hub = LocUtil.deserialize(config.getConfigurationSection("hub"));
        }

        if (config.contains("teamColors")) {
            colorRegistry.setOrder(config.getStringList("teamColors"));
        }

        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection != null) {
            for (String key : mapsSection.getKeys(false)) {
                ConfigurationSection ms = mapsSection.getConfigurationSection(key);
                SpideMap m = new SpideMap(key);
                m.setState(MapState.valueOf(ms.getString("state", "MAINTENANCE")));
                if (ms.contains("pos1")) m.setPos1(LocUtil.deserialize(ms.getConfigurationSection("pos1")));
                if (ms.contains("pos2")) m.setPos2(LocUtil.deserialize(ms.getConfigurationSection("pos2")));
                m.setRegionConfirmed(ms.getBoolean("regionConfirmed", false));
                if (ms.contains("lobby")) m.setLobby(LocUtil.deserialize(ms.getConfigurationSection("lobby")));
                m.setPointsToWin(ms.getInt("pointsToWin", 1));
                m.setRadius(ms.getInt("radius", 1));
                m.setPierce(ms.getBoolean("pierce", false));
                m.setMinPlayers(ms.getInt("minPlayers", -1));
                m.setMaxPlayers(ms.getInt("maxPlayers", -1));

                ConfigurationSection teamsSection = ms.getConfigurationSection("teams");
                if (teamsSection != null) {
                    for (String color : teamsSection.getKeys(false)) {
                        ConfigurationSection ts = teamsSection.getConfigurationSection(color);
                        Team team = new Team(color, ts.getInt("required", 1));
                        List<?> spawnList = ts.getList("spawns");
                        if (spawnList != null) {
                            for (Object o : spawnList) {
                                if (o instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> raw = (Map<String, Object>) o;
                                    org.bukkit.World world = Bukkit.getWorld(String.valueOf(raw.get("world")));
                                    if (world != null) {
                                        Location loc = new Location(world,
                                                ((Number) raw.get("x")).doubleValue(),
                                                ((Number) raw.get("y")).doubleValue(),
                                                ((Number) raw.get("z")).doubleValue(),
                                                ((Number) raw.getOrDefault("yaw", 0)).floatValue(),
                                                ((Number) raw.getOrDefault("pitch", 0)).floatValue());
                                        team.addSpawnPoint(loc);
                                    }
                                }
                            }
                        }
                        m.getTeams().add(team);
                    }
                }

                // Remise à zéro systématique de l'état d'exécution au redémarrage du plugin :
                // au boot, aucun joueur n'est réellement en jeu, donc une map ne doit jamais
                // rester bloquée en "occupée" (ou avec un score résiduel) suite à un crash
                // ou un arrêt du serveur en pleine partie.
                m.resetGame();
                m.refreshState();

                // Capture automatiquement un snapshot de régénération si la zone est déjà
                // confirmée (utile après un redémarrage, sans devoir refaire /sp posconfirm).
                if (m.isRegionConfirmed() && m.getPos1() != null && m.getPos2() != null) {
                    try {
                        m.setSnapshot(ArenaSnapshot.capture(m.getPos1(), m.getPos2()));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Impossible de capturer le snapshot de " + key + ": " + e.getMessage());
                    }
                }

                maps.put(key.toLowerCase(), m);
            }
        }
    }

    public void save() {
        if (config == null) config = new YamlConfiguration();

        if (hub != null) {
            config.createSection("hub", LocUtil.serialize(hub));
        }
        config.set("teamColors", colorRegistry.getOrder());

        config.set("maps", null);
        for (SpideMap m : maps.values()) {
            String base = "maps." + m.getName();
            config.set(base + ".state", m.getState().name());
            if (m.getPos1() != null) config.createSection(base + ".pos1", LocUtil.serialize(m.getPos1()));
            if (m.getPos2() != null) config.createSection(base + ".pos2", LocUtil.serialize(m.getPos2()));
            config.set(base + ".regionConfirmed", m.isRegionConfirmed());
            if (m.getLobby() != null) config.createSection(base + ".lobby", LocUtil.serialize(m.getLobby()));
            config.set(base + ".pointsToWin", m.getPointsToWin());
            config.set(base + ".radius", m.getRadius());
            config.set(base + ".pierce", m.isPierce());
            config.set(base + ".minPlayers", m.getMinPlayers());
            config.set(base + ".maxPlayers", m.getMaxPlayers());

            for (Team t : m.getTeams()) {
                String tbase = base + ".teams." + t.getColor();
                config.set(tbase + ".required", t.getRequiredPlayers());
                List<Map<String, Object>> spawns = new ArrayList<>();
                for (Location loc : t.getSpawnPoints()) {
                    spawns.add(LocUtil.serialize(loc));
                }
                config.set(tbase + ".spawns", spawns);
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder maps.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public List<SpideMap> allMapsOrdered() {
        return new ArrayList<>(maps.values());
    }

    public SpideMap getMap(String name) {
        return maps.get(name.toLowerCase());
    }

    public TeamColorRegistry getColorRegistry() {
        return colorRegistry;
    }

    public Location getHub() {
        return hub;
    }

    public void setHub(Location hub) {
        this.hub = hub;
        save();
    }

    // ------------------------------------------------------------------
    // Map configuration commands
    // ------------------------------------------------------------------

    public boolean createMap(String name) {
        if (maps.containsKey(name.toLowerCase())) return false;
        maps.put(name.toLowerCase(), new SpideMap(name));
        save();
        return true;
    }

    /**
     * Supprime entièrement une map (zone, équipes, spawns, lobby, réglages...).
     * Refuse la suppression si une partie est en cours dessus.
     *
     * @return OK si supprimée, NOT_FOUND si aucune map de ce nom n'existe,
     *         OCCUPIED si une partie est actuellement en cours dessus.
     */
    public DeleteResult deleteMap(String name) {
        SpideMap map = getMap(name);
        if (map == null) return DeleteResult.NOT_FOUND;
        if (map.getState() == MapState.OCCUPIED) return DeleteResult.OCCUPIED;

        cancelCountdown(map, null);
        maps.remove(name.toLowerCase());
        playerMap.values().removeIf(v -> v.equalsIgnoreCase(map.getName()));
        spectatorMap.values().removeIf(v -> v.equalsIgnoreCase(map.getName()));
        pendingRoundCheck.remove(map.getName());
        save();
        return DeleteResult.OK;
    }

    public enum DeleteResult {
        OK, NOT_FOUND, OCCUPIED
    }

    public void setupTeams(SpideMap map, int nbTeams, int playersPerTeam) {
        map.getTeams().clear();
        List<String> colors = colorRegistry.firstN(Math.max(nbTeams, 2));
        for (int i = 0; i < nbTeams; i++) {
            String color = i < colors.size() ? colors.get(i) : "COLOR" + (i + 1);
            map.getTeams().add(new Team(color, playersPerTeam));
        }
        map.refreshState();
        save();
    }

    public boolean addSpawn(SpideMap map, String color, Location loc) {
        Team team = map.getTeam(color);
        if (team == null) return false;
        boolean added = team.addSpawnPoint(loc);
        if (added) {
            map.refreshState();
            save();
        }
        return added;
    }

    /** @return true si min &lt;= max et min &gt;= 2, false sinon (valeurs non appliquées). */
    public boolean setPlayerLimits(SpideMap map, int min, int max) {
        if (min < 2 || max < min) return false;
        map.setMinPlayers(min);
        map.setMaxPlayers(max);
        save();
        return true;
    }

    /**
     * Capture un snapshot de la zone actuelle, utilisé pour régénérer l'arène à chaque
     * manche. À appeler une fois la zone construite, typiquement lors de /sp posconfirm.
     */
    public boolean captureSnapshot(SpideMap map) {
        if (map.getPos1() == null || map.getPos2() == null) return false;
        map.setSnapshot(ArenaSnapshot.capture(map.getPos1(), map.getPos2()));
        return true;
    }

    private void regenerateArena(SpideMap map) {
        if (map.getSnapshot() != null) {
            map.getSnapshot().restore();
        }
    }

    /**
     * Force une remise à zéro complète d'une map en cours de blocage (bug, crash de partie...) :
     * tout le monde est renvoyé au hub, les compteurs sont remis à zéro et l'état est recalculé.
     */
    public void forceReset(SpideMap map) {
        cancelCountdown(map, null);
        for (Team t : map.getTeams()) {
            for (UUID uuid : new ArrayList<>(t.getMembers())) {
                playerMap.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && hub != null) {
                    p.teleport(hub);
                    p.setGameMode(GameMode.ADVENTURE);
                }
            }
        }
        for (UUID uuid : new ArrayList<>(map.getSpectators())) {
            spectatorMap.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && hub != null) {
                p.teleport(hub);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
        scoreboardManager.remove(map);
        pendingRoundCheck.remove(map.getName());
        map.resetGame();
        regenerateArena(map);
        map.refreshState();
        save();
    }

    /**
     * Fait quitter le joueur de la map où il se trouve (en tant que joueur actif ou
     * spectateur), où qu'il en soit dans la partie, et le renvoie au hub.
     *
     * @return la map quittée, ou null si le joueur n'était dans aucune partie.
     */
    public SpideMap leaveGame(Player player) {
        UUID uuid = player.getUniqueId();

        SpideMap map = getMapOfPlayer(uuid);
        if (map != null) {
            playerMap.remove(uuid);
            for (Team t : map.getTeams()) {
                t.eliminate(uuid);
                t.getMembers().remove(uuid);
            }
            teleportToHub(player);
            resetPlayerScoreboard(player);
            if (map.getState() == MapState.OCCUPIED) {
                scheduleRoundCheck(map);
            } else {
                cancelCountdownIfBelowMin(map);
            }
            return map;
        }

        SpideMap specMap = getMapOfSpectator(uuid);
        if (specMap != null) {
            spectatorMap.remove(uuid);
            specMap.getSpectators().remove(uuid);
            teleportToHub(player);
            resetPlayerScoreboard(player);
            return specMap;
        }

        return null;
    }

    private void teleportToHub(Player player) {
        if (hub != null) {
            player.teleport(hub);
        }
        player.setGameMode(GameMode.ADVENTURE);
    }

    private void resetPlayerScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void cancelCountdownIfBelowMin(SpideMap map) {
        if (countdowns.containsKey(map.getName()) && map.getTotalCurrentPlayers() < map.getEffectiveMinPlayers()) {
            cancelCountdown(map, "§cPas assez de joueurs, le décompte de départ est annulé.");
        }
    }

    // ------------------------------------------------------------------
    // Joining / spectating from the GUI or /sp join
    // ------------------------------------------------------------------

    public boolean joinAsPlayer(Player player, SpideMap map) {
        if (map.getState() != MapState.AVAILABLE) return false;
        Team target = null;
        for (Team t : map.getTeams()) {
            if (!t.isFull()) {
                target = t;
                break;
            }
        }
        if (target == null) return false;

        target.addMember(player.getUniqueId());
        playerMap.put(player.getUniqueId(), map.getName());
        player.teleport(map.getLobby());
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();

        int total = map.getTotalCurrentPlayers();
        if (total >= map.getEffectiveMaxPlayers() || map.isLobbyFull()) {
            cancelCountdown(map, null);
            startGame(map);
        } else if (total >= map.getEffectiveMinPlayers()) {
            startCountdown(map);
        }
        return true;
    }

    public boolean joinAsSpectator(Player player, SpideMap map) {
        if (map.getState() != MapState.OCCUPIED) return false;
        map.getSpectators().add(player.getUniqueId());
        spectatorMap.put(player.getUniqueId(), map.getName());
        player.teleport(map.getLobby());
        player.setGameMode(GameMode.SPECTATOR);
        scoreboardManager.assignPlayer(map, player);
        return true;
    }

    // ------------------------------------------------------------------
    // Countdown de lancement (10s dès que le minimum de joueurs est atteint)
    // ------------------------------------------------------------------

    private void startCountdown(SpideMap map) {
        if (countdowns.containsKey(map.getName())) return;

        countdowns.put(map.getName(), Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = 10;

            @Override
            public void run() {
                if (map.getTotalCurrentPlayers() < map.getEffectiveMinPlayers()) {
                    cancelCountdown(map, "§cPas assez de joueurs, le décompte de départ est annulé.");
                    return;
                }
                if (secondsLeft <= 0) {
                    BukkitTask task = countdowns.remove(map.getName());
                    if (task != null) task.cancel();
                    startGame(map);
                    return;
                }
                broadcastToLobby(map, "§eLa partie sur §f" + map.getName() + " §edémarre dans §f" + secondsLeft + "s§e...");
                secondsLeft--;
            }
        }, 0L, 20L));
    }

    private void cancelCountdown(SpideMap map, String message) {
        BukkitTask task = countdowns.remove(map.getName());
        if (task != null) {
            task.cancel();
            if (message != null) broadcastToLobby(map, message);
        }
    }

    private void broadcastToLobby(SpideMap map, String message) {
        for (Team t : map.getTeams()) {
            for (UUID uuid : t.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(message);
            }
        }
    }

    // ------------------------------------------------------------------
    // Game lifecycle
    // ------------------------------------------------------------------

    public void startGame(SpideMap map) {
        if (map.getState() == MapState.OCCUPIED) return; // déjà démarrée (sécurité anti double-appel)
        map.setState(MapState.OCCUPIED);
        regenerateArena(map);
        for (Team t : map.getTeams()) {
            t.resetRound();
        }
        teleportAllToSpawns(map);
        scoreboardManager.createAndAssign(map);
        save();
    }

    private void teleportAllToSpawns(SpideMap map) {
        for (Team t : map.getTeams()) {
            List<Location> spawns = t.getSpawnPoints();
            List<UUID> members = t.getMembers();
            for (int i = 0; i < members.size(); i++) {
                Player p = Bukkit.getPlayer(members.get(i));
                if (p == null) continue;
                Location spawn = spawns.get(i % spawns.size());
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
                giveLoadout(p);
            }
        }
    }

    private void giveLoadout(Player p) {
        p.getInventory().clear();

        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        if (bowMeta != null) {
            // Enchantement Infinité récupéré via sa clé vanilla (plus stable qu'une constante
            // statique, celles-ci ayant été renommées/dépréciées entre versions d'API).
            Enchantment infinity = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("infinity"));
            if (infinity != null) {
                bowMeta.addEnchant(infinity, 1, true);
            }
            bowMeta.setUnbreakable(true);
            bowMeta.getPersistentDataContainer().set(ItemTags.BOW, PersistentDataType.BYTE, (byte) 1);
            bow.setItemMeta(bowMeta);
        }
        p.getInventory().addItem(bow);

        // Une flèche physique reste nécessaire pour pouvoir bander l'arc ; avec Infinité
        // + EntityShootBowEvent#setConsumeItem(false) (voir ArrowListener) elle n'est
        // jamais retirée de l'inventaire. Elle est elle aussi taguée pour ne pas pouvoir
        // être déplacée / jetée.
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        if (arrowMeta != null) {
            arrowMeta.getPersistentDataContainer().set(ItemTags.ARROW, PersistentDataType.BYTE, (byte) 1);
            arrow.setItemMeta(arrowMeta);
        }
        p.getInventory().addItem(arrow);

        p.setHealth(p.getMaxHealth());
        p.setFireTicks(0);
    }

    /** Un joueur "tombe" (élimination) : bascule en spectateur et vérifie la fin de manche. */
    public void eliminatePlayer(Player player, SpideMap map) {
        if (map.getState() != MapState.OCCUPIED) return;

        boolean wasAlive = false;
        for (Team t : map.getTeams()) {
            if (t.getAlive().contains(player.getUniqueId())) {
                t.eliminate(player.getUniqueId());
                wasAlive = true;
            }
        }
        // Le joueur était déjà éliminé (double event, void déclenché plusieurs fois...) :
        // on ne refait ni l'annonce ni le calcul de fin de manche pour éviter les points en double.
        if (!wasAlive) return;

        player.setGameMode(GameMode.SPECTATOR);
        Bukkit.broadcast(Component.text("§7" + player.getName() + " §fa été éliminé sur §7" + map.getName()));
        scheduleRoundCheck(map);
    }

    /**
     * Reporte la résolution de fin de manche de quelques ticks et fusionne les appels :
     * si plusieurs joueurs sont éliminés le même tick (ou presque, ex: double élimination
     * simultanée), un seul calcul de fin de manche est effectué avec l'état final,
     * ce qui évite d'attribuer un point en trop ou de laisser la partie bloquée.
     */
    private void scheduleRoundCheck(SpideMap map) {
        if (!pendingRoundCheck.add(map.getName())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRoundCheck.remove(map.getName());
            checkRoundEnd(map);
        }, 2L);
    }

    private void checkRoundEnd(SpideMap map) {
        if (map.getState() != MapState.OCCUPIED) return;

        List<Team> alive = new ArrayList<>();
        for (Team t : map.getTeams()) {
            if (!t.getAlive().isEmpty()) alive.add(t);
        }
        if (alive.size() > 1) return; // la manche continue

        if (alive.size() == 1) {
            Team winner = alive.get(0);
            winner.addPoint();
            scoreboardManager.refresh(map);
            if (winner.getScore() >= map.getPointsToWin()) {
                endGame(map, winner);
            } else {
                reviveAll(map);
            }
        }
        // si alive.size() == 0 (double élimination simultanée), on relance simplement la manche
        // sans attribuer de point à personne.
        else {
            reviveAll(map);
        }
    }

    /** Nouvelle manche : l'arène est régénérée avant que tout le monde revive à son spawn. */
    private void reviveAll(SpideMap map) {
        regenerateArena(map);
        for (Team t : map.getTeams()) {
            t.resetRound();
        }
        teleportAllToSpawns(map);
        scoreboardManager.refresh(map);
    }

    private void endGame(SpideMap map, Team winner) {
        for (Team t : map.getTeams()) {
            boolean won = t == winner;
            for (UUID uuid : t.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.setGameMode(GameMode.SPECTATOR);
                Title title = Title.title(
                        won ? Component.text("§a§lVICTOIRE") : Component.text("§c§lDÉFAITE"),
                        Component.text("§7Équipe " + winner.getColor()),
                        Title.Times.of(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
                );
                p.showTitle(title);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendEveryoneToHub(map);
            scoreboardManager.remove(map);
            map.resetGame();
            regenerateArena(map);
            map.setState(MapState.AVAILABLE);
            map.refreshState();
            save();
        }, 20L * 10);
    }

    /**
     * Renvoie tout le monde (joueurs de la partie + spectateurs) au hub. Le retrait du
     * suivi (playerMap/spectatorMap) est fait AVANT la téléportation, pour que le listener
     * de confinement de zone ne réintercepte pas ce déplacement et n'annule pas le
     * retour au hub.
     */
    private void sendEveryoneToHub(SpideMap map) {
        for (Team t : map.getTeams()) {
            for (UUID uuid : new ArrayList<>(t.getMembers())) {
                playerMap.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    if (hub != null) {
                        p.teleport(hub);
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                }
            }
        }
        for (UUID uuid : new ArrayList<>(map.getSpectators())) {
            spectatorMap.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (hub != null) {
                    p.teleport(hub);
                    p.setGameMode(GameMode.ADVENTURE);
                }
            }
        }
        map.getSpectators().clear();
    }

    // ------------------------------------------------------------------
    // Lookup helpers used by listeners
    // ------------------------------------------------------------------

    public SpideMap getMapOfPlayer(UUID uuid) {
        String name = playerMap.get(uuid);
        return name == null ? null : getMap(name);
    }

    public SpideMap getMapOfSpectator(UUID uuid) {
        String name = spectatorMap.get(uuid);
        return name == null ? null : getMap(name);
    }

    public boolean isInAnyGame(UUID uuid) {
        return playerMap.containsKey(uuid) || spectatorMap.containsKey(uuid);
    }
}
