package com.hikabrain.plugin.stats;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enregistre, pour chaque paire de joueurs, le nombre de fois où l'un a battu l'autre
 * (au sens "était dans l'équipe gagnante d'une partie où l'adversaire était dans
 * l'équipe perdante"). C'est la matière première du classement de force (voir
 * {@link PowerRankingCalculator}) : sans savoir QUI a battu QUI, impossible de calculer
 * une force "transitive" (battre un joueur fort vaut plus que battre un joueur faible).
 *
 * Les données sont sauvegardées dans head-to-head.yml. Pour chaque joueur, on stocke sa
 * propre liste d'adversaires rencontrés avec, pour chacun, le nombre de victoires et de
 * défaites face à lui précisément (pas de moyenne globale).
 */
public class HeadToHeadManager {

    /** Bilan face à un adversaire précis. */
    public static class Record {
        public int wins;
        public int losses;

        public int totalGames() { return wins + losses; }
    }

    public static class PlayerHeadToHead {
        public String name;
        public final Map<UUID, Record> opponents = new HashMap<>();
    }

    private final HikaBrainPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, PlayerHeadToHead> data = new HashMap<>();

    public HeadToHeadManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "head-to-head.yml");
        load();
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer head-to-head.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        data.clear();
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                PlayerHeadToHead phh = new PlayerHeadToHead();
                phh.name = playerSection.getString("name", "?");

                ConfigurationSection opponentsSection = playerSection.getConfigurationSection("opponents");
                if (opponentsSection != null) {
                    for (String oppUuidStr : opponentsSection.getKeys(false)) {
                        try {
                            UUID oppUuid = UUID.fromString(oppUuidStr);
                            ConfigurationSection oppSection = opponentsSection.getConfigurationSection(oppUuidStr);
                            if (oppSection == null) continue;
                            Record record = new Record();
                            record.wins = oppSection.getInt("wins", 0);
                            record.losses = oppSection.getInt("losses", 0);
                            phh.opponents.put(oppUuid, record);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                data.put(uuid, phh);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        config.set("players", null);
        for (Map.Entry<UUID, PlayerHeadToHead> entry : data.entrySet()) {
            String base = "players." + entry.getKey();
            PlayerHeadToHead phh = entry.getValue();
            config.set(base + ".name", phh.name);
            for (Map.Entry<UUID, Record> oppEntry : phh.opponents.entrySet()) {
                String oppBase = base + ".opponents." + oppEntry.getKey();
                config.set(oppBase + ".wins", oppEntry.getValue().wins);
                config.set(oppBase + ".losses", oppEntry.getValue().losses);
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder head-to-head.yml: " + e.getMessage());
        }
    }

    private PlayerHeadToHead getOrCreate(UUID uuid, String name) {
        PlayerHeadToHead phh = data.get(uuid);
        if (phh == null) {
            phh = new PlayerHeadToHead();
            phh.name = name;
            data.put(uuid, phh);
        } else if (name != null) {
            phh.name = name;
        }
        return phh;
    }

    // ── Enregistrement ────────────────────────────────────────────────────────

    /**
     * Enregistre un résultat entre deux joueurs adverses (winner a battu loser).
     * Met à jour les DEUX côtés de la relation (le bilan de winner face à loser,
     * ET le bilan de loser face à winner), pour que le score de force puisse être
     * calculé aussi bien "dans le sens des victoires" que "dans le sens des défaites"
     * (voir {@link PowerRankingCalculator}).
     */
    public void recordResult(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName) {
        if (winnerUuid.equals(loserUuid)) return;

        PlayerHeadToHead winnerData = getOrCreate(winnerUuid, winnerName);
        winnerData.opponents.computeIfAbsent(loserUuid, k -> new Record()).wins++;

        PlayerHeadToHead loserData = getOrCreate(loserUuid, loserName);
        loserData.opponents.computeIfAbsent(winnerUuid, k -> new Record()).losses++;

        save();
    }

    // ── Accesseurs ─────────────────────────────────────────────────────────────

    public Map<UUID, PlayerHeadToHead> getAll() {
        return java.util.Collections.unmodifiableMap(data);
    }

    public PlayerHeadToHead getPlayerData(UUID uuid) {
        return data.get(uuid);
    }

    public String getName(UUID uuid, String fallback) {
        PlayerHeadToHead phh = data.get(uuid);
        return phh != null ? phh.name : fallback;
    }

    /** Tous les joueurs ayant au moins une confrontation enregistrée. */
    public Set<UUID> getKnownPlayers() {
        return new HashSet<>(data.keySet());
    }

    public void resetAll() {
        data.clear();
        save();
    }
}
