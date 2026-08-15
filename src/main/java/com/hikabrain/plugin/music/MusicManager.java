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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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
 * La musique est mise en PAUSE (pas arrêtée) pendant chaque temps d'attente après un
 * point marqué, et REPREND exactement là où elle s'était interrompue quand le round
 * redémarre — elle ne recommence du début qu'au tout début d'une nouvelle partie (voir
 * {@link #startNewMatchMusic}, {@link #pauseMusicForArena}, {@link #resumeMusicForArena}).
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

    /**
     * File d'attente "mélangée" par arène pour le mode "random" : au lieu d'un tirage
     * totalement indépendant à chaque partie (qui peut retomber sur la même piste deux
     * fois de suite), chaque arène pioche dans un ordre aléatoire de TOUTES les pistes
     * disponibles, sans jamais répéter tant que le cycle n'est pas épuisé. Une fois
     * toutes les pistes passées, un nouveau mélange est tiré (en évitant que sa première
     * piste soit la même que la toute dernière du cycle précédent, pour ne jamais avoir
     * deux fois la même piste d'affilée même à la jonction de deux cycles).
     */
    private final Map<String, Deque<String>> shuffleQueues = new HashMap<>();
    private final Map<String, String> lastPlayedTrack = new HashMap<>();

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

    /**
     * Pioche la prochaine piste "aléatoire" pour cette arène, sans jamais répéter la
     * piste juste précédente (voir {@link #shuffleQueues}). Si toutes les pistes du
     * cycle en cours ont déjà été jouées, un nouveau cycle mélangé est démarré.
     */
    private String pickRandomTrack(String arenaName) {
        List<String> tracks = listAvailableTracks();
        if (tracks.isEmpty()) return null;
        if (tracks.size() == 1) return tracks.get(0); // une seule piste : rien d'autre à faire tourner

        Deque<String> queue = shuffleQueues.get(arenaName);
        if (queue == null || queue.isEmpty()) {
            queue = buildShuffledQueue(tracks, lastPlayedTrack.get(arenaName));
            shuffleQueues.put(arenaName, queue);
        }

        String next = queue.poll();
        lastPlayedTrack.put(arenaName, next);
        return next;
    }

    /**
     * Construit un nouvel ordre aléatoire de toutes les pistes disponibles. Si la
     * première piste tirée est la même que celle qui vient de terminer le cycle
     * précédent (avoidRepeat), on l'échange avec une autre pour ne jamais avoir deux
     * fois la même piste d'affilée, même à la jonction entre deux cycles.
     */
    private Deque<String> buildShuffledQueue(List<String> tracks, String avoidRepeat) {
        List<String> shuffled = new ArrayList<>(tracks);
        Collections.shuffle(shuffled, random);

        if (avoidRepeat != null && shuffled.size() > 1 && shuffled.get(0).equals(avoidRepeat)) {
            int swapWith = 1 + random.nextInt(shuffled.size() - 1);
            Collections.swap(shuffled, 0, swapWith);
        }

        return new ArrayDeque<>(shuffled);
    }

    // ── Lecture par arène ────────────────────────────────────────────────────────

    /**
     * Démarre une TOUTE NOUVELLE musique pour cette arène, depuis le début (choisit une
     * piste — au hasard si configuré ainsi — et repart de zéro). À appeler UNIQUEMENT au
     * tout début d'une partie (voir GameManager#beginPlayPhase), jamais à la reprise
     * après un temps d'attente : sinon un réglage "random" changerait de chanson à
     * chaque point marqué, et "reprendre où on s'est arrêté" n'aurait plus de sens.
     */
    public void startNewMatchMusic(GameManager gm) {
        if (!plugin.getConfig().getBoolean("music.enabled", false)) return;

        String arenaName = gm.getName();
        stopMusicForArena(gm); // sécurité : jamais deux sessions en même temps pour la même arène

        String choice = getArenaTrackChoice(arenaName);
        if (choice == null || choice.equalsIgnoreCase("off")) return;

        String fileName = choice.equalsIgnoreCase("random") ? pickRandomTrack(arenaName) : choice;
        if (fileName == null) return; // aucune piste disponible

        NbsSong song = loadSong(fileName);
        if (song == null || song.getNotes().isEmpty()) return;

        MusicSession session = new MusicSession(gm, song);
        activeSessions.put(arenaName, session);
        session.playFromStart();
    }

    /**
     * Met la musique de cette arène EN PAUSE (silence pendant le temps d'attente après un
     * point marqué), en retenant sa position exacte pour la reprise — voir
     * {@link #resumeMusicForArena}. Contrairement à {@link #stopMusicForArena}, la
     * session n'est PAS oubliée.
     */
    public void pauseMusicForArena(GameManager gm) {
        MusicSession session = activeSessions.get(gm.getName());
        if (session != null) session.pause();
    }

    /**
     * Reprend la musique de cette arène exactement là où elle avait été mise en pause
     * (voir {@link #pauseMusicForArena}). Ne fait rien s'il n'y a pas de session en
     * cours pour cette arène (musique désactivée, ou partie qui vient de se terminer).
     */
    public void resumeMusicForArena(GameManager gm) {
        MusicSession session = activeSessions.get(gm.getName());
        if (session != null) session.resume();
    }

    /**
     * Arrête complètement la musique de cette arène et oublie sa position (fin de
     * partie, ou filet de sécurité en cas d'arrêt brutal) : la prochaine partie
     * repartira forcément d'une chanson toute neuve, pas d'une reprise.
     */
    public void stopMusicForArena(GameManager gm) {
        MusicSession session = activeSessions.remove(gm.getName());
        if (session != null) session.stop();
    }

    /**
     * Une "session" de lecture pour une arène : programme les notes d'une chanson à
     * partir d'une position donnée (en ticks Minecraft écoulés depuis le début de la
     * chanson), et sait se mettre en pause en retenant cette position pour la reprendre
     * plus tard au même endroit. Reboucle automatiquement à la fin si configuré.
     */
    private class MusicSession {
        private final GameManager gm;
        private final NbsSong song;

        /** Position de lecture en ticks Minecraft depuis le début de la chanson (0 = tout début). */
        private long positionTicks = 0;
        /** Horodatage (ms) du début du segment de lecture EN COURS, pour calculer la position à la pause. */
        private long segmentStartMillis;
        private boolean playing = false;

        private final List<BukkitTask> noteTasks = new ArrayList<>();
        private BukkitTask endOfSongTask;

        MusicSession(GameManager gm, NbsSong song) {
            this.gm = gm;
            this.song = song;
        }

        void playFromStart() {
            positionTicks = 0;
            resume();
        }

        /** Reprend la lecture depuis {@link #positionTicks} (là où on s'était arrêté). */
        void resume() {
            if (playing) return;
            playing = true;
            segmentStartMillis = System.currentTimeMillis();
            scheduleNotesFrom(positionTicks);
        }

        /** Met en pause : annule les notes programmées, mémorise la position atteinte. */
        void pause() {
            if (!playing) return;
            long elapsedSinceResume = Math.round((System.currentTimeMillis() - segmentStartMillis) / 50.0); // 1 tick = 50ms
            positionTicks += Math.max(0, elapsedSinceResume);
            cancelTasks();
            playing = false;
        }

        /** Arrêt définitif (fin de partie) : comme pause(), mais la position n'a plus d'importance. */
        void stop() {
            cancelTasks();
            playing = false;
        }

        private void scheduleNotesFrom(long startTick) {
            double volumeConfig = plugin.getConfig().getDouble("music.volume", 1.0);
            double tempo = song.getTempo();

            for (NbsSong.NbsNote note : song.getNotes()) {
                long noteMcTick = Math.round(note.tick * (20.0 / tempo));
                if (noteMcTick < startTick) continue; // déjà joué avant la pause, on ne le rejoue pas

                // Instrument NBS "vanilla" (0-9) → son correspondant ; au-delà (instrument
                // personnalisé, très courant dans les morceaux composés avec des packs
                // d'instruments étendus), on retombe sur le Harp plutôt que d'ignorer la
                // note entièrement — jouer la bonne hauteur avec le mauvais timbre reste
                // bien plus fidèle que de la faire disparaître complètement.
                Sound sound = INSTRUMENT_SOUNDS.getOrDefault(note.instrument, Sound.BLOCK_NOTE_BLOCK_HARP);

                long delay = noteMcTick - startTick;
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> playNoteToArena(note, sound, volumeConfig), delay);
                noteTasks.add(task);
            }

            long totalLengthTicks = Math.max(20L, song.getLengthInMinecraftTicks());
            long remaining = Math.max(1L, totalLengthTicks - startTick);
            endOfSongTask = Bukkit.getScheduler().runTaskLater(plugin, this::onSongFinished, remaining);
        }

        private void onSongFinished() {
            if (plugin.getConfig().getBoolean("music.loop", true)) {
                playing = false; // pour permettre à resume() de reprogrammer proprement
                positionTicks = 0;
                resume();
            } else {
                playing = false;
            }
        }

        private void playNoteToArena(NbsSong.NbsNote note, Sound sound, double volumeConfig) {
            // Minecraft limite VRAIMENT le pitch d'un son à [0.5 ; 2.0] (2 octaves), quelle
            // que soit la méthode utilisée pour le jouer — c'est une limite du moteur audio
            // lui-même, impossible à contourner. Une note dont la hauteur NBS sort de cette
            // plage est donc transposée d'octaves entières (÷2 ou ×2 sur le pitch) jusqu'à
            // retomber dans la plage jouable, plutôt que d'être purement écrêtée à l'extrême
            // : elle garde ainsi sa vraie note (do, ré, mi...), juste sur une octave voisine,
            // au lieu de sonner strictement identique à toutes les autres notes extrêmes.
            int clicks = note.key - 33; // touche NBS -> clic de bloc de notes (peut sortir de [0;24])
            while (clicks < 0) clicks += 12;
            while (clicks > 24) clicks -= 12;

            double semitoneOffset = (clicks - 12) + (note.pitchFine / 100.0);
            float pitch = (float) Math.pow(2.0, semitoneOffset / 12.0);
            pitch = Math.max(0.5f, Math.min(2.0f, pitch)); // garde-fou final (le fin réglage pitchFine pourrait déborder de justesse)
            float volume = (float) Math.max(0.0, Math.min(1.0, (note.velocity / 100.0) * volumeConfig));

            for (UUID uuid : gm.getPlayerTeams().keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, pitch);
                }
            }
        }

        private void cancelTasks() {
            for (BukkitTask task : noteTasks) task.cancel();
            noteTasks.clear();
            if (endOfSongTask != null) {
                endOfSongTask.cancel();
                endOfSongTask = null;
            }
        }
    }
}
