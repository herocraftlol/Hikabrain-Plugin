package fr.spide.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une équipe au sein d'une SpideMap : une couleur, un nombre de joueurs requis,
 * une liste de points de spawn (un par joueur attendu) et l'état de la partie en cours
 * (membres actuellement vivants, score).
 */
public class Team {

    private final String color; // ex: "ORANGE", "YELLOW", "RED", "BLACK", ou couleur perso ajoutée via /sp teamlist add
    private final int requiredPlayers;
    private final List<Location> spawnPoints = new ArrayList<>();

    // État de partie (remis à zéro à chaque nouvelle manche)
    private final List<UUID> members = new ArrayList<>();   // joueurs assignés à cette équipe pour la partie en cours
    private final List<UUID> alive = new ArrayList<>();     // joueurs encore en vie dans la manche en cours
    private int score = 0;

    public Team(String color, int requiredPlayers) {
        this.color = color;
        this.requiredPlayers = requiredPlayers;
    }

    public String getColor() {
        return color;
    }

    public int getRequiredPlayers() {
        return requiredPlayers;
    }

    public List<Location> getSpawnPoints() {
        return spawnPoints;
    }

    public boolean isSpawnsFull() {
        return spawnPoints.size() >= requiredPlayers;
    }

    /** @return true si le spawn a bien été ajouté, false si l'équipe a déjà tous ses spawns */
    public boolean addSpawnPoint(Location loc) {
        if (isSpawnsFull()) return false;
        spawnPoints.add(loc);
        return true;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public List<UUID> getAlive() {
        return alive;
    }

    public boolean isFull() {
        return members.size() >= requiredPlayers;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
        alive.add(uuid);
    }

    public void eliminate(UUID uuid) {
        alive.remove(uuid);
    }

    public boolean isEliminated() {
        return !members.isEmpty() && alive.isEmpty();
    }

    public void resetRound() {
        alive.clear();
        alive.addAll(members);
    }

    public void resetGame() {
        members.clear();
        alive.clear();
    }

    public int getScore() {
        return score;
    }

    public void addPoint() {
        score++;
    }

    public void resetScore() {
        score = 0;
    }
}
