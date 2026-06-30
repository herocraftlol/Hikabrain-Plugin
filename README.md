# HikaBrain Plugin

Plugin Minecraft HikaBrain - Système de capture de zone par équipes avec leaderboards par catégorie pour Paper 1.21.1.

## Fonctionnalités

### 🎮 Gameplay
- **Capture de zone par équipes** - Système de combat rouge vs bleu inspiré de Screaming Bedwars
- **Scoreboard en temps réel** - Affichage des scores, kills, deaths, K/D et victoires par équipe
- **Système de lobby** - Compte à rebours configurable avec freeze des joueurs
- **Classements par catégorie** - Leaderboards K/D, Victoires, Kills totaux avec hologrammes 3D

### 📊 Statistiques
- Statistiques K/D par équipe
- Persistance des statistiques dans un fichier YAML
- **Hologrammes de leaderboard** - Affichage 3D des classements dans le monde

### 🎨 Interface
- **GUI de sélection d'arène** - Interface graphique pour parcourir et sélectionner les arènes
- Sélection d'équipe (rouge/bleu) via GUI
- Inventaire personnalisé par état du jeu

### ⚔️ Système de Kit
- Kits configurables pour les joueurs
- Attribution automatique en fonction des paramètres

## Commandes

| Commande | Description |
|----------|-------------|
| `/hb` | Commande principale du plugin |
| `/hb create <nom>` | Créer une nouvelle arène |
| `/hb delete <nom>` | Supprimer une arène |
| `/hb list` | Lister toutes les arènes |
| `/hb arenas` | Ouvrir le GUI de sélection d'arène |
| `/hb setlobby <arène>` | Définir le point de lobby |
| `/hb setspawn <arène> <rouge/bleu>` | Définir les spawns d'équipe |
| `/hb setcapture <arène>` | Définir la zone de capture |
| `/hb setgamezone <arène>` | Définir la zone de jeu |
| `/hb start <arène>` | Démarrer une partie |
| `/hb stop <arène>` | Arrêter une partie |
| `/hb join <arène>` | Rejoindre une arène |
| `/hb joinrandom` | Rejoindre une arène aléatoire |
| `/hb leave` | Quitter la partie |
| `/hb stats` | Voir les statistiques |
| `/hb leaderboard` | Afficher le leaderboard |
| `/arenas` | Ouvrir le GUI de sélection d'arène |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup arènes | OP |
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
- Durée des compte à rebours (lobby et round)
- Points nécessaires pour gagner
- Apparence complète du scoreboard (titre, lignes, couleurs)
- Messages personnalisés avec préfixe

## Auteur

- **Author**: Claude
- **Version**: 1.0.2

## Licence

MIT License
