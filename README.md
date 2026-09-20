# 🎮 HikaBrain + SpaceShip Plugin

![Version](https://img.shields.io/badge/version-1.1.0-blue)
![Paper](https://img.shields.io/badge/Paper-1.21.1-orange)
![Java](https://img.shields.io/badge/Java-21-red)
![Modes](https://img.shields.io/badge/modes-HikaBrain%20%2B%20SpaceShip-8A2BE2)

> Le plugin **tout-en-un** pour Paper 1.21.1 : **HikaBrain**, **SpaceShip** et la **Caméra de Découverte** réunis dans un seul JAR. Capture de zone par équipes, Domination multi-salles façon vaisseau spatial, tournois automatisés, boutique de cosmétiques, musique NBS, niveaux & perks, **leaderboards holographiques par format (1v1/2v2/3v3/4v4)**, **hologrammes de statistiques personnelles au style entièrement configurable et personnalisés pour chaque joueur**, **rematch en un clic**, et désormais **une visite cinématique automatique du lobby à la première connexion grâce à la Caméra de Découverte**.

**HikaBrain + SpaceShip** rassemble **deux modes de jeu compétitifs** dans un seul plugin, plus une **cinématique de découverte du lobby** qui accueille automatiquement chaque nouveau joueur. Côté **HikaBrain**, deux équipes (Rouge vs Bleu) s'affrontent pour contrôler une zone centrale, avec statistiques détaillées, classements holographiques (globaux **et par format d'équipe**), **hologrammes de statistiques personnelles** au style configurable (chaque joueur voit ses propres stats en s'approchant) construits sur les **TextDisplay natifs** — une seule entité, aucun scintillement —, système de tournoi intégré, véritable **boutique de cosmétiques** pour récompenser les joueurs fidèles, et un **bouton « Rejouer »** qui relance instantanément une partie du même format. Côté **SpaceShip**, le mode **Domination multi-zones** oppose deux équipes (Noir vs Blanc) dans un vaisseau spatial composé de **5, 7 ou 9 salles** alignées (Mid neutre au centre puis Bases de chaque côté) : capturez les salles adverses, défendez les vôtres, faites-les basculer sous votre couleur et empêchez l'ennemi de prendre le Mid. Enfin, la **Caméra de Découverte** embarque chaque nouveau joueur dans une cabine virtuelle (construite en `BlockDisplay`) qui survole le lobby selon un trajet configurable à l'arc parabolique — points de vue successifs, sons d'ambiance, particules — pour leur faire découvrir la map avant leur première partie.

---

## ✨ Fonctionnalités Principales

### 🎯 Gameplay
- **Système de Capture de Zone** - Combat stratégique pour le contrôle du territoire
- **Deux Équipes** - Rouge vs Bleu avec spawns distincts
- **Scoreboard en Temps Réel** - Scores, kills, deaths, K/D et victoires
- **Compte à Rebours Configurable** - Lobby avec freeze des joueurs
- **Items de Jeu** - Bouton forcer le démarrage 🔵 et quitter la partie 🔴
- **Rematch en un Clic** - À la fin de chaque partie, deux boutons cliquables dans le chat : « ▶ REJOUER » (relance une partie du même format 1v1/2v2/3v3…) et « ✖ QUITTER » (rester au lobby)
- **Blocs de Map Cassables Configurables** - Autorisez certains blocs de base de l'arène (erre, pierre…) à être cassés pendant les rounds (`/hb breakable`), tout en gardant la protection du reste de la map.

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
- **Titres (TAG) affichés dans le chat** — le titre équipé s'affiche à côté de votre pseudo à chaque message

### ⚔️ Système de Kit
- **Kits Configurables** - Équipement personnalisé par équipe
- **Attribution Automatique** - Distribution selon les paramètres

### 🎵 Système de Musique
- **Jukebox dans l'Arène** - Contrôle de la musique pendant les parties
- **Musique d'Ambiance** - Bandes sonores adaptatives selon l'état du jeu
- **Lecture Aléatoire Intelligente** - Mode « random » par arène : cycle mélangé de toutes les pistes sans répétition tant que le cycle n'est pas épuisé
- **Commande `/hb music`** - Gestion complète de la musique
- **Support des fichiers NBS** personnalisés

### 🚀 SpaceShip — Domination Multi-Zones (NOUVEAU)
- **Vaisseau à 5, 7 ou 9 salles** — Mid neutre au centre puis Bases (Base1 → Base4) de chaque côté, alignées le long d'un couloir central
- **Deux équipes : Noir vs Blanc** — chacune avec ses propres spawns dans chaque salle, ses propres zones-but à capturer et à défendre
- **Mode Domination** — chaque salle peut basculer sous le contrôle d'une équipe : restez-y pour grignoter du terrain, reprenez les salles perdues, bloquez l'adversaire au Mid
- **Confinement des spectateurs** — un rayon paramétrable (`spectator-confinement-radius`) les recale automatiquement dans la salle en cours s'ils s'éloignent du point de vue
- **Protection des spawns** — un rayon (`spawn-protection-radius`) interdit la pose de blocs autour de CHAQUE spawn, toutes salles / équipes confondues (fini les joueurs coincés dans leur base)
- **Tournois SpaceShip** — bracket à élimination directe avec `/sstournament`, chaque match est une vraie partie jouée sur une arène existante
- **Classement des plus longues parties** — hologramme dédié des records de durée (`/ss longestgames`)
- **Hologrammes de statistiques** — leaderboards par catégorie (`/ss leaderboard`) et records de parties (`/ss longestgames`) avec style configurable
- **Commande `/ss`** — gestion complète des arènes, configuration des zones, démarrage/arrêt, rejoindre, spectateur…
- **Données isolées** — `spaceship-arenas/` et fichiers préfixés `spaceship-` pour ne jamais se mélanger à HikaBrain

### 🎬 Caméra de Découverte (NOUVEAU)
- **Cinématique automatique au premier join** — chaque joueur qui se connecte pour la première fois embarque dans une cabine virtuelle qui survole le lobby selon un trajet préconfiguré (`discovery.enabled` dans `cabin-config.yml`)
- **Cabine en `BlockDisplay`** — petite plateforme construite avec des entités natives Minecraft, aucun bloc posé : invisible pour les autres joueurs et nettoyée parfaitement à la fin
- **Trajets à l'arc parabolique** — chaque route part d'un point A, arrive à un point B, avec une `arc-height` qui fait grimper la cabine au-dessus de la ligne droite avant de redescendre (effet « grand saut »)
- **Trajets multi-points** — option avancée : `/transport addpoint <route> <secondes>` ajoute des points de vue intermédiaires avec leur propre durée de transition et leur propre angle de caméra
- **Caméra suiveuse** — `lock-camera: true` oriente la caméra du passager dans le sens du déplacement (effet véhicule), ou la laisse libre s'il préfère garder le contrôle
- **Particules & sons d'ambiance** — `particle`, `sound-start`, `sound-loop` et `sound-end` configurables par trajet (CLOUD, END_ROD, ENTITY_ENDER_DRAGON_FLAP, BLOCK_AMETHYST_BLOCK_CHIME…)
- **Téléportation tick-par-tick** — le joueur suit la trajectoire de façon fluide et continue, pas de « véhicule » qui se désynchronise côté client
- **Commande `/transport`** — administration complète des trajets (`create`, `setstart`, `setend`, `setduration`, `setheight`, `setcabin`, `setcamera`, `addpoint`, `clearpoints`, `setpointduration`, `delete`, `reload`, `save`, `go`, `cancel`)
- **Jouée une seule fois par joueur** — la liste est persistée dans `discovery-seen.yml`, mais un admin peut la réinitialiser et `/transport go <route>` lance manuellement n'importe quel trajet existant

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
| `/hb breakable <arène> <add\|remove\|list\|clear> [matériau]` | Autoriser ou interdire la casse de certains blocs de base de l'arène (ex : terre, pierre) |
| `/hb leaderboard <victoires\|kills\|kd\|parties\|1v1\|2v2\|3v3\|4v4>` | Poser un leaderboard top 10 à votre position |
| `/hb leaderboard <catégorie> [remove\|size <taille>]` | Supprimer ou redimensionner le leaderboard le plus proche |
| `/hb statshologram` | Poser un hologramme de statistiques personnelles |
| `/hb statshologram remove` | Supprimer l'hologramme le plus proche |
| `/hb statshologram size <taille>` | Redimensionner l'hologramme de stats le plus proche (ex: 1.5) |
| `/hb rematch <teamSize>` | Rejoindre une nouvelle partie du même format (déclenché par le bouton « ▶ REJOUER ») |
| `/hb rematchcancel` | Rester au lobby (déclenché par le bouton « ✖ QUITTER ») |
| `/arenas` | Ouvrir le GUI de sélection d'arène |
| `/cosmetics` | Ouvrir la boutique de cosmétiques |
| `/tournament` | Système de tournoi HikaBrain (bracket à élimination directe) |
| `/ss` | **NOUVEAU** — Commande principale du mode SpaceShip (création d'arène multi-zones, configuration des salles / spawns / zones-but, démarrage, statistiques, leaderboards, longestgames…) |
| `/ssarenas` | **NOUVEAU** — Ouvre le GUI de sélection d'arène SpaceShip |
| `/sstournament` | **NOUVEAU** — Gestion des tournois SpaceShip (bracket à élimination directe), alias `/sstourney`, `/sst` |
| `/transport` | **NOUVEAU** — Configure et lance les trajets de la Caméra de Découverte (`list`, `go`, `cancel`, `create`, `setstart`, `setend`, `addpoint`, `clearpoints`, `setpointduration`, `setduration`, `setheight`, `setcabin`, `setcamera`, `delete`, `reload`, `save`) |

## Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `hikabrain.admin` | Administration du jeu, hologrammes, setup arènes, tournoi | OP |
| `hikabrain.play` | Jouer au HikaBrain | Tous |
| `hikabrain.tournament.join` | S'inscrire à un tournoi | Tous |
| `hikabrain.cosmetics.use` | Accéder à la boutique de cosmétiques | Tous |
| `spaceship.admin` | Administration du jeu SpaceShip | OP |
| `spaceship.play` | Jouer à SpaceShip | Tous |
| `spaceship.tournament.admin` | Créer / démarrer / annuler les tournois SpaceShip | OP |
| `transport.use` | Utiliser les trajets et la visite du lobby | Tous |
| `transport.admin` | Configurer les trajets et la cinématique | OP |

## 🆕 Dernière Mise à Jour (v1.1.0)

Cette version majeure **fusionne HikaBrain, SpaceShip et la Caméra de Découverte en un seul plugin** et apporte une expérience d'accueil cinématographique au lobby. Un nouveau JAR unique suffit désormais : retirez vos anciens `HikaBrain.jar` / `SpaceShip.jar` et installez **uniquement** `HikaBrain-SpaceShip.jar` pour éviter les conflits de commandes et de listeners. Chaque sous-système conserve ses propres fichiers de données et de configuration — rien n'est mélangé.

### 🚀 SpaceShip intégré au plugin

Le mode **SpaceShip** (Domination multi-zones Noir vs Blanc) est maintenant entièrement intégré au JAR principal. Il disposait jusqu'ici de son propre plugin ; il s'installe désormais automatiquement au démarrage d'HikaBrain et partage le même cycle de vie (commandes, listeners, sauvegardes).

- **Vaisseau à 5, 7 ou 9 salles** — Mid neutre au centre puis Bases (Base1 → Base4) alignées de chaque côté ; chaque salle a ses propres spawns par équipe et ses propres zones-but
- **Domination** — chaque salle peut basculer sous le contrôle d'une équipe ; restez-y pour grignoter du terrain, reprenez les salles perdues, bloquez l'adversaire au Mid
- **Tournois SpaceShip** — `/sstournament` (alias `/sstourney`, `/sst`) gère un bracket à élimination directe où chaque match est une vraie partie jouée sur une arène existante
- **Protection des spawns** — un rayon `spawn-protection-radius` autour de CHAQUE spawn (toutes salles / équipes confondues) empêche de boucher ou piéger un joueur (le sien ou celui de l'adversaire)
- **Confinement des spectateurs** — un rayon `spectator-confinement-radius` les recale automatiquement dans la salle en cours s'ils s'éloignent du point de vue
- **Classement des plus longues parties** — `/ss longestgames` pose un hologramme dédié des records de durée, mis à jour en place comme les autres leaderboards
- **Données isolées** — `spaceship-arenas/` et fichiers préfixés `spaceship-` (`spaceship-config.yml`, `spaceship-stats.yml`, etc.) pour ne jamais empiéter sur les données HikaBrain

### 🎬 Caméra de Découverte (CabinTransport)

À la première connexion d'un joueur, le plugin déclenche automatiquement une **cinématique d'accueil** qui embarque le joueur dans une cabine virtuelle survolant le lobby. Plus qu'une scène passive : c'est un **trajet paramétrable** que l'administrateur dessine et configure au préalable.

- **Déclenchement automatique au premier join** — chaque joueur qui se connecte pour la première fois voit la cabine l'embarquer et survoler le lobby selon le trajet `discovery.route` (par défaut `lobby-tour`)
- **Cabine en `BlockDisplay`** — petite plateforme construite avec des entités natives Minecraft, aucun bloc posé : invisible pour les autres joueurs et nettoyée parfaitement à la fin du trajet
- **Trajets à l'arc parabolique** — chaque route part d'un point A et arrive à un point B, avec une `arc-height` qui fait grimper la cabine au-dessus de la ligne droite avant de redescendre (effet « grand saut »)
- **Trajets multi-points** — option avancée : `/transport addpoint <route> <secondes>` ajoute des points de vue intermédiaires avec leur propre durée de transition et leur propre angle de caméra (yaw / pitch indépendants par point)
- **Caméra suiveuse** — `lock-camera: true` oriente la caméra du passager dans le sens du déplacement (effet véhicule), ou la laisse libre s'il préfère garder le contrôle
- **Particules & sons d'ambiance** — `particle`, `sound-start`, `sound-loop` et `sound-end` configurables par trajet (CLOUD, END_ROD, ENTITY_ENDER_DRAGON_FLAP, BLOCK_AMETHYST_BLOCK_CHIME…)
- **Téléportation tick-par-tick** — le joueur suit la trajectoire de façon fluide et continue, pas de « véhicule » qui se désynchronise côté client
- **Jouée une seule fois par joueur** — la liste des joueurs ayant déjà vu la découverte est persistée dans `discovery-seen.yml` ; un admin peut `/transport go <route>` pour relancer manuellement n'importe quel trajet

### 🛠️ Détails techniques
| Fichier | Changement |
|---------|------------|
| `pom.xml` | ArtifactId `HikaBrain` → `HikaBrain-SpaceShip`, version → **1.1.0-discovery-camera** |
| `plugin.yml` | Description enrichie (HikaBrain + SpaceShip + Découverte), version → **1.1.0**, nouvelles commandes `ss` / `ssarenas` / `sstournament` / `transport`, nouvelles permissions `spaceship.*` et `transport.*` |
| `HikaBrainPlugin` | Nouvelle classe principale **unique** : initialise HikaBrain + SpaceShip + CabinTransport, gère les cycles de vie (sauvegarde / arrêt) de chacun |
| `com.hikabrain.plugin.*` | Code HikaBrain conservé (cosmétiques, leaderboards, statistiques, tournois, hologrammes, musique NBS, kits, rematch, breakable…) |
| `com.spaceship.plugin.*` | **Nouveau sous-package** — code SpaceShip intégré (arènes multi-zones, scoreboard, statistiques, historique, hologrammes, tournois, listeners) |
| `fr.cabintransport.*` | **Nouveau sous-package** — code CabinTransport (modèles `Route` / `CameraPoint` / `CabinPart` / `PointRef`, `RouteManager`, `JourneyManager`, `DiscoveryManager`, `TransportCommand`, `JourneyListener`) |
| `resources/spaceship-config.yml` | **Nouveau** — configuration SpaceShip (min/max joueurs, durée des comptes à rebours, scoreboard, messages) |
| `resources/cabin-config.yml` | **Nouveau** — configuration CabinTransport (section `discovery`, liste des `routes`, `messages`) |
| `resources/plugin.yml` | Mise à jour des commandes et permissions (intégration des sous-systèmes) |
| **OBSOLÈTE** `SpaceShipPlugin.java` | **Supprimé** — SpaceShip n'est plus un plugin séparé, il est démarré par `HikaBrainPlugin` |

---

## 🆕 Mise à jour précédente (v1.0.33)

Cette version enrichit le gameplay d'arène : les **blocs de base de la map** (ceux déjà présents lors de la configuration) peuvent désormais être **autorisés à être cassés** arène par arène, la **pioche du kit** creuse enfin **vite** (Efficacité II), et le **respawn instantané** s'applique automatiquement à **tous les mondes**, y compris ceux chargés après le démarrage du plugin.



---

### ✨ Nouveautés de la v1.0.33

#### ⛏️ Blocs de base cassables configurables (`/hb breakable`)
- Jusqu'ici, **aucun bloc de base de la map** ne pouvait être cassé dans la zone de jeu, même pour creuser sous ses pieds dans de la terre ou de la pierre. Nouveau : un administrateur peut **autoriser certains matériaux** arène par arène.
- **`/hb breakable <arène> add <matériau>`** — autorise la casse de ce matériau (ex : `DIRT` pour pouvoir creuser sous ses pieds) ; **`remove`** pour retirer l'autorisation ; **`list`** pour voir les matériaux autorisés ; **`clear`** pour tout retirer.
- La **protection reste active** pour tout le reste : seuls les matériaux explicitement autorisés deviennent cassables, et ils **réapparaissent comme n'importe quel autre bloc de la map** à chaque round reset / début de partie (restauration complète du snapshot).

#### 🧱 Pioche du kit Efficacité II
- La pioche en fer du kit est toujours **incassable**, mais elle est désormais enchantée **Efficacité II** sur toutes les arènes — elle creuse beaucoup plus vite, ce qui rend notamment les blocs de base autorisés (erre, pierre…) réellement exploitables en jeu.



#### 🌍 Respawn instantané sur tous les mondes
- Le plugin appliquait le `DO_IMMEDIATE_RESPAWN` **uniquement aux mondes déjà chargés** au démarrage : un monde chargé plus tard (arène dans un monde à part, par exemple) pouvait redemander un clic manuel sur « Respawn » après chaque mort. C'est corrigé : un nouveau listener applique la règle **à tout monde chargé ensuite**, automatiquement.



#### 🛠️ Détails techniques
| Fichier | Changement |
|---------|------------|
| `HikaBrainCommand` | Nouvelle commande `/hb breakable <add\|remove\|list\|clear>` |
| `Arena` | Nouvelle liste `breakableMaterials` (matériaux de blocs de base autorisés à être cassés) + getters/add/remove |
| `ArenaProtectionListener` | La protection des blocs de base est ignorée pour les matériaux autorisés |
| `KitManager` | Pioche du kit : incassable + **Efficacité II** (nouvelle `makeEfficiencyPickaxe()`) |
| `WorldLoadListener` | **Nouveau** — applique le `DO_IMMEDIATE_RESPAWN` à tout monde chargé après le démarrage du plugin |
| `HikaBrainPlugin` | Enregistre le nouveau `WorldLoadListener` |
| `plugin.yml` / `pom.xml` | Version → **1.0.33** |

---

## 🆕 Dernière Mise à Jour (v1.0.32)

Cette version donne enfin vie aux **cosmétiques de type « TAG »** : le titre acheté et équipé s'affiche désormais **à côté de votre pseudo dans le chat**. Il s'agissait d'un bug : le titre existait bien en boutique (achat et équipement fonctionnaient), mais il n'était **jamais réellement affiché** nulle part en jeu. C'est maintenant corrigé.

---

### ✨ Nouveautés de la v1.0.32

#### 🏷️ Titres cosmétiques affichés dans le chat
- Le **titre (TAG)** que vous équipez dans la boutique (`/cosmetics`) s'affiche désormais **à côté de votre pseudo à chaque message** envoyé dans le chat, pour tous les destinataires.
- Comme pour tous les cosmétiques, il ne s'affiche **que quand il est actif pour vous** — donc jamais pendant une partie HikaBrain (il est automatiquement masqué en arène).
- Les titres supportent les **codes couleurs** (`&c`, `&l`…) grâce à la nouvelle conversion Adventure.

#### ℹ️ Descriptions d'effet dans la boutique
- Chaque cosmétique de la boutique affiche maintenant une **description claire de son effet** dans son lore — vous savez exactement ce que vous achetez avant de dépenser vos points (ex : « Fait tourbillonner un halo de particules au-dessus de ta tête », « Affiche ce titre à côté de ton pseudo »…).
- Le texte est automatiquement **découpé en plusieurs lignes** pour rester lisible, sans couper un mot.

#### 🛠️ Détails techniques
| Fichier | Changement |
|---------|------------|
| `CosmeticChatListener` | **Nouveau** — affiche le titre TAG équipé après le pseudo de l'expéditeur dans le chat (`AsyncChatEvent` + rendu Adventure) |
| `Cosmetic` | Nouvelle méthode `getEffectDescription()` : décrit en une phrase l'effet de chaque cosmétique |
| `CosmeticShopGUI` | Affiche la description d'effet dans le lore de chaque item + `wrapLore()` pour un découpage lisible |
| `MessageUtil` | Nouvelle conversion legacy → `Component` Adventure (`formatComponent`) |
| `HikaBrainPlugin` | Enregistre le nouveau listener `CosmeticChatListener` |
| `plugin.yml` / `pom.xml` | Version → **1.0.32** |

---

## 🆕 Mise à jour précédente (v1.0.31)

Cette version corrige définitivement le **clignotement des hologrammes de statistiques personnelles** : certains joueurs voyaient leur hologramme disparaître puis réapparaître en boucle, surtout lorsqu'ils se tenaient à la limite du rayon de détection.

---

### ✨ Hologrammes personnels désormais stables et plus réactifs

#### 🚫 Avant
- **Rayon de détection très court (5 blocs)** : l'hologramme personnel n'apparaissait qu'au tout dernier moment
- Un joueur **pile à la limite du rayon** voyait son hologramme **supprimé puis recréé à chaque micro-mouvement** (léger regard, tremblement de position…) — un va-et-vient permanent qui donnait l'impression d'un clignotement constant
- Si le client Minecraft cessait de « suivre » l'entité (limite de distance de rendu, changement de chunk…), l'hologramme pouvait **rester invisible** jusqu'à sa recréation complète

#### ✨ Maintenant
- **Rayon de détection porté à 20 blocs** : l'hologramme personnel apparaît bien plus tôt quand un joueur s'approche
- **Hystérésis anti-va-et-vient** : l'hologramme n'est retiré qu'au-delà d'un **rayon de sortie plus large (23 blocs)** que le rayon de détection — cette marge de 3 blocs empêche tout cycle supprimer/recréer pour un joueur qui bouge à la limite
- **Visibilité ré-affirmée en continu** : à chaque cycle de rafraîchissement (toutes les 2 secondes), le plugin redit au client « cet hologramme est visible pour toi » (`Player#showEntity`, sans coût si déjà visible) — fini les disparitions après un changement de chunk ou un dépassement de distance de rendu
- **Suppression propre** : l'entité personnelle n'est retirée qu'en cas de **déconnexion** ou de **véritable sortie** du rayon de sortie

#### 🛠️ Détails techniques
| Fichier | Changement |
|---------|------------|
| `StatsHologramManager` | `DETECTION_RADIUS` 5.0 → 20.0, nouveau `REMOVAL_RADIUS` (détection + 3 blocs) avec hystérésis, ré-affirmation de la visibilité à chaque rafraîchissement, suppression uniquement sur déconnexion ou sortie réelle |

---

## 🆕 Mise à jour précédente (v1.0.30)

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

1. Téléchargez le JAR `HikaBrain-SpaceShip-1.1.0-discovery-camera.jar` depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. **Supprimez** tout ancien JAR HikaBrain ou SpaceShip du dossier `plugins/` pour éviter les conflits de commandes et de listeners
3. Placez **uniquement** `HikaBrain-SpaceShip-1.1.0-discovery-camera.jar` dans le dossier `plugins/` de votre serveur Paper 1.21.1
4. Redémarrez le serveur
5. Configurez vos arènes HikaBrain avec `/hb create <nom>`, vos arènes SpaceShip avec `/ss create <nom>`, et votre trajet de découverte avec `/transport create lobby-tour` (puis `setstart`, `setend`, `setduration`)

## ⚙️ Configuration

Trois fichiers de configuration cohabitent dans `plugins/HikaBrain-SpaceShip/` :

- **`config.yml`** — HikaBrain
  - Nombre de joueurs minimum/maximum par arène
  - Durée des comptes à rebours (lobby et round)
  - Points nécessaires pour gagner
  - Apparence complète du scoreboard (titre, lignes, couleurs)
  - **Apparence des hologrammes** (section `hologram-style` : fond, ombre, orientation, échelle…)
  - Messages personnalisés avec préfixe
- **`spaceship-config.yml`** — SpaceShip
  - Min/max joueurs, durée des comptes à rebours (`lobby-countdown-min-reached`, `lobby-countdown-fast`, `round-reset-countdown`)
  - Protection des spawns (`spawn-protection-radius`)
  - Confinement des spectateurs (`spectator-confinement-radius`)
  - Scoreboard dédié, préfixes & messages
- **`cabin-config.yml`** — Caméra de Découverte
  - Section `discovery` (`enabled`, `route`, `message`)
  - Liste des `routes` (start / end / durée / arc-height / particle / sons / cabine / lock-camera)
  - Points de caméra supplémentaires (mode multi-points)
  - Messages dédiés

## 🛠️ Compilation

- **Java** : 21
- **API** : Paper 1.21.1
- **Build** : Maven

```bash
# Cloner le dépôt
git clone https://github.com/herocraftlol/Hikabrain-Plugin.git

# Compiler
mvn clean package -DskipTests

# Le JAR sera dans target/HikaBrain-SpaceShip-1.1.0-discovery-camera.jar
```

## 📝 Auteur

- **Développeur**: herocraftlol
- **Version** : 1.1.0 (build `1.1.0-discovery-camera`)

## 📄 Licence

MIT License
