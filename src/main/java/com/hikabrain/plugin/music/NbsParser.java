package com.hikabrain.plugin.music;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyse un fichier .nbs (format "Note Block Song" de Note Block Studio / Open Note
 * Block Studio) pour en extraire la liste des notes à jouer et le tempo — voir
 * {@link NbsSong}.
 *
 * Implémenté directement à partir de la spécification officielle du format
 * (https://opennbs.gitbook.io/open-note-block-studio/nbs-format), en gérant à la fois
 * l'ancien format "classique" (v0, sans en-tête de version) et les versions récentes
 * (v1 à v5, celles que produit Open Note Block Studio aujourd'hui). Aucune dépendance
 * externe n'est nécessaire : tout est lu directement, en pur Java.
 *
 * On ne lit QUE l'en-tête et la section des notes (le strict nécessaire pour la lecture) :
 * les sections optionnelles "calques" et "instruments personnalisés" qui suivent dans le
 * fichier ne sont jamais lues, ce qui simplifie beaucoup le parseur (elles ne servent
 * qu'à l'éditeur Note Block Studio, pas à la lecture des notes elles-mêmes).
 */
public final class NbsParser {

    private NbsParser() {
    }

    public static NbsSong parse(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            int firstShort = readShort(in);

            int version;
            int songLengthHeader = 0;

            if (firstShort != 0) {
                // Ancien format "classique" (v0) : les 2 premiers octets sont directement
                // la longueur de la chanson, il n'y a pas de champ de version.
                version = 0;
                songLengthHeader = firstShort;
            } else {
                // Format récent (v1+) : les 2 premiers octets à 0 indiquent ce format,
                // suivis de la version puis du nombre d'instruments vanilla.
                version = readUnsignedByte(in);
                readUnsignedByte(in); // nombre d'instruments vanilla (pas utile ici)
                if (version >= 3) {
                    songLengthHeader = readShort(in);
                }
            }

            int songHeight = readShort(in); // nombre de calques (pas utile ici, mais fait avancer le curseur)
            readString(in); // titre
            readString(in); // auteur
            readString(in); // auteur original
            readString(in); // description
            double tempo = readShort(in) / 100.0; // "ticks de chanson" par seconde

            in.readBoolean(); // sauvegarde auto activée
            in.readByte();    // durée entre sauvegardes auto
            in.readByte();    // signature temporelle (x/4èmes)
            readInt(in); // minutes passées sur le projet
            readInt(in); // clics gauche
            readInt(in); // clics droit
            readInt(in); // blocs ajoutés
            readInt(in); // blocs retirés
            readString(in); // nom du fichier .midi/.schematic d'origine

            if (version >= 4) {
                in.readBoolean(); // boucle activée (géré nous-mêmes côté HikaBrain, pas besoin de le lire précisément)
                in.readByte();    // nombre max de boucles
                readShort(in);    // tick de reprise de boucle
            }

            List<NbsSong.NbsNote> notes = new ArrayList<>();
            int maxTick = songLengthHeader;

            int tick = -1;
            while (true) {
                int jumpTicks = readShort(in);
                if (jumpTicks == 0) break;
                tick += jumpTicks;

                int layer = -1;
                while (true) {
                    int jumpLayers = readShort(in);
                    if (jumpLayers == 0) break;
                    layer += jumpLayers;

                    int instrument = readUnsignedByte(in);
                    int key = in.readByte();

                    int velocity = 100;
                    int pitchFine = 0;
                    if (version >= 4) {
                        velocity = readUnsignedByte(in);
                        readUnsignedByte(in); // panoramique (non utilisé, pas de son stéréo ici)
                        pitchFine = readShort(in);
                    }

                    notes.add(new NbsSong.NbsNote(tick, instrument, key, velocity, pitchFine));
                    if (tick > maxTick) maxTick = tick;
                }
            }

            return new NbsSong(notes, tempo, maxTick, file.getName());
        }
    }

    // ── Lecture bas niveau (little-endian, comme spécifié par le format .nbs) ──────────

    private static int readUnsignedByte(DataInputStream in) throws IOException {
        return in.readUnsignedByte();
    }

    private static int readShort(DataInputStream in) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        return (short) (b1 + (b2 << 8));
    }

    private static int readInt(DataInputStream in) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        int b4 = in.readUnsignedByte();
        return b1 + (b2 << 8) + (b3 << 16) + (b4 << 24);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readInt(in);
        StringBuilder sb = new StringBuilder(Math.max(0, length));
        for (int i = 0; i < length; i++) {
            char c = (char) in.readByte();
            if (c == 0x0D) c = ' '; // comme le fait Note Block Studio lui-même
            sb.append(c);
        }
        return sb.toString();
    }
}
