# 🎮 HikaBrain Plugin

> Un plugin Minecraft complet pour Paper 1.21.1 — Système de capture de zone par équipes avec tournoi automatisé, chat d'arène et leaderboards holographiques.

**HikaBrain** est un minije u palpitant où deux équipes (Rouge vs Bleu) s'affrontent pour contrôler une zone centrale. Inspiré par le style Screaming Bedwars, ce plugin offre une expérience compétitive avec des statistiques détaillées, des classements holographiques et un système de tournoi intégré.

---

## ✨ Fonctionnalités Principales

### 🎯 Gameplay
- **Système de Capture de Zone** — Combat stratégique pour le contrôle du territoire
- **Deux Équipes** — Rouge vs Bleu avec spawns distincts
- **Scoreboard en Temps Réel** — Scores, kills, deaths, K/D et victoires
- **Compte à Rebours Configurable** — Lobby avec freeze des joueurs
- **Items de Jeu** — Bouton forcer le démarrage 🔵 et quitter la partie 🔴

### 🏆 Système de Tournoi
- **Tournois Automatisés** — Créez et gérez des tournois compétitifs
- **Matchs en Arène** — Duels entre équipes avec bracket visuel
- **Hologrammes de Tournoi** — Affichage 3D des brackets et classements
- **Historique des Matchs** — Sauvegarde complète des résultats

### 📊 Statistiques & Classements
- **Statistiques K-D** — Par équipe et par joueur
- **Leaderboards par Catégorie** — K/D, Victoires, Kills totaux
- **Hologrammes 3D** — Classements visibles dans le monde Minecraft
- **Persistance YAML** — Données sauvegardées automatiquement

### 🎨 Interface Graphique
- **GUI de Sélection d'Arène** — Interface intuitive pour choisir son arène
- **Sélection d'Équipe** — Choix Rouge/Bleu via GUI
- **Inventaire Dynamique** — Items adaptés à chaque état du jeu

### ⚔️ Système de Kit
- **Kits Configurables** — Équipement personnalisé par équipe
- **Attribution Automatique** — Distribution selon les paramètres

---

## 📋 Commandes

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
| `/hb top` | Classement des 10 meilleurs joueurs |
| `/hb points` | Vos points et niveau |
| `/hb perk` | Gérer vos perks |
| `/tournament` | Système de tournoi automatisé |
| `/arenas` | GUI de sélection d'arène |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup arènes | OP |
| `hikabrain.play` | Jouer au Hikabrain | Tous |
| `hikabrain.tournament.join` | S'inscrire à un tournoi | Tous |

---

## 🆕 Dernière Mise à Jour (v1.0.22)

### 🔧 Améliorations Techniques et Optimisations

Cette mise à jour apporte des **améliorations substantielles** au module Web API et au système de classement, avec une meilleure stabilité et performance.

#### 🌐 Module Web API Reconstruit
- **Refonte complète du LeaderboardExportServer** pour une meilleure gestion des connexions HTTP
- **Optimisation du traitement des données** — Export des leaderboards plus rapide et plus fiable
- **Amélioration de la stabilité** — Meilleure gestion des erreurs et reconnexion automatique
- **Logging détaillé** — Plus d'informations pour le débogage

#### 📊 Système de Classement Amélioré
- **PowerRankingCalculator optimisé** — Algorithme de calcul du power ranking revu et corrigé
- **Gestion des Head-to-Head** — Meilleure gestion des affrontements directs entre joueurs
- **Historique des matchs enrichi** — Plus de données disponibles pour les statistiques

#### ⚔️ Améliorations du Gameplay
- **GameManager optimisé** — Meilleure gestion des états de jeu et transitions
- **Protection anti-AFK améliorée** — Système de détection des joueurs inactifs
- **Équilibrage du PvP** — Ajustements fins des mécaniques de combat

#### 🛡️ Corrections de Bugs
- Corrections diverses de bugs rapportés par la communauté
- Amélioration de la stabilité générale du plugin

---

### 🏆 Fonctionnalités Versions Précédentes

#### 🌟 Système de Points et Niveaux (v1.0.16)

Un **système de progression complet** pour récompenser votre investissement dans le jeu :

**Système de Points**
- **1 point** par coup porté à un adversaire
- **5 points** par kill
- **8 points** par but marqué (capture de zone)
- **15 points** pour une victoire d'équipe

**Progression par Niveaux**
- Les niveaux sont calculés avec des paliers progressifs
- Plus vous jouez, plus vous montez en niveau !

**Avantages Cosmétiques (100% Équitables)**

| Niveau requis | Perk | Description |
|--------------|------|-------------|
| 3 | 🌟 Nuage de particules | Un nuage de particules affichant votre tête flotte au-dessus de vous |
| 6 | ✨ Étincelles de victoire | Des étincelles dorées tourbillonnent quand votre équipe gagne |
| 10 | 💎 Étoile de prestige | Une étoile colorée à côté de votre niveau |

---

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

## 📥 Installation

1. Téléchargez le JAR depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. Placez le fichier `HikaBrain.jar` dans le dossier `plugins` de votre serveur Paper 1.21.1
3. Redémarrez le serveur
4. Configurez les arènes avec `/hb create <nom>`

## ⚙️ Configuration

Le fichier `config.yml` permet de personnaliser :
- Nombre de joueurs minimum/maximum par arène
- Durée des comptes à rebours (lobby et round)
- Points nécessaires pour gagner
- Système de points par action (coup, kill, but, victoire)
- Apparence du scoreboard (titre, lignes, couleurs)
- Messages personnalisés avec préfixe

---

## 📝 Auteur

- **Développeur**: herocraftlol
- **Version**: 1.0.22
- **API**: Paper 1.21.1

## 📄 Licence

MIT License
