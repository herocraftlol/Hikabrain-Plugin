package com.hikabrain.plugin.music;

import java.util.List;

/**
 * Une musique .nbs déjà analysée (voir {@link NbsParser}), prête à être jouée.
 * Contient uniquement ce dont on a besoin pour la lecture (les notes et le tempo) —
 * les métadonnées cosmétiques du fichier (auteur, description...) ne sont pas conservées.
 */
public class NbsSong {

    /** Une note à jouer, à un instant donné de la chanson. */
    public static class NbsNote {
        /** Tick de la chanson (pas un tick Minecraft) auquel cette note doit sonner. */
        public final int tick;
        /** Index d'instrument NBS (0-9 = instruments vanilla ; au-delà = ignoré, voir NbsParser). */
        public final int instrument;
        /** Touche NBS (0-87). 45 = F#4 = la note "centrale" d'un bloc de notes vanilla. */
        public final int key;
        /** Volume de la note (0-100), présent uniquement à partir du format NBS v4. */
        public final int velocity;
        /** Réglage fin de hauteur en centièmes de demi-ton, présent uniquement à partir du format NBS v4. */
        public final int pitchFine;

        public NbsNote(int tick, int instrument, int key, int velocity, int pitchFine) {
            this.tick = tick;
            this.instrument = instrument;
            this.key = key;
            this.velocity = velocity;
            this.pitchFine = pitchFine;
        }
    }

    private final List<NbsNote> notes;
    /** "Ticks de chanson" par seconde (ex: 10.0 = un tick de chanson toutes les 100ms). */
    private final double tempo;
    /** Durée totale en ticks de chanson (dernier tick contenant une note). */
    private final int lengthInTicks;
    private final String fileName;

    public NbsSong(List<NbsNote> notes, double tempo, int lengthInTicks, String fileName) {
        this.notes = notes;
        this.tempo = tempo > 0 ? tempo : 10.0;
        this.lengthInTicks = lengthInTicks;
        this.fileName = fileName;
    }

    public List<NbsNote> getNotes() {
        return notes;
    }

    public double getTempo() {
        return tempo;
    }

    public int getLengthInTicks() {
        return lengthInTicks;
    }

    public String getFileName() {
        return fileName;
    }

    /** Durée totale de la chanson en ticks Minecraft (20 ticks/seconde), pour savoir quand la reboucler. */
    public long getLengthInMinecraftTicks() {
        return Math.round(lengthInTicks * (20.0 / tempo));
    }
}
