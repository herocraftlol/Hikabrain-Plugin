# 🎮 Spide — Plugin PaperMC 1.21

> Un jeu d'équipes palpitant où les flèches ne blessent jamais mais détruisent des blocs.
> Le but : faire tomber les joueurs adverses en détruisant les blocs de leur base !

**Spide** est un minijeu compétitif et stratégique pour Paper 1.21 où deux équipes ou plus s'affrontent dans une arène. Les joueurs utilisent des arcs qui ne font aucun dégât direct mais détruisent les blocs — l'objectif étant de détruire la base adverse pour faire tomber les ennemis dans le vide !

---

## ✨ Fonctionnalités Principales

### 🎯 Gameplay Unique
- **Flèches destructrices** — Les flèches ne blessent pas les joueurs mais détruisent les blocs à l'impact
- **Chutes stratégiques** — Faites tomber vos adversaires en détruisant les blocs sous leurs pieds
- **Mode Perceur** — Option pour que les flèches traversent tous les blocs rencontrés
- **Rayon de destruction configurable** — Définissez la taille de la zone détruite à chaque impact

### ⚔️ Système d'Équipes
- **2 à 16 équipes** — Configurez le nombre d'équipes souhaité (minimum 2)
- **16 couleurs disponibles** — ORANGE, YELLOW, RED, BLACK + 12 teintes Minecraft supplémentaires
- **Spawns personnalisables** — Définissez précisément les points d'apparition de chaque équipe
- **Équilibrage automatique** — Les joueurs sont répartis automatiquement dans les équipes non complètes

### 🏆 Système de Parties
- **Compte à rebours de 10 secondes** — Démarre automatiquement quand le nombre minimum de joueurs est atteint
- **Manches multiples** — La partie se joue en plusieurs manches gagnantes
- **Régénération automatique de l'arène** — Chaque manche commence avec l'arène restaurée
- **Snapshot de blocs** — L'arène est restaurée à son état exact du dernier `/sp <map> posconfirm`

### 🎨 Interface & Feedback
- **GUI de sélection de map** — Interface intuitive avec double coffre (6x9)
  - 🟩 Vert (lime) = disponible — cliquez pour rejoindre
  - 🟧 Orange = en maintenance
  - 🟥 Rouge = en cours — cliquez pour spectater
- **Scoreboard en jeu** — Sidebar affichant les équipes, joueurs vivants et points
- **Tab-complete contextuel** — Auto-complétion intelligente pour toutes les commandes

### 🔫 Équipement
- **Arc enchanté Infinité** — Tir illimité garanti
- **Arc incassable & non-déplaçable** — L'arc ne peut pas être cassé, lâché ou échangé
- **Flèche verrouillée** — Ne peut pas être déplacée ou passée en main secondaire

---

## 📋 Commandes

| Commande | Description |
|----------|-------------|
| `/sp` | Menu cliquable listant toutes les fonctionnalités |
| `/sp help` | Aide complète et détaillée |
| `/sp gui` | Ouvre le GUI de sélection de map |
| `/sp list` | Liste toutes les maps avec leur état |
| `/sp join <map>` | Rejoindre une map directement (joueur ou spectateur) |
| `/sp leave` | Quitter la partie et retourner au hub |
| `/sp create <nom>` | Créer une nouvelle map |
| `/sp delete <nom>` | Supprimer une map |
| `/sp <map> info` | Afficher les informations détaillées d'une map |
| `/sp <map> pos1 / pos2 / posconfirm` | Définir la zone de jeu et capturer le snapshot |
| `/sp <map> equipe <nbEquipes> <joueursParEquipe>` | Configurer les équipes |
| `/sp <map> joueurs <min> <max>` | Configurer le nombre de joueurs |
| `/sp <map> spawn color <couleur>` | Définir un spawn pour une équipe |
| `/sp <map> lobby` | Définir le point d'attente |
| `/sp <map> point <n>` | Nombre de manches pour gagner |
| `/sp <map> rayon <n>` | Rayon de destruction des flèches |
| `/sp <map> rayon pierce` | Activer le mode perceur |
| `/sp <map> reset` | Forcer la réinitialisation d'une map |
| `/sp teamlist` | Voir l'ordre des couleurs d'équipe |
| `/sp teamlist add <couleur>` | Ajouter une couleur à la liste |
| `/sp hub` | Définir le point d'arrivée (hub) |

---

## 🎮 Déroulement d'une Partie

1. **Rejoindre** — Le joueur clique sur une map verte ou utilise `/sp join <map>`
2. **Attente au lobby** — Les joueurs attendent au point de lobby configuré
3. **Décompte** — Quand le minimum de joueurs est atteint, un compte à rebours de 10s commence
4. **Lancement** — L'arène est régénérée depuis le snapshot, chaque joueur reçoit son arc et est téléporté à son spawn
5. **Combat** — Détruisez les blocs sous vos ennemis pour les faire tomber et les éliminer
6. **Manche terminée** — Quand une seule équipe a des joueurs vivants, elle marque un point
7. **Nouvelle manche** — L'arène est régénérée et le combat recommence
8. **Victoire** — La première équipe à atteindre le nombre de points requis gagne !

---

## 🛠️ Compilation & Installation

### Prérequis
- **Java** : 21
- **API** : Paper 1.21
- **Build** : Maven

### Compiler

```bash
# Cloner le dépôt
git clone https://github.com/herocraftlol/Hikabrain-Plugin.git

# Entrer dans le dossier
cd Hikabrain-Plugin

# Compiler avec Maven
mvn clean package

# Le JAR se trouve dans target/Spide.jar
```

### Installer

1. Téléchargez le JAR depuis la [dernière release](https://github.com/herocraftlol/Hikabrain-Plugin/releases/latest)
2. Placez le fichier `Spide.jar` dans le dossier `plugins` de votre serveur Paper 1.21
3. Redémarrez le serveur
4. Configurez votre première map avec `/sp create <nom>`

---

## 🆕 Dernière Mise à Jour (v1.0.1)

### Nouveautés & Correctifs

- **`/sp leave`** — Quitte la partie ou le spectator et retourne au hub à tout moment
- **`/sp join <map>`** — Rejoindre une map directement par son nom (joueur ou spectateur)
- **Compte à rebours de 10s + joueurs min/max** — Configurable avec `/sp <map> joueurs <min> <max>`
- **Régénération de l'arène à chaque manche** — Snapshot restauré automatiquement
- **Spectateurs confinés** — Joueurs éliminés et spectateurs libres bloqués dans la zone
- **Arc et flèche verrouillés** — Incassables, non-déplaçables, non-jetables
- **Tab-complete contextuel** — Auto-complétion complète sur Tab
- **Tir infini réparé** — Arc enchanté Infinité pour un tir véritablement illimité
- **Scoreboard en jeu** — Sidebar avec équipes, joueurs vivants et points
- **Mort instantanée sous la map** — Élimination immédiate si le joueur passe sous la zone
- **Points et fin de partie fiabilisés** — Système debounced pour éviter les bugs d'élimination simultanée
- **Remise à zéro au démarrage** — Les maps potentiellement bloquées sont réinitialisées automatiquement

---

## 📝 Permissions

| Permission | Description | Défaut |
|------------|-------------|--------|
| `spide.admin` | Configuration des maps, création, suppression | OP |
| `spide.play` | Jouer à Spide | Tous |

---

## 📄 Licence

MIT License

---

**Développé pour PaperMC 1.21** | [Signaler un bug](https://github.com/herocraftlol/Hikabrain-Plugin/issues)
