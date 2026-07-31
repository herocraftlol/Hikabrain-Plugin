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

## 🆕 Dernière Mise à Jour (v1.0.17)

### 🎨 Personnalisation Avancée du GUI des Arènes

Cette mise à jour ajoute un contrôle total sur le placement des arènes dans le menu de sélection `/arenas` :

#### Nouvelle Commande `/hb guislot`
- **Placez vos arènes où vous le souhaitez** — Utilisez `/hb guislot <emplacement 1-45> <arène>` pour assigner une arène à un emplacement précis du GUI
- **Réservation d'emplacements** — Les emplacements 46 à 54 (dernière ligne) restent réservés au bouton "arène aléatoire"
- **Assignation persistente** — Les placements sont sauvegardés automatiquement et persistent au redémarrage du serveur
- **Flexibilité totale** — Retirez une assignation avec `/hb guislot <emplacement> clear` et l'emplacement sera automatiquement rempli par le système

#### Amélioration Technique : Affichage des Têtes de Joueur
- Correction du rendu des têtes de joueur pour l'effet cosmétique "Nuage de particules"
- Utilisation d'ArmorStands pour un affichage fidèle des skins Minecraft
- Nettoyage automatique des entités en cas d'arrêt brutal de partie

---

### 🌟 Système de Points et Niveaux (v1.0.16)

C'est une grande mise à jour qui ajoute un **système de progression complet** pour récompenser votre investissement dans le jeu :

#### Système de Points
- **1 point** par coup porté à un adversaire
- **5 points** par kill
- **8 points** par but marqué (capture de zone)
- **15 points** pour une victoire d'équipe

#### Progression par Niveaux
- Les niveaux sont calculés avec des paliers progressifs (chaque niveau demande plus de points que le précédent)
- Plus vous jouez, plus vous montez en niveau !

#### Avantages Cosmétiques (100% Équitables)
Tous les perks sont **purement cosmétiques** — ils ne donnent aucun avantage en jeu pour préserver l'équité :

| Niveau requis | Perk | Description |
|--------------|------|-------------|
| 3 | 🌟 Nuage de particules | Un nuage de particules affichant votre tête flotte au-dessus de vous en début de partie |
| 6 | ✨ Étincelles de victoire | Des étincelles dorées tourbillonnent autour de vous quand votre équipe gagne |
| 10 | 💎 Étoile de prestige | Une petite étoile colorée apparaît à côté de votre niveau affiché en jeu |

#### Nouvelles Commandes
- `/hb top` — Affiche le classement des 10 meilleurs joueurs par niveau
- `/hb points` — Affiche vos points et votre niveau actuel avec progression
- `/hb perk` — Gérez vos perks équipés (équiper/déséquiper)

### 📊 Améliorations du Scoreboard
- Scoreboard amélioré avec affichage en temps réel des scores et statistiques

### ⚙️ Configuration
Le fichier `config.yml` permet désormais de configurer :
- Les points attribués par action (coup, kill, but, victoire)
- Le palier de base pour les niveaux

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
- **Version**: 1.0.17

## 📄 Licence

MIT License
