package com.hikabrain.plugin.music;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Joue de la musique d'ambiance (via les sons de bloc de notes vanilla, aucun plugin
 * externe requis) pendant les parties HikaBrain : une piste par défaut configurable
 * globalement, ou une piste différente par arène.
 *
 * Les fichiers .nbs doivent être placés dans plugins/HikaBrain/music/. Voir
 * {@link NbsParser} pour le détail du format lu, et {@link #INSTRUMENT_SOUNDS} pour la
 * correspondance entre les 10 instruments vanilla du format NBS et les sons Minecraft.
 *
 * IMPORTANT (droit d'auteur) : convertir une musique en blocs de notes ne supprime pas
 * ses droits d'auteur. Privilégier des morceaux du domaine public (musique classique)
 * ou explicitement libres de droits (Incompetech, OpenGameArt.org, Pixabay Music...),
 * convertis soi-même via Open Note Block Studio (https://opennbs.org).
 */
public class MusicManager {

    /** Correspondance instrument NBS (0-9, "vanilla") → son de bloc de notes Minecraft. */
    private static final Map<Integer, Sound> INSTRUMENT_SOUNDS = Map.of(
            0, Sound.BLOCK_NOTE_BLOCK_HARP,
            1, Sound.BLOCK_NOTE_BLOCK_BASS,
            2, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
            3, Sound.BLOCK_NOTE_BLOCK_SNARE,
            4, Sound.BLOCK_NOTE_BLOCK_HAT,
            5, Sound.BLOCK_NOTE_BLOCK_GUITAR,
            6, Sound.BLOCK_NOTE_BLOCK_FLUTE,
            7, Sound.BLOCK_NOTE_BLOCK_BELL,
            8, Sound.BLOCK_NOTE_BLOCK_CHIME,
            9, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE
    );

    private final HikaBrainPlugin plugin;
    private final File musicDir;
    private final File overridesFile;
    private final Random random = new Random();

    private final Map<String, NbsSong> songCache = new HashMap<>();
    private final Map<String, MusicSession> activeSessions = new HashMap<>(); // nom d'arène -> session
    private final Map<String, String> arenaTrackOverride = new HashMap<>();   // nom d'arène -> fichier/"off"/"random"

    public MusicManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.musicDir = new File(plugin.getDataFolder(), "music");
        this.overridesFile = new File(plugin.getDataFolder(), "music-arenas.yml");
        musicDir.mkdirs();
        loadOverrides();
    }

    // ── Persistance des choix par arène ─────────────────────────────────────────

    private void loadOverrides() {
        if (!overridesFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(overridesFile);
        for (String arenaName : config.getKeys(false)) {
            arenaTrackOverride.put(arenaName, config.getString(arenaName));
        }
    }

    private void saveOverrides() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, String> entry : arenaTrackOverride.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
        try {
            config.save(overridesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder music-arenas.yml : " + e.getMessage());
        }
    }

    /**
     * Fixe la piste utilisée pour une arène précise ("nomfichier.nbs", "random" ou "off"),
     * ou retire le réglage spécifique à l'arène (retombe alors sur le réglage global) si
     * trackChoice vaut null.
     */
    public void setArenaTrack(String arenaName, String trackChoice) {
        if (trackChoice == null) {
            arenaTrackOverride.remove(arenaName);
        } else {
            arenaTrackOverride.put(arenaName, trackChoice);
        }
        saveOverrides();
    }

    public String getArenaTrackChoice(String arenaName) {
        return arenaTrackOverride.getOrDefault(arenaName, plugin.getConfig().getString("music.default-track", "random"));
    }

    /** Liste les fichiers .nbs disponibles dans plugins/HikaBrain/music/. */
    public List<String> listAvailableTracks() {
        File[] files = musicDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".nbs"));
        if (files == null) return List.of();
        return java.util.Arrays.stream(files).map(File::getName).sorted().collect(Collectors.toList());
    }

    // ── Chargement (avec cache) ─────────────────────────────────────────────────

    private NbsSong loadSong(String fileName) {
        NbsSong cached = songCache.get(fileName);
        if (cached != null) return cached;

        File file = new File(musicDir, fileName);
        if (!file.exists()) return null;

        try {
            NbsSong song = NbsParser.parse(file);
            songCache.put(fileName, song);
            return song;
        } catch (IOException e) {
            plugin.getLogger().warning("[HikaBrain] Impossible de lire le fichier de musique '" + fileName + "' : " + e.getMessage());
            return null;
        }
    }

    private String pickRandomTrack() {
        List<String> tracks = listAvailableTracks();
        if (tracks.isEmpty()) return null;
        return tracks.get(random.nextInt(tracks.size()));
    }

    // ── Lecture par arène ────────────────────────────────────────────────────────

    /**
     * Démarre la musique pour cette arène (si activée en config), selon la piste choisie
     * pour cette arène ou, à défaut, la piste par défaut globale. Appelé à chaque fois
     * que la phase de jeu active (re)commence — début de partie ET reprise après chaque
     * temps d'attente (voir GameManager#startRoundReset).
     */
    public void startMusicForArena(GameManager gm) {
        if (!plugin.getConfig().getBoolean("music.enabled", false)) return;

        String arenaName = gm.getName();
        stopMusicForArena(gm); // sécurité : jamais deux sessions en même temps pour la même arène

        String choice = getArenaTrackChoice(arenaName);
        if (choice == null || choice.equalsIgnoreCase("off")) return;

        String fileName = choice.equalsIgnoreCase("random") ? pickRandomTrack() : choice;
        if (fileName == null) return; // aucune piste disponible

        NbsSong song = loadSong(fileName);
        if (song == null || song.getNotes().isEmpty()) return;

        MusicSession session = new MusicSession(gm, song);
        activeSessions.put(arenaName, session);
        session.start();
    }

    /**
     * Arrête la musique de cette arène (silence pendant le temps d'attente/chat rapide,
     * et à la fin de la partie).
     */
    public void stopMusicForArena(GameManager gm) {
        MusicSession session = activeSessions.remove(gm.getName());
        if (session != null) session.stop();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Une "session" de lecture : programme toutes les notes d'une chanson pour les
     * joueurs d'une arène, et reboucle automatiquement si configuré (music.loop).
     * Toutes les tâches programmées sont annulables via {@link #stop()}, pour ne jamais
     * laisser de notes sonner après la fin du temps de jeu actif.
     */
    private class MusicSession {
        private final GameManager gm;
        private final NbsSong song;
        private final List<BukkitTask> noteTasks = new ArrayList<>();
        private BukkitTask loopTask;

        MusicSession(GameManager gm, NbsSong song) {
            this.gm = gm;
            this.song = song;
        }

        void start() {
            scheduleAllNotes();
            if (plugin.getConfig().getBoolean("music.loop", true)) {
                long lengthTicks = Math.max(20L, song.getLengthInMinecraftTicks());
                loopTask = Bukkit.getScheduler().runTaskLater(plugin, this::loopRestart, lengthTicks + 20L);
            }
        }

        private void loopRestart() {
            scheduleAllNotes();
            long lengthTicks = Math.max(20L, song.getLengthInMinecraftTicks());
            loopTask = Bukkit.getScheduler().runTaskLater(plugin, this::loopRestart, lengthTicks + 20L);
        }

        private void scheduleAllNotes() {
            double volumeConfig = plugin.getConfig().getDouble("music.volume", 1.0);
            double tempo = song.getTempo();

            for (NbsSong.NbsNote note : song.getNotes()) {
                Sound sound = INSTRUMENT_SOUNDS.get(note.instrument);
                if (sound == null) continue; // instrument personnalisé (résource pack) : pas supporté, on l'ignore

                long delay = Math.round(note.tick * (20.0 / tempo));

                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> playNoteToArena(note, sound, volumeConfig), delay);
                noteTasks.add(task);
            }
        }

        private void playNoteToArena(NbsSong.NbsNote note, Sound sound, double volumeConfig) {
            int clicks = clamp(note.key - 33, 0, 24); // touche NBS -> clic de bloc de notes vanilla (0-24)
            double semitoneOffset = (clicks - 12) + (note.pitchFine / 100.0);
            float pitch = (float) Math.pow(2.0, semitoneOffset / 12.0);
            float volume = (float) Math.max(0.0, Math.min(1.0, (note.velocity / 100.0) * volumeConfig));

            for (UUID uuid : gm.getPlayerTeams().keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
                }
            }
        }

        void stop() {
            for (BukkitTask task : noteTasks) task.cancel();
            noteTasks.clear();
            if (loopTask != null) {
                loopTask.cancel();
                loopTask = null;
            }
        }
    }
}
