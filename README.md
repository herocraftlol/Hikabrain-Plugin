# 🎮 HikaBrain Plugin

> Un plugin Minecraft complet pour Paper 1.21.1 — Système de capture de zone par équipes avec tournoi automatisé, boutique de cosmétiques, musique NBS, niveaux & perks, et leaderboards holographiques.

**HikaBrain** est un minijeu palpitant où deux équipes (Rouge vs Bleu) s'affrontent pour contrôler une zone centrale. Inspiré par le style Screaming Bedwars, ce plugin offre une expérience compétitive avec des statistiques détaillées, des classements holographiques, un système de tournoi intégré et désormais une véritable **boutique de cosmétiques** pour récompenser l'investissement des joueurs.

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
- **Boutique de Cosmétiques** - GUI d'achat et d'équipement (`/cosmetics`)
- **Inventaire Dynamique** - Items adaptés à chaque état du jeu

### 🛍️ Boutique de Cosmétiques (NOUVEAU)
- **~50 cosmétiques** répartis en 5 catégories : chapeaux, particules, traînées, tags et entrées
- **4 raretés** : Commun, Rare, Épique et Légendaire
- **Achat avec points** dépensables (sans jamais baisser le niveau ni le classement)
- **Niveau minimum requis** pour éviter le « farming » intensif
- **Purement visuel** — aucun avantage en jeu, et invisibles pendant les parties HikaBrain

### ⚔️ Système de Kit
- **Kits Configurables** - Équipement personnalisé par équipe
- **Attribution Automatique** - Distribution selon les paramètres

### 🎵 Système de Musique
- **Jukebox dans l'Arène** - Contrôle de la musique pendant les parties
- **Musique d'Ambiance** - Bandes sonores adaptatives selon l'état du jeu
- **Commande `/hb music`** - Gestion complète de la musique
- **Support des fichiers NBS** personnalisés

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
| `/hb top` | Classement des 10 meilleurs joueurs par niveau |
| `/hb points` | Voir ses points et son niveau |
| `/hb perk` | Gérer ses perks équipés |
| `/hb music` | Gérer la musique de l'arène |
| `/hb leaderboard` | Afficher le leaderboard |
| `/arenas` | Ouvrir le GUI de sélection d'arène |
| `/cosmetics` | Ouvrir la boutique de cosmétiques |
| `/tournament` | Système de tournoi automatisé |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup arènes, tournoi | OP |
| `hikabrain.play` | Jouer au HikaBrain | Tous |
| `hikabrain.tournament.join` | S'inscrire à un tournoi | Tous |

## 🆕 Dernière Mise à Jour (v1.0.25)

### 🛍️ Boutique de Cosmétiques

Cette mise à jour majeure introduit un tout nouveau **système de cosmétiques** pour récompenser et personnaliser l'expérience des joueurs les plus assidus.

#### 🎭 Catégories de Cosmétiques
- **Chapeaux** — Casquettes en cuir teint, têtes de mobs (zombie, squelette, wither, creeper, dragon)
- **Particules** — Auras visuelles (flammes, cœurs, notes de musique, souffle de dragon, totem divin…)
- **Traînées** — Effets au déplacement (poussières colorées, portail, feu d'artifice…)
- **Tags** — Préfixes personnalisés affichés à côté du nom
- **Entrées** — Effets spectaculaires à la connexion/entrée en arène

#### 💎 Raretés & Progression
- **4 raretés** : Commun, Rare, Épique, Légendaire
- Prix et niveaux requis **progressifs** : les cosmétiques légendaires représentent plusieurs semaines de jeu régulier
- Achat via un **solde dépensable** distinct du total de points — dépenser ne fait **jamais** baisser le niveau ni le classement
- **Niveau minimum** requis pour empêcher le farming intensif sur courte période

#### 🧩 Nouvelles Commandes & GUI
- **`/cosmetics`** (alias `/cosmetic`, `/hbshop`) — Ouvre la boutique de cosmétiques
- GUI complet d'achat, d'équipement et de déséquipement par catégorie
- Cosmétiques **invisibles pendant les parties HikaBrain** pour préserver l'équité

#### 🔧 Corrections & Améliorations
- Intégration du système de cosmétiques avec le gestionnaire de niveaux existant
- Nettoyage et refactorisation du code pour la maintenabilité

---

### 🆕 Fonctionnalités Versions Précédentes

#### 🎵 Système de Musique (v1.0.24)
- Jukebox dans l'arène, musique d'ambiance adaptative, commande `/hb music`, support NBS

#### 🏆 Système de Classement Amélioré (v1.0.20)
- HeadToHeadManager pour les affrontements directs
- PowerRankingCalculator pour évaluer les joueurs

#### 🌐 Module Web API (v1.0.19)
- LeaderboardExportServer pour exporter les données

#### 🌟 Système de Points et Niveaux (v1.0.16)
- Points par action (coup, kill, but, victoire)
- Progression par niveaux à paliers progressifs
- Perks purement cosmétiques (nuage de particules, étincelles, étoile de prestige)

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
- **Version**: 1.0.25

## 📄 Licence

MIT License
