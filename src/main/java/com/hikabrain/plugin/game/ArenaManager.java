package com.hikabrain.plugin.game;

import com.hikabrain.plugin.HikaBrainPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Registre central de toutes les arènes HikaBrain configurées sur le serveur.
 * Permet de créer, lister, supprimer et récupérer des arènes nommées, chacune
 * pilotée par sa propre instance de GameManager — ce qui permet plusieurs parties
 * HikaBrain indépendantes et simultanées dans un même monde (arena1, arena2, ...).
 *
 * La liste des noms d'arènes existantes est elle-même persistée dans un petit fichier
 * dédié (arenas/arenas.yml) afin de savoir, au redémarrage du plugin, quelles arènes
 * recharger depuis leurs fichiers individuels.
 */
public class ArenaManager {

    private final HikaBrainPlugin plugin;
    private final Map<String, GameManager> arenas = new LinkedHashMap<>();
    private final File registryFile;

    public ArenaManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        File arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
        this.registryFile = new File(arenasDir, "arenas.yml");
    }

    /**
     * Charge toutes les arènes connues depuis le disque (appelé au démarrage du plugin).
     */
    public void loadAll() {
        YamlConfiguration registry = YamlConfiguration.loadConfiguration(registryFile);
        List<String> names = registry.getStringList("arenas");
        for (String name : names) {
            createOrLoad(name);
        }
    }

    private void saveRegistry() {
        YamlConfiguration registry = new YamlConfiguration();
        registry.set("arenas", new ArrayList<>(arenas.keySet()));
        try {
            registry.save(registryFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder la liste des arènes : " + e.getMessage());
        }
    }

    /**
     * Crée une nouvelle arène vide avec le nom donné. Renvoie false si une arène
     * avec ce nom existe déjà.
     */
    public boolean create(String name) {
        String normalized = normalize(name);
        if (arenas.containsKey(normalized)) {
            return false;
        }
        createOrLoad(normalized);
        saveRegistry();
        return true;
    }

    private void createOrLoad(String name) {
        GameManager gm = new GameManager(plugin, name);
        gm.loadArenaConfig();
        gm.loadGameZoneSnapshot();
        arenas.put(name, gm);
    }

    /**
     * Supprime une arène de la mémoire et du registre (elle ne sera plus rechargée au
     * redémarrage). Si une partie est en cours sur cette arène, elle est arrêtée au préalable.
     *
     * Note : les fichiers .yml/.snapshot de cette arène restent sur le disque (dans le dossier
     * "arenas/"), au cas où l'admin voudrait les récupérer ; ils ne sont pas supprimés automatiquement.
     */
    public boolean delete(String name) {
        String normalized = normalize(name);
        GameManager gm = arenas.remove(normalized);
        if (gm == null) {
            return false;
        }
        gm.forceStop();
        saveRegistry();
        return true;
    }

    public GameManager get(String name) {
        return arenas.get(normalize(name));
    }

    public boolean exists(String name) {
        return arenas.containsKey(normalize(name));
    }

    public Collection<GameManager> getAll() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    /**
     * Alias pour getAll() pour compatibilité.
     */
    public Collection<GameManager> getAllGameManagers() {
        return getAll();
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(arenas.keySet());
    }

    /**
     * Sauvegarde la configuration de toutes les arènes (appelé à l'arrêt du plugin).
     */
    public void saveAll() {
        for (GameManager gm : arenas.values()) {
            gm.saveArenaConfig();
        }
    }

    /**
     * Arrête toutes les parties en cours sur toutes les arènes (appelé à l'arrêt du plugin).
     */
    public void stopAll() {
        for (GameManager gm : arenas.values()) {
            gm.forceStop();
        }
    }

    /**
     * Trouve l'arène (s'il y en a une) dans laquelle le joueur donné est actuellement engagé.
     */
    public GameManager findArenaOf(org.bukkit.entity.Player player) {
        for (GameManager gm : arenas.values()) {
            if (gm.isPlaying(player)) {
                return gm;
            }
        }
        return null;
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
