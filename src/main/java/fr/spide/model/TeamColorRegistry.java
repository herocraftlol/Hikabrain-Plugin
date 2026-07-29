package fr.spide.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Liste ordonnée des couleurs d'équipe disponibles, dans l'ordre où elles seront
 * distribuées lors d'un /sp <map> equipe <n> <joueurs>.
 *
 * Les 4 premières couleurs (ORANGE, YELLOW, RED, BLACK) sont celles demandées en priorité.
 * Les 12 couleurs suivantes complètent les 16 couleurs de teinte Minecraft (laine, verre
 * teinté, bannières...) afin qu'aucune équipe ne se retrouve jamais sans couleur dédiée,
 * même avec beaucoup d'équipes. Elles sont ajoutées automatiquement dans cet ordre :
 * WHITE, LIGHT_GRAY, GRAY, CYAN, LIGHT_BLUE, BLUE, PURPLE, MAGENTA, PINK, LIME, GREEN, BROWN.
 *
 * Des couleurs personnalisées supplémentaires peuvent toujours être ajoutées avec
 * /sp teamlist add <couleur>, et l'ordre d'ajout est conservé.
 */
public class TeamColorRegistry {

    private final List<String> order = new ArrayList<>();

    public TeamColorRegistry() {
        // Les 4 couleurs prioritaires
        order.add("ORANGE");
        order.add("YELLOW");
        order.add("RED");
        order.add("BLACK");

        // Les 12 couleurs restantes des 16 teintes Minecraft, dans un ordre choisi
        // pour rester cohérent visuellement (neutres, puis froides, puis chaudes)
        order.add("WHITE");
        order.add("LIGHT_GRAY");
        order.add("GRAY");
        order.add("CYAN");
        order.add("LIGHT_BLUE");
        order.add("BLUE");
        order.add("PURPLE");
        order.add("MAGENTA");
        order.add("PINK");
        order.add("LIME");
        order.add("GREEN");
        order.add("BROWN");
    }

    public List<String> getOrder() {
        return Collections.unmodifiableList(order);
    }

    public boolean add(String color) {
        String c = color.toUpperCase();
        if (order.contains(c)) return false;
        order.add(c);
        return true;
    }

    public void setOrder(List<String> newOrder) {
        order.clear();
        order.addAll(newOrder);
    }

    public List<String> firstN(int n) {
        return new ArrayList<>(order.subList(0, Math.min(n, order.size())));
    }
}
