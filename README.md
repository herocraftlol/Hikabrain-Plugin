# ⚔️ HikaBrain Plugin

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=for-the-badge&logo=minecraft)
![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-API-red?style=for-the-badge&logo=paper)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

> **Plugin de combat tactique par équipes avec capture de zones, hologrammes 3D, leaderboards dynamiques et interfaces graphiques immersives !**

---

## 🎮 À Propos

**HikaBrain** est un plugin Minecraft compétitif mettant en scène des combats tactiques par équipes. Inspired du style *Screaming Bedwars*, les joueurs s'affrontent pour contrôler des zones de capture tout en enchaînant les kills. Le plugin offre une expérience complète avec :

- 🏆 Classements en temps réel
- 🌟 Hologrammes 3D de statistiques
- 🎨 Interfaces graphiques modernes
- 📊 Scoreboards dynamiques
- 💾 Persistance des données en YAML

---

## ✨ Fonctionnalités

### 🎯 Gameplay
| Feature | Description |
|---------|-------------|
| **Capture de Zone** | Système de contrôle territorial red/blue inspiré Bedwars |
| **Freeze Countdown** | Joueurs immobilisés avant le début de la partie |
| **Kits Personnalisés** | Système de kits intégré pour équiper les joueurs |
| **Protection d'Arène** | Blocages protégés avec restauration automatique |

### 📊 Statistiques & Classements
| Feature | Description |
|---------|-------------|
| **Stats K/D** | Suivi kills, deaths, victoires et parties jouées |
| **Leaderboards** | Top 10 par catégorie (victoires, kills, K/D, parties) |
| **Hologrammes 3D** | Affichage flottant des statistiques dans le monde |
| **Persistance YAML** | Sauvegarde automatique des données |

### 🎨 Interface
| Feature | Description |
|---------|-------------|
| **GUI d'Arènes** | Sélection visuelle des arènes disponibles |
| **GUI d'Équipe** | Choix entre équipe Rouge et Bleue |
| **Scoreboard Dynamique** | Affichage temps réel des scores |
| **Inventaires Contextuels** | Items adaptés à chaque état du jeu |

---

## 📋 Commandes Complètes

### 🎮 Commandes Joueurs

| Commande | Description |
|----------|-------------|
| `/hb join <nom>` | Rejoindre une arène spécifique |
| `/hb joinrandom` | Rejoindre une arène aléatoire (priorité arenas occupées) |
| `/hb leave` | Quitter la partie en cours |
| `/hb list` | Lister toutes les arènes avec leur état |
| `/hb info [nom]` | Voir les informations d'une arène |
| `/hb stats [pseudo]` | Voir vos statistiques ou celles d'un joueur |
| `/hb top [kd\|kills\|wins\|games]` | Classement des meilleurs joueurs |
| `/arenas` | Ouvrir le GUI de sélection d'arène |

### 🔧 Commandes Admin / Setup

| Commande | Description | Permission |
|----------|-------------|------------|
| `/hb create <nom>` | Créer une nouvelle arène | `hikabrain.admin` |
| `/hb delete <nom>` | Supprimer une arène | `hikabrain.admin` |
| `/hb setlobby <nom>` | Définir le point de spawn lobby | `hikabrain.admin` |
| `/hb setspawn <nom> <red\|blue> <index>` | Définir un spawn d'équipe (1-4) | `hikabrain.admin` |
| `/hb delspawn <nom> <red\|blue> <index>` | Supprimer un spawn d'équipe | `hikabrain.admin` |
| `/hb setcapture <nom> <red\|blue> <pos1\|pos2>` | Définir les coins de zone de capture | `hikabrain.admin` |
| `/hb setgamezone <nom> <pos1\|pos2>` | Définir la zone de jeu protégée | `hikabrain.admin` |
| `/hb start <nom>` | Forcer le démarrage d'une partie | `hikabrain.admin` |
| `/hb stop <nom>` | Forcer l'arrêt d'une partie | `hikabrain.admin` |
| `/hb resetstats` | Réinitialiser toutes les statistiques | `hikabrain.admin` |

### 🌟 Hologrammes & Leaderboards

