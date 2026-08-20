# 🎮 HikaBrain Plugin

![Version](https://img.shields.io/badge/version-1.0.30-blue)
![Paper](https://img.shields.io/badge/Paper-1.21.1-orange)
![Java](https://img.shields.io/badge/Java-21-red)

> Un plugin Minecraft complet pour Paper 1.21.1 — Capture de zone par équipes, tournois automatisés, boutique de cosmétiques, musique NBS, niveaux & perks, **leaderboards holographiques par format (1v1/2v2/3v3/4v4)**, **hologrammes de statistiques personnelles au style entièrement configurable et personnalisés pour chaque joueur**, et **rematch en un clic en fin de partie**.

**HikaBrain** est un minijeu palpitant où deux équipes (Rouge vs Bleu) s'affrontent pour contrôler une zone centrale. Inspiré par le style Screaming Bedwars, ce plugin offre une expérience compétitive avec des statistiques détaillées, des classements holographiques (globaux **et par format d'équipe**), des **hologrammes de statistiques personnelles** (chaque joueur voit ses propres stats en s'approchant) construits sur les **TextDisplay natifs** — une seule entité, aucun scintillement, apparence configurable —, un système de tournoi intégré, une véritable **boutique de cosmétiques** pour récompenser l'investissement des joueurs, et un **bouton « Rejouer »** qui relance instantanément une partie du même format à la fin de chaque match.

---

## ✨ Fonctionnalités Principales

### 🎯 Gameplay
- **Système de Capture de Zone** - Combat stratégique pour le contrôle du territoire
- **Deux Équipes** - Rouge vs Bleu avec spawns distincts
- **Scoreboard en Temps Réel** - Scores, kills, deaths, K/D et victoires
- **Compte à Rebours Configurable** - Lobby avec freeze des joueurs
- **Items de Jeu** - Bouton forcer le démarrage 🔵 et quitter la partie 🔴
- **Rematch en un Clic** - À la fin de chaque partie, deux boutons cliquables dans le chat : « ▶ REJOUER » (relance une partie du même format 1v1/2v2/3v3…) et « ✖ QUITTER » (rester au lobby)

### 🏆 Système de Tournoi
- **Tournois Automatisés** - Créez et gérez des tournois compétitifs
- **Matchs en Arène** - Duels entre équipes avec bracket visuel
- **Hologrammes de Tournoi** - Affichage 3D des brackets et classements
- **Historique des Matchs** - Sauvegarde complète des résultats

### 📊 Statistiques & Classements
- **Statistiques K/D** - Par équipe et par joueur
- **Leaderboards par Catégorie** - K/D, Victoires, Kills, Parties jouées…
- **Leaderboards par Format** - Tops 1v1, 2v2, 3v3 et 4v4 (par victoires dans chaque format)
- **Hologrammes 3D sans scintillement** - Une seule entité `TextDisplay` par hologramme, mise à jour en place, apparence configurable (`hologram-style` dans config.yml)
- **Hologrammes de Statistiques Personnelles** - Posez un hologramme via `/hb statshologram` : chaque joueur qui s'approche y voit **ses propres** stats (niveau, points, K/D, victoires, parties, temps de jeu, classements jour / semaine / total) — **simultanément, même à plusieurs joueurs près du même hologramme** (une entité cachée par joueur, montrée uniquement à lui) — et redimensionnez-le avec `/hb statshologram size <taille>`
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
- **Lecture Aléatoire Intelligente** - Mode « random » par arène : cycle mélangé de toutes les pistes sans répétition tant que le cycle n'est pas épuisé
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
| `/hb leaderboard <victoires\|kills\|kd\|parties\|1v1\|2v2\|3v3\|4v4>` | Poser un leaderboard top 10 à votre position |
| `/hb leaderboard <catégorie> [remove\|size <taille>]` | Supprimer ou redimensionner le leaderboard le plus proche |
| `/hb statshologram` | Poser un hologramme de statistiques personnelles |
| `/hb statshologram remove` | Supprimer l'hologramme le plus proche |
| `/hb statshologram size <taille>` | Redimensionner l'hologramme de stats le plus proche (ex: 1.5) |
| `/hb rematch <teamSize>` | Rejoindre une nouvelle partie du même format (déclenché par le bouton « ▶ REJOUER ») |
| `/hb rematchcancel` | Rester au lobby (déclenché par le bouton « ✖ QUITTER ») |
| `/arenas` | Ouvrir le GUI de sélection d'arène |
| `/cosmetics` | Ouvrir la boutique de cosmétiques |
| `/tournament` | Système de tournoi automatisé |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup arènes, tournoi | OP |
| `hikabrain.play` | Jouer au HikaBrain | Tous |
| `hikabrain.tournament.join` | S'inscrire à un tournoi | Tous |

## 🆕 Dernière Mise à Jour (v1.0.30)

Cette version perfectionne les **hologrammes de statistiques personnelles** : lorsque plusieurs joueurs s'approchent du même hologramme, **chacun voit désormais ses propres statistiques en même temps**, et plus seulement celles du joueur le plus proche.

---

### 👥 Hologrammes de Stats Réellement Personnalisés, Joueur par Joueur

#### 🚫 Avant

L'hologramme personnel n'affichait que les stats du **joueur le plus proche** : avec plusieurs joueurs autour, un seul voyait les siennes, les autres n'avaient qu'un texte « En attente d'un joueur... » — ou pire, elles se mélangeaient pour tout le monde.

#### ✨ Maintenant

Dès qu'un joueur s'approche, une **entité `TextDisplay` dédiée** est créée pour lui : **cachée à tout le monde par défaut** (`setVisibleByDefault(false)`), puis **montrée uniquement à ce joueur** (`Player#showEntity`) — la méthode officiellement recommandée par Paper pour afficher un contenu différent à chaque joueur sur une même position.

- **Simultanéité totale** : cinq joueurs autour du même hologramme voient chacun leurs propres stats en même temps, sans jamais se marcher dessus
- **Toujours à jour** : tant qu'un joueur reste à portée (rayon de 5 blocs), son entité personnelle est **rafraîchie en continu**, toutes les 2 secondes — jamais de placeholder « en attente » pour lui
- **Zéro résidu** : l'entité personnelle est **créée à l'arrivée** du joueur et **supprimée** dès qu'il s'éloigne ou se déconnecte
- **Style partagé** : toute l'apparence configurable de la v1.0.29 (fond, ombre, orientation, échelle…) s'applique aussi à chaque entité personnelle, et sa mise à jour se répercute sur tous les hologrammes existants

#### 🛠️ Détails techniques

- Création à la volée d'un `TextDisplay` par joueur à portée, avec suppression automatique à la sortie du rayon de détection (5 blocs)
- `Player#showEntity` / `Entity#setVisibleByDefault(false)` : rendu côté client strictement filtré par joueur — aucun risque qu'un joueur voie les stats d'un autre
- Mise à jour du texte **en place** (jamais de respawn), conservant l'approche « zéro scintillement » introduite en v1.0.29
- Persistance inchangée (`personal-holograms.yml`) : seuls les emplacements et échelles sont sauvegardés ; les entités personnelles sont recréées dynamiquement

### 📋 Résumé des changements
| Fichier | Changement |
|---------|------------|
| `StatsHologramManager` | Refonte : une entité `TextDisplay` personnelle par joueur à portée (montrée uniquement à lui), au lieu d'une seule entité limitée au joueur le plus proche |
| `plugin.yml` | Version → `1.0.30` + description mise à jour |

---

## 🆕 Mise à jour précédente (v1.0.29)

Cette version est entièrement dédiée aux **hologrammes** : ils passent tous au `TextDisplay` natif (une seule entité, **zéro scintillement**), leur apparence devient **entièrement configurable**, et le système de leaderboards s'enrichit de **quatre nouvelles catégories par format d'équipe**.

---

### ✨ Hologrammes Repensés : TextDisplay Natif & Zéro Scintillement

Jusqu'ici, les hologrammes de statistiques personnelles empilaient **8 ArmorStands invisibles** (un par ligne) et les leaderboards supprimaient puis recréaient toutes leurs lignes à chaque rafraîchissement — ce qui pouvait causer un **clignotement visible** et des désynchronisations.

- **Maintenant** : chaque hologramme (stats personnelles **et** leaderboards) est **une seule entité `TextDisplay`** — le vrai type « hologramme » natif de Minecraft depuis la 1.19.4, multi-lignes en une seule entité.
- L'entité n'est **jamais respawnée** : à chaque rafraîchissement automatique, seul son texte est mis à jour en place. L'affichage reste **fluide, permanent et sans aucun scintillement**.

### 🎨 Apparence Entièrement Configurable (`hologram-style`)

Une nouvelle section `hologram-style` dans le `config.yml` contrôle l'apparence de **tous** les hologrammes du plugin, pour une identité visuelle cohérente :

| Option | Rôle |
|--------|------|
| `background` | Fond du texte : `default` (vanilla), `none` (transparent) ou couleur ARGB `#AARRGGBB` |
| `see-through` | Voir le texte à travers les blocs (`true`/`false`) |
| `shadow` | Ombre portée sous le texte pour la lisibilité |
| `billboard` | Orientation : `CENTER` (face au joueur), `VERTICAL`, `HORIZONTAL` ou `FIXED` |
| `line-width` | Largeur de ligne avant retour automatique (pixels) |
| `scale` | Taille par défaut des hologrammes |

### 📏 Redimensionnement des Hologrammes de Stats

Nouvelle sous-commande **`/hb statshologram size <taille>`** (ex: `/hb statshologram size 1.5`) qui règle la taille de l'hologramme de statistiques personnelles le plus proche (rayon de 5 blocs), avec autocomplétion des valeurs courantes (0.5 à 3.0). L'échelle est **persistée** et rechargée au démarrage.

### 🏅 Leaderboards par Format d'Équipe (1v1 / 2v2 / 3v3 / 4v4)

Les leaderboards top 10 ne se limitent plus aux classements globaux : quatre nouvelles catégories classent les joueurs **par victoires dans un format précis** :

```bash
/hb leaderboard 1v1    # Top 10 des victoires en 1v1
/hb leaderboard 2v2    # Top 10 des victoires en 2v2
/hb leaderboard 3v3    # Top 10 des victoires en 3v3
/hb leaderboard 4v4    # Top 10 des victoires en 4v4
```

Chacune fonctionne comme les catégories existantes (`remove`, `size <taille>`, rafraîchissement automatique toutes les 10 secondes).

### 🌐 Export Web Enrichi

L'export JSON du classement web (`LeaderboardExportServer`) inclut désormais, pour chaque joueur, le **détail de ses performances par format** : victoires, kills, parties jouées et K/D en 1v1, 2v2, 3v3 et 4v4 (classement « depuis toujours »).

### 📋 Résumé des changements
| Fichier | Changement |
|---------|------------|
| `HologramStyle` | **Nouveau** — apparence partagée et configurable de tous les hologrammes (fond, ombre, orientation, échelle…) |
| `StatsHologramManager` | Refonte : un seul `TextDisplay` par hologramme (au lieu de 8 ArmorStands), échelle individuelle persistée, rechargement du style |
| `CategoryLeaderboardManager` | Refonte : `TextDisplay` unique mis à jour en place (plus de clignotement) + 4 nouvelles catégories 1v1/2v2/3v3/4v4 |
| `HikaBrainCommand` | `/hb statshologram size <taille>` + catégories par format dans `/hb leaderboard` + autocomplétions |
| `LeaderboardExportServer` | Export JSON du détail par format (wins/kills/parties/K-D en 1v1 à 4v4) |
| `config.yml` | Nouvelle section `hologram-style` entièrement documentée |
| `plugin.yml` | Version → `1.0.29` + description mise à jour |

---

## 🆕 Mise à jour précédente (v1.0.28)

Cette version apporte deux améliorations qui rendent l'expérience de jeu plus fluide et la musique plus fidèle : un **rematch en un clic** en fin de partie, et un **moteur musical NBS plus précis**.

---

### 🔁 Rematch en un Clic en Fin de Partie

Jusqu'à présent, à la fin d'une partie, les joueurs devaient rouvrir manuellement le GUI `/arenas` pour relancer une partie. C'est désormais chose du passé : dès l'écran de fin, chaque joueur reçoit **deux boutons cliquables directement dans le chat**.

#### 🎮 Les deux boutons
- **▶ REJOUER** (vert, gras) — Relance immédiatement une nouvelle partie, **du même format** que celle qu'on vient de jouer (1v1, 2v2, 3v3…).
- **✖ QUITTER** (rouge, gras) — Garde le joueur au lobby, tout simplement.

#### 🧠 Logique de sélection intelligente
Le bouton « REJOUER » ne se contente pas de renvoyer vers une arène au hasard :
1. Il recherche en priorité une arène **du même format exact** (même `teamSize`).
2. Parmi celles-ci, il préfère une arène qui **a déjà des joueurs dedans** — pour rejouer vite, sans attendre seul dans un lobby vide.
3. S'il n'y a aucune arène du bon format, il retombe sur la recherche d'arène aléatoire classique (`findBestArenaForRandomJoin`) plutôt que de laisser le joueur sans rien.

#### 🛠️ Détails techniques
- Nouvelles sous-commandes : `/hb rematch <teamSize>` et `/hb rematchcancel`
- `GameManager#sendRematchPrompt(player, teamSize)` génère les deux `Component` Adventure avec `ClickEvent.runCommand` et `HoverEvent.showText`
- `ArenaManager#findBestArenaForRematch(teamSize)` filtre les arènes joignables par format, puis trie par présence de joueurs
- Le `teamSize` est calculé à la fin de la partie et passé dans le résumé de fin de jeu

---

### 🎵 Moteur Musical NBS Plus Fidèle

Le lecteur de fichiers `.nbs` (Note Block Studio) gagne en précision pour restituer fidèlement les morceaux composés avec des packs d'instruments étendus.

#### 🎹 Instruments personnalisés
- **Avant** : les instruments NBS au-delà de la plage vanilla (0–9) étaient purement **ignorés** — les notes disparaissaient du morceau.
- **Maintenant** : un instrument non reconnu retombe sur le **Harp** (bloc de notes par défaut). La note garde ainsi sa **vraie hauteur**, avec un timbre approximatif — bien plus fidèle qu'une note entièrement absente.

#### 🎚️ Transposition par octaves (au lieu de l'écrêtage)
Minecraft limite le pitch d'un son à une plage de **[0.5 ; 2.0]** (deux octaves), quelle que soit la méthode utilisée — c'est une limite du moteur audio lui-même, impossible à contourner.
- **Avant** : une note dont la hauteur NBS sortait de la plage jouable était **écrêtée** (clampée à 0 ou 24 clics) — toutes les notes extrêmes sonnaient alors identiques.
- **Maintenant** : la note est **transposée d'octaves entières** (±12 clics) jusqu'à retomber dans la plage jouable. Elle conserve ainsi sa **vraie note** (do, ré, mi…), juste sur une octave voisine, au lieu d'être écrasée vers l'extrême.
- Un **garde-fou final** (`Math.max(0.5, Math.min(2.0, pitch))`) sécurise le réglage fin du pitch au cas où il déborderait de justesse.

#### 📋 Résumé des changements
| Fichier | Changement |
|---------|------------|
| `HikaBrainCommand` | Nouvelles sous-commandes `/hb rematch` & `/hb rematchcancel` |
| `ArenaManager` | `findBestArenaForRematch(teamSize)` — recherche par format + priorité joueurs présents |
| `GameManager` | `sendRematchPrompt()` — boutons cliquables « ▶ REJOUER » / « ✖ QUITTER » en fin de partie |
| `MusicManager` | Instruments personnalisés → Harp (au lieu d'ignorer) + transposition par octaves (au lieu de l'écrêtage) |
| `plugin.yml` | Version → `1.0.28` + description mise à jour + sous-commandes rematch dans l'usage |

---

## 🆕 Mise à jour précédente (v1.0.27)

### 🎵 Lecture Aléatoire Intelligente de la Musique

Le mode « random » du jukebox (`/hb music random`) a été entièrement repensé pour offrir une expérience musicale plus variée et moins répétitive :

#### 🔄 Avant
- À chaque partie, une piste était tirée **totalement au hasard** parmi toutes celles disponibles
- Conséquence : il était possible de retomber sur **la même piste plusieurs fois d'affilée**

#### ✨ Maintenant
- Chaque arène dispose de sa propre **file d'attente mélangée** (shuffle queue)
- Le plugin **cycle dans un ordre aléatoire à travers TOUTES les pistes disponibles**, sans jamais répéter une piste tant que le cycle n'est pas entièrement épuisé
- Une fois toutes les pistes passées, un **nouveau mélange est tiré** automatiquement
- À la jonction entre deux cycles, la première piste du nouveau mélange ne peut pas être la même que la dernière du cycle précédent — **aucune répétition consécutive, jamais**

#### 🛠️ Détails techniques
- `MusicManager#pickRandomTrack(arenaName)` remplace l'ancien tirage purement aléatoire
- Mémoire par arène : `shuffleQueues` (file en cours) et `lastPlayedTrack` (dernière piste jouée)
- `buildShuffledQueue()` garantit l'absence de répétition à la jonction des cycles via un échange intelligent

---

## 🆕 Mise à jour précédente (v1.0.26)

### 📡 Hologrammes de Statistiques Personnelles

Cette mise à jour introduit un tout nouveau type d'hologramme, distinct du leaderboard classique : les **hologrammes de statistiques personnelles**. Contrairement au leaderboard (qui affiche le même top 10 à tout le monde), chaque hologramme affiche dynamiquement **les statistiques du joueur le plus proche** — chacun y voit donc *ses propres* données en s'approchant.

#### 🎯 Nouvelle commande `/hb statshologram`
- `/hb statshologram` — Pose un hologramme de statistiques personnelles à votre position
- `/hb statshologram remove` — Supprime l'hologramme le plus proche (rayon de 5 blocs)
- Vous pouvez en poser **plusieurs** (au spawn, dans le hub, à côté des arènes…)

#### 🖥️ Contenu affiché (par joueur)
Chaque hologramme se rafraîchit automatiquement toutes les 2 secondes et affiche :
- ✦ **Statistiques HikaBrain** ✦
- Pseudo du joueur détecté
- **Niveau X** · **Y points**
- ⚔ **K/D** (kills / morts)
- 🏆 Victoires · 🎮 Parties jouées
- ⏱ Temps de jeu total
- 📅 Classement du **jour** · 🗓 de la **semaine** · 🕰 **total** (à vie)

#### 🛠️ Détails techniques
- Nouvelles méthodes de classement : `LevelManager#getPointsRank` (classement à vie) et `MatchHistoryManager#getPointsRankForPeriod` (classement sur une période jour / semaine)
- Hologramme basé sur des ArmorStands invisibles, une ligne fixe par entrée — le texte est mis à jour en place (jamais respawné) pour rester fluide et **sans scintillement**
- Détection du joueur le plus proche dans un rayon de 5 blocs
- Persistance des positions dans `personal-holograms.yml`

---

### 💬 Amélioration du Chat Rapide d'Arène

Le système de **messages rapides** (blocs colorés à cliquer pendant l'attente entre deux points ou à la victoire) a été étendu :

- **Clic gauche ET clic droit** déclenchent désormais l'envoi du message (auparavant seul le clic gauche fonctionnait)
- L'anti-spam empêche toujours une rafale lors d'un clic maintenu
- Le clic est aussi neutralisé pour éviter de casser/placer un bloc ou d'ouvrir un conteneur pendant l'utilisation du bloc-message

---

### 🎨 Visibilité des Cosmétiques en Fin de Partie

Correction d'un chemin manquant dans le cycle de vie des cosmétiques : à la **fin normale d'une partie** (quand les joueurs sont téléportés hors de l'arène), les cosmétiques équipés redeviennent désormais **correctement visibles**. Auparavant, seuls `removePlayer()` et `removeSpectator()` réappliquaient les cosmétiques — la fin de partie passait à côté. Le `GameManager` réapplique maintenant explicitement les cosmétiques à ce moment.

---

### 📋 Résumé des changements
| Fichier | Changement |
|---------|------------|
| `StatsHologramManager` | Refonte complète : hologrammes de statistiques personnelles dynamiques |
| `HikaBrainPlugin` | Instanciation & cycle de vie du `StatsHologramManager` |
| `HikaBrainCommand` | Nouvelle sous-commande `/hb statshologram [remove]` + autocomplétion |
| `LevelManager` | `getPointsRank()` — rang à vie du joueur |
| `MatchHistoryManager` | `getPointsRankForPeriod()` — rang sur une période |
| `QuickChatListener` | Clic gauche **et** droit pour les messages rapides |
| `GameManager` | Réapplication des cosmétiques en fin de partie normale |
| `plugin.yml` | Version → `1.0.26` + description mise à jour |

---

## 🆕 Mise à jour précédente (v1.0.25)

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
- **Apparence des hologrammes** (section `hologram-style` : fond, ombre, orientation, échelle…)
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
- **Version**: 1.0.30

## 📄 Licence

MIT License
