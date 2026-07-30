# 🎮 HikaBrain Plugin

> Un plugin Minecraft complet pour Paper 1.21.1 - Système de capture de zone par équipes avec tournoi automatisé, chat d'arène et leaderboards holographiques.

**HikaBrain** est un minije u palpitant où deux équipes (Rouge vs Bleu) s'affrontent pour contrôler une zone centrale. Inspiré par le style Screaming Bedwars, ce plugin offre une expérience compétitive avec des statistiques détaillées, des classements holographiques et un système de tournoi intégré.

---

## ✨ Fonctionnalités Principales

### 🎯 Gameplay
- **Système de Capture de Zone** - Combat stratégique pour le contrôle du territoire
- **Deux Équipes** - Rouge vs Bleu avec spawns distincts
- **Scoreboard en Temps Réel** - Scores, kills, deaths, K/D et victoires
- **Compte à Rebours Configurable** - Lobby avec freeze des joueurs
- **Items de Jeu** - Bouton forcer le démarrage 🔵 et quitter la partie 🔴

### 🏆 Système de Tournoi
- **Tournois Automatisés** - Créez et gérez des tournois compétitifs
- **Matchs en Arène** - Duels entre équipes avec bracket visuel
- **Hologrammes de Tournoi** - Affichage 3D des brackets et classements
- **Historique des Matchs** - Sauvegarde complète des résultats

### 📊 Statistiques & Classements
- **Statistiques K/D** - Par équipe et par joueur
- **Leaderboards par Catégorie** - K/D, Victoires, Kills totaux
- **Hologrammes 3D** - Classements visibles dans le monde Minecraft
- **Persistance YAML** - Données sauvegardées automatiquement

### 🎨 Interface Graphique
- **GUI de Sélection d'Arène** - Interface intuitive pour choisir son arène
- **Sélection d'Équipe** - Choix Rouge/Bleu via GUI
- **Inventaire Dynamique** - Items adaptés à chaque état du jeu

### ⚔️ Système de Kit
- **Kits Configurables** - Équipement personnalisé par équipe
- **Attribution Automatique** - Distribution selon les paramètres

---

## 📋 Fonctionnalités Détaillées

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

## 🆕 Dernière Mise à Jour (v1.0.14)

### Améliorations du Système de Kit
- **Protection Renforcée des Items** - Les items du kit (épée en fer, pioche, pomme dorée et blocs) sont désormais parfaitement protégés contre :
  - Le déplacement via clic droit, touche numérique ou échange offhand
  - Le drop accidentel
  - La perte au décès (les items sont automatiquement restitués au round suivant)
- **Double Emplacement pour les Blocs** - Les blocs de construction (grès lisse) sont désormais disponibles dans l'offhand ET dans le slot 4 de la hotbar pour une meilleure expérience de jeu
- **Gestion du Drag-and-Drop** - Empêche désormais efficacement le glisser-déposer des items du kit vers d'autres emplacements

### Corrections de Bugs
- Correction d'un problème où les items du kit pouvaient être déplacés ou perdus dans certaines conditions
- Amélioration de la cohérence de la protection des items à travers tous les types d'interactions

---

## 📖 Installation

1. Téléchargez le JAR depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. Placez le fichier `HikaBrain.jar` dans le dossier `plugins` de votre serveur Paper 1.21.1
3. Redémarrez le serveur
4. Configurez les arènes avec `/hb create <nom>`

## ⚙️ Configuration

Le fichier `config.yml` permet de personnaliser :
- Nombre de joueurs minimum/maximum par arène
- Durée des comptes à rebours (lobby et round)
- Points nécessaires pour gagner
- Apparence complète du scoreboard (titre, lignes, couleurs)
- Messages personnalisés avec préfixe

## 🛠️ Compilation

- **Java** : 21
- **API** : Paper 1.21.1
- **Build** : Maven

```bash
# Cloner le dépôt
git clone https://github.com/herocraftlol/Hikabrain-Plugin.git

# Compiler
mvn clean package

# Le JAR sera dans target/HikaBrain.jar
```

## 📝 Auteur

- **Développeur**: herocraftlol
- **Version**: 1.0.14

## 📄 Licence

MIT License