| Commande | Description |
|----------|-------------|
| `/hb holostats` | Faire apparaître un hologramme de stats à votre position |
| `/hb holoremove` | Supprimer tous les hologrammes de stats |
| `/hb leaderboard <victoires\|kills\|kd\|parties>` | Spawner un leaderboard Top 10 |
| `/hb leaderboard <catégorie> remove` | Supprimer un leaderboard |

### 📊 Configuration Scoreboard

| Commande | Description |
|----------|-------------|
| `/hb setsbserver <nom>` | Définir le nom du serveur affiché |
| `/hb setsbgame <nom>` | Définir le nom du jeu affiché |
| `/hb setsbtitle <titre>` | Définir le titre du scoreboard |
| `/hb setsblines <lignes>` | Définir les lignes (séparées par `\|`) |
| `/hb reloadsb` | Recharger la configuration |
| `/hb sbinfo` | Voir les informations du scoreboard |

---

## 🔑 Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Accès complet administration, setup, hologrammes | OP |
| `hikabrain.play` | Permission de jouer à HikaBrain | Tous |

---

## 🏗️ Architecture Technique

```
com.hikabrain.plugin/
├── commands/          # Gestionnaire de commandes /hb
├── game/              # Logique de jeu (Arena, GameManager, Teams...)
├── gui/               # Interfaces graphiques (ArenaGUI)
├── hologram/          # Hologrammes et leaderboards 3D
├── listeners/         # 10+ listeners d'événements Bukkit
├── scoreboard/        # Gestionnaire de scoreboard dynamique
├── stats/             # Système de statistiques persistantes
└── util/              # Utilitaires (MessageUtil)
```

### Stack Technique
- **Java** 21
- **Paper API** 1.21.1
- **Build** Maven avec shade plugin
- **Data** YAML pour la persistance

---

## 🚀 Installation

### Méthode 1 : Compilation depuis les sources

```bash
# Cloner le repository
git clone https://github.com/herocraftlol/Hikabrain-Plugin.git
cd Hikabrain-Plugin

# Compiler avec Maven
mvn clean package

# Le JAR sera dans target/HikaBrain.jar
```

### Méthode 2 : Télécharger la release

1. Téléchargez le JAR depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. Placez le fichier dans le dossier `plugins` de votre serveur Paper 1.21.1
3. Redémarrez le serveur

---

## ⚙️ Configuration

### config.yml
Le fichier `config.yml` permet de configurer :
- Nombre de joueurs minimum/maximum par arène
- Durée des compte à rebours
- Points nécessaires pour gagner
- Apparence et contenu du scoreboard

### Setup d'une Arène

```bash
# 1. Créer l'arène
/hb create maArene

# 2. Définir le lobby
/hb setlobby maArene

# 3. Ajouter les spawns équipe rouge (jusqu'à 4)
/hb setspawn maArene red 1

# 4. Ajouter les spawns équipe bleue (jusqu'à 4)
/hb setspawn maArene blue 1

# 5. Définir les zones de capture (2 clics pour chaque)
/hb setcapture maArene red pos1  # Clic 1
/hb setcapture maArene red pos2  # Clic 2
/hb setcapture maArene blue pos1
/hb setcapture maArene blue pos2

# 6. Définir la zone de jeu (protection globale)
/hb setgamezone maArene pos1  # Clic 1
/hb setgamezone maArene pos2  # Clic 2
```

---

## 📈 États du Jeu

```
WAITING → COUNTDOWN → PLAYING → ENDING
   ↓          ↓          ↓
  Lobby    Freeze    Combat
```

| État | Description |
|------|-------------|
| `WAITING` | En attente de joueurs minimum |
| `COUNTDOWN` | Freeze + compte à rebours |
| `PLAYING` | Partie active avec captures |
| `ENDING` | Fin de partie + affichage résultats |

---

## 🎯 États des Joueurs

| État | Description |
|------|-------------|
| `LOBBY` | Dans le lobby, sélection d'équipe |
| `PLAYING` | En jeu, peut capturer et combattre |
| `SPECTATING` | Spectateur (à implémenter) |

---

## 📜 Licence

MIT License - Libre d'utilisation et de modification.

---

## 👤 Auteur

- **Version**: 1.0.9
- **API**: Paper 1.21.1
- **Java**: 21

---

<p align="center">
  <strong>⭐ N'hésitez pas à star le projet si vous l'appréciez ! ⭐</strong>
</p>
