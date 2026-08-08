package com.hikabrain.plugin.cosmetics;

import com.hikabrain.plugin.HikaBrainPlugin;
import com.hikabrain.plugin.levels.LevelManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère la possession, l'équipement, l'achat et l'affichage des cosmétiques.
 *
 * Les cosmétiques ne sont JAMAIS visibles pendant qu'un joueur est engagé avec une
 * arène HikaBrain, sous quelque forme que ce soit (lobby d'attente, partie en cours,
 * spectateur) : voir {@link #applyCosmetics} / {@link #removeCosmetics}, appelés
 * respectivement quand le joueur QUITTE et REJOINT une arène (voir GameManager). Ils ne
 * sont donc actifs que dans le "vrai" lobby/hub du serveur, en dehors de tout ça.
 *
 * L'achat consomme le solde DÉPENSABLE du joueur (voir LevelManager#spendBalance),
 * distinct du total de points qui détermine le niveau : dépenser ne fait donc jamais
 * baisser le niveau ni le classement d'un joueur.
 */
public class CosmeticManager {

    private final HikaBrainPlugin plugin;
    private final File dataFile;

    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, Map<CosmeticCategory, String>> equipped = new HashMap<>();

    // État des effets actuellement APPLIQUÉS (pour pouvoir les retirer proprement)
    private final Map<UUID, ItemStack> previousHelmet = new HashMap<>(); // null-safe : clé présente = on a modifié le casque
    private final Set<UUID> helmetWasModified = new HashSet<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> trailTasks = new HashMap<>();
    /** Joueurs pour qui les cosmétiques sont actuellement actifs (pour éviter les doublons). */
    private final Set<UUID> active = new HashSet<>();

    public CosmeticManager(HikaBrainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        load();
    }

    // ── Persistance ────────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                owned.put(uuid, new HashSet<>(playerSection.getStringList("owned")));

                Map<CosmeticCategory, String> equipMap = new HashMap<>();
                ConfigurationSection equippedSection = playerSection.getConfigurationSection("equipped");
                if (equippedSection != null) {
                    for (String categoryName : equippedSection.getKeys(false)) {
                        try {
                            CosmeticCategory category = CosmeticCategory.valueOf(categoryName);
                            equipMap.put(category, equippedSection.getString(categoryName));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                equipped.put(uuid, equipMap);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (UUID uuid : owned.keySet()) {
            String base = "players." + uuid;
            config.set(base + ".owned", new java.util.ArrayList<>(owned.getOrDefault(uuid, Set.of())));
            Map<CosmeticCategory, String> equipMap = equipped.getOrDefault(uuid, Map.of());
            for (Map.Entry<CosmeticCategory, String> entry : equipMap.entrySet()) {
                config.set(base + ".equipped." + entry.getKey().name(), entry.getValue());
            }
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder cosmetics.yml : " + e.getMessage());
        }
    }

    // ── Possession / équipement ──────────────────────────────────────────────────

    public boolean isOwned(UUID uuid, String cosmeticId) {
        return owned.getOrDefault(uuid, Set.of()).contains(cosmeticId);
    }

    public Cosmetic getEquipped(UUID uuid, CosmeticCategory category) {
        String id = equipped.getOrDefault(uuid, Map.of()).get(category);
        return id != null ? CosmeticRegistry.get(id) : null;
    }

    public enum PurchaseResult {
        SUCCESS, ALREADY_OWNED, LEVEL_TOO_LOW, INSUFFICIENT_FUNDS
    }

    /**
     * Tente d'acheter un cosmétique pour ce joueur. Débite son solde dépensable en cas de
     * succès (voir LevelManager), sans jamais toucher à son total de points/niveau.
     */
    public PurchaseResult purchase(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        if (isOwned(uuid, cosmetic.getId())) return PurchaseResult.ALREADY_OWNED;

        LevelManager levelManager = plugin.getLevelManager();
        if (levelManager.getLevel(uuid) < cosmetic.getUnlockLevel()) return PurchaseResult.LEVEL_TOO_LOW;
        if (!levelManager.spendBalance(uuid, player.getName(), cosmetic.getPrice())) return PurchaseResult.INSUFFICIENT_FUNDS;

        owned.computeIfAbsent(uuid, k -> new HashSet<>()).add(cosmetic.getId());
        save();
        return PurchaseResult.SUCCESS;
    }

    /**
     * Équipe un cosmétique déjà possédé (retire automatiquement l'ancien de la même
     * catégorie). Si le joueur est actuellement en lobby, l'effet est appliqué
     * immédiatement. Renvoie false si le cosmétique n'est pas possédé.
     */
    public boolean equip(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        if (!isOwned(uuid, cosmetic.getId())) return false;

        equipped.computeIfAbsent(uuid, k -> new HashMap<>()).put(cosmetic.getCategory(), cosmetic.getId());
        save();

        if (active.contains(uuid)) {
            // Ré-applique immédiatement pour refléter le changement sans attendre un
            // aller-retour hors de l'arène.
            removeCategoryEffect(player, cosmetic.getCategory());
            applyCategoryEffect(player, cosmetic);
        }
        return true;
    }

    /** Déséquipe le cosmétique actuellement équipé dans cette catégorie, s'il y en a un. */
    public void unequip(Player player, CosmeticCategory category) {
        UUID uuid = player.getUniqueId();
        Map<CosmeticCategory, String> map = equipped.get(uuid);
        if (map == null || map.remove(category) == null) return;
        save();

        if (active.contains(uuid)) {
            removeCategoryEffect(player, category);
        }
    }

    // ── Application des effets (UNIQUEMENT hors arène, voir GameManager) ────────

    /**
     * Applique tous les cosmétiques équipés par ce joueur. À appeler UNIQUEMENT quand le
     * joueur est dans le "vrai" lobby/hub (jamais en arène, sous aucune forme).
     */
    public void applyCosmetics(Player player) {
        UUID uuid = player.getUniqueId();
        if (active.contains(uuid)) return; // déjà actif, ne pas dupliquer les tâches
        active.add(uuid);

        for (CosmeticCategory category : CosmeticCategory.values()) {
            Cosmetic cosmetic = getEquipped(uuid, category);
            if (cosmetic != null) applyCategoryEffect(player, cosmetic);
        }
    }

    /**
     * Retire tous les effets cosmétiques actifs de ce joueur (casque rendu à son état
     * précédent, particules/traînées arrêtées). À appeler dès que le joueur entre dans
     * une arène HikaBrain, sous quelque forme que ce soit.
     */
    public void removeCosmetics(Player player) {
        UUID uuid = player.getUniqueId();
        if (!active.remove(uuid)) return;

        for (CosmeticCategory category : CosmeticCategory.values()) {
            removeCategoryEffect(player, category);
        }
    }

    private void applyCategoryEffect(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        switch (cosmetic.getCategory()) {
            case HAT -> {
                if (!helmetWasModified.contains(uuid)) {
                    previousHelmet.put(uuid, player.getInventory().getHelmet());
                    helmetWasModified.add(uuid);
                }
                player.getInventory().setHelmet(buildHatItem(cosmetic));
            }
            case PARTICLE -> {
                BukkitTask task = startAuraTask(player, cosmetic);
                particleTasks.put(uuid, task);
            }
            case TRAIL -> {
                BukkitTask task = startTrailTask(player, cosmetic);
                trailTasks.put(uuid, task);
            }
            case ENTRANCE -> playEntranceEffect(player, cosmetic);
            case TAG -> {
                // Le tag est lu directement par ScoreboardManager/le prefixe de chat via
                // getEquipped(uuid, TAG) : rien à "démarrer" ici, c'est déjà pris en compte
                // dès qu'un cosmétique TAG est équipé et le joueur actif (voir getActiveTag).
            }
        }
    }

    private void removeCategoryEffect(Player player, CosmeticCategory category) {
        UUID uuid = player.getUniqueId();
        switch (category) {
            case HAT -> {
                if (helmetWasModified.remove(uuid)) {
                    player.getInventory().setHelmet(previousHelmet.remove(uuid));
                }
            }
            case PARTICLE -> {
                BukkitTask task = particleTasks.remove(uuid);
                if (task != null) task.cancel();
            }
            case TRAIL -> {
                BukkitTask task = trailTasks.remove(uuid);
                if (task != null) task.cancel();
            }
            default -> {
                // ENTRANCE (ponctuel, rien à retirer) et TAG (rien à démarrer) : no-op
            }
        }
    }

    /**
     * Le tag actuellement affiché pour ce joueur (uniquement s'il est bien "actif",
     * c'est-à-dire hors de toute arène) — utilisé par le scoreboard/chat pour l'afficher.
     * Renvoie null si aucun tag équipé ou si le joueur est en arène.
     */
    public String getActiveTag(UUID uuid) {
        if (!active.contains(uuid)) return null;
        Cosmetic tag = getEquipped(uuid, CosmeticCategory.TAG);
        return tag != null ? tag.getTagText() : null;
    }

    // ── Construction des items / effets concrets ─────────────────────────────────

    private ItemStack buildHatItem(Cosmetic cosmetic) {
        ItemStack item = new ItemStack(cosmetic.getIconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(com.hikabrain.plugin.util.MessageUtil.format(cosmetic.getDisplayName()));
            item.setItemMeta(meta);
        }
        if (cosmetic.getLeatherColor() != null && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(cosmetic.getLeatherColor().getColor());
            item.setItemMeta(leatherMeta);
        }
        return item;
    }

    private BukkitTask startAuraTask(Player player, Cosmetic cosmetic) {
        int[] tick = {0};
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            double angle = tick[0] * 0.3;
            Location center = player.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                double dx = Math.cos(a) * 0.7;
                double dz = Math.sin(a) * 0.7;
                Location loc = center.clone().add(dx, Math.sin(angle * 2) * 0.2, dz);
                spawnCosmeticParticle(player, cosmetic, loc);
            }
            tick[0]++;
        }, 0L, 2L);
    }

    private BukkitTask startTrailTask(Player player, Cosmetic cosmetic) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            Location loc = player.getLocation().add(0, 0.1, 0);
            spawnCosmeticParticle(player, cosmetic, loc);
        }, 0L, 3L);
    }

    private void playEntranceEffect(Player player, Cosmetic cosmetic) {
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || tick[0] >= 30) { // 1.5 seconde
                task.cancel();
                return;
            }
            double angle = tick[0] * 0.5;
            Location center = player.getLocation().add(0, 1.0, 0);
            for (int i = 0; i < 3; i++) {
                double a = angle + i * (2 * Math.PI / 3);
                double radius = 0.5 + (tick[0] * 0.04);
                Location loc = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                spawnCosmeticParticle(player, cosmetic, loc);
            }
            tick[0]++;
        }, 0L, 1L);
    }

    private void spawnCosmeticParticle(Player player, Cosmetic cosmetic, Location loc) {
        Particle particle = cosmetic.getParticle();
        if (particle == null || loc.getWorld() == null) return;

        if (cosmetic.getParticleColor() != null) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(cosmetic.getParticleColor(), 1.2f);
            loc.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0, dustOptions);
        } else {
            loc.getWorld().spawnParticle(particle, loc, 1, 0.02, 0.02, 0.02, 0.01);
        }
    }
}
