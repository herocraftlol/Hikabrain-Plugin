# HikaBrain Plugin

Plugin Minecraft HikaBrain avec hologrammes de statistiques, GUI de sélection d'arène et système de capture de zone par équipes.

## Fonctionnalités

### 🎮 Gameplay
- **Capture de zone par équipes** - Système type Screaming Bedwars
- **Scoreboard en temps réel** - Affichage des scores, kills, deaths et K/D
- **Système de freeze** - Joueurs immobilisés pendant le compte à rebours

### 📊 Statistiques
- Statistiques K/D par équipe
- Persistance des statistiques dans un fichier YAML
- **Hologrammes de statistiques** - Affichage 3D des stats dans le monde

### 🎨 Interface
- **GUI de sélection d'arène** - Interface graphique pour parcourir les arènes
- Sélection d'équipe (rouge/bleu) via GUI
- Inventaire personnalisé par état du jeu

## Commandes

| Commande | Description |
|----------|-------------|
| `/hb` | Commande principale |
| `/hb stats` | Voir les statistiques |
| `/hb holostats <x> <y> <z>` | Créer un hologramme de stats |
| `/hb holoremove` | Supprimer les hologrammes |
| `/arenas` | Ouvrir le GUI de sélection d'arène |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup | OP |
| `hikabrain.play` | Jouer au Hikabrain | Tous |

## Compilation

- **Java** : 21
- **API** : Paper 1.21.1
- **Build** : Maven

```bash
mvn clean package
```

## Installation

1. Téléchargez le JAR depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. Placez le fichier dans le dossier `plugins` de votre serveur Paper 1.21.1
3. Redémarrez le serveur

## Configuration

Le fichier `config.yml` permet de configurer :
- Nombre de joueurs min/max
- Durée des compte à rebours
- Points nécessaires pour gagner
- Apparence du scoreboard

## Auteur

- **Author**: Claude
- **Version**: 1.0.2

## Licence

MIT License
