package io.github.tst_038.minigami.paper.session;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * A player's state from before they joined a match. Holds plain values only, so it can be written
 * to disk and restored after a restart or crash.
 *
 * @param items inventory contents (storage, armor and off hand), Base64 of Paper's item bytes
 */
public record PlayerSnapshot(
        String items,
        SavedLocation location,
        String gameMode,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        int totalExperience,
        boolean allowFlight,
        boolean flying,
        int fireTicks,
        int freezeTicks,
        int remainingAir,
        float fallDistance,
        List<SavedEffect> effects,
        List<SavedAttribute> attributes) {

    private static final int FORMAT_VERSION = 1;

    public PlayerSnapshot {
        Objects.requireNonNull(items);
        Objects.requireNonNull(location);
        Objects.requireNonNull(gameMode);
        effects = List.copyOf(effects);
        attributes = List.copyOf(attributes);
    }

    public record SavedLocation(UUID world, double x, double y, double z, float yaw, float pitch) {

        /** The location, or the main world's spawn if its world no longer exists. */
        public Location resolve(Server server) {
            var found = server.getWorld(world);
            if (found == null) {
                return server.getWorlds().getFirst().getSpawnLocation();
            }
            return new Location(found, x, y, z, yaw, pitch);
        }
    }

    /** The same state, but with the player to be put back somewhere else. */
    public PlayerSnapshot withLocation(SavedLocation moved) {
        return new PlayerSnapshot(items, moved, gameMode, health, foodLevel, saturation, exhaustion, level, exp, totalExperience,
                allowFlight, flying, fireTicks, freezeTicks, remainingAir, fallDistance, effects, attributes);
    }

    public record SavedEffect(String type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
    }

    /** @param modifiers keys of the modifiers the attribute had */
    public record SavedAttribute(String key, double base, List<String> modifiers) {

        public SavedAttribute {
            modifiers = List.copyOf(modifiers);
        }
    }

    public static PlayerSnapshot capture(Player player) {
        var location = player.getLocation();
        var effects = player.getActivePotionEffects().stream()
                .map(effect -> new SavedEffect(effect.getType().getKey().asString(), effect.getDuration(), effect.getAmplifier(),
                        effect.isAmbient(), effect.hasParticles(), effect.hasIcon()))
                .toList();
        var attributes = new ArrayList<SavedAttribute>();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            var instance = player.getAttribute(attribute);
            if (instance != null) {
                var modifiers = instance.getModifiers().stream().map(modifier -> modifier.getKey().asString()).toList();
                attributes.add(new SavedAttribute(attribute.getKey().asString(), instance.getBaseValue(), modifiers));
            }
        }
        return new PlayerSnapshot(
                Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(player.getInventory().getContents())),
                new SavedLocation(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch()),
                player.getGameMode().name(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getFireTicks(),
                player.getFreezeTicks(),
                player.getRemainingAir(),
                player.getFallDistance(),
                effects,
                attributes);
    }

    /**
     * Puts the saved state back. Health is clamped to the current max health, because max health from
     * equipment only comes back on the next tick; {@link PlayerSessions} tops it up then.
     */
    public void restore(Player player) {
        restoreAttributes(player);
        player.setGameMode(GameMode.valueOf(gameMode));
        player.getInventory().setContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(items)));
        player.teleport(location.resolve(player.getServer()));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setTotalExperience(totalExperience);
        player.setLevel(level);
        player.setExp(exp);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setFireTicks(fireTicks);
        player.setFreezeTicks(freezeTicks);
        player.setRemainingAir(remainingAir);
        player.setFallDistance(fallDistance);
        player.clearActivePotionEffects();
        // A match may have shown its own border; the world's is back.
        player.setWorldBorder(null);
        for (SavedEffect effect : effects) {
            var type = Registry.EFFECT.get(Objects.requireNonNull(NamespacedKey.fromString(effect.type())));
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.duration(), effect.amplifier(), effect.ambient(),
                        effect.particles(), effect.icon()));
            }
        }
        restoreHealth(player);
    }

    void restoreHealth(Player player) {
        player.setHealth(Math.min(health, Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue()));
    }

    /**
     * Resets base values and drops modifiers added since the capture. Modifiers that already existed
     * come from equipment, effects or other plugins, which re-apply them themselves; adding them here
     * too would clash with the game re-applying equipment.
     */
    private void restoreAttributes(Player player) {
        for (SavedAttribute saved : attributes) {
            var attribute = Registry.ATTRIBUTE.get(Objects.requireNonNull(NamespacedKey.fromString(saved.key())));
            var instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(saved.base());
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (!saved.modifiers().contains(modifier.getKey().asString())) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    public String toYaml() {
        var yaml = new YamlConfiguration();
        yaml.set("version", FORMAT_VERSION);
        yaml.set("items", items);
        yaml.set("location.world", location.world().toString());
        yaml.set("location.x", location.x());
        yaml.set("location.y", location.y());
        yaml.set("location.z", location.z());
        yaml.set("location.yaw", location.yaw());
        yaml.set("location.pitch", location.pitch());
        yaml.set("game-mode", gameMode);
        yaml.set("health", health);
        yaml.set("food-level", foodLevel);
        yaml.set("saturation", saturation);
        yaml.set("exhaustion", exhaustion);
        yaml.set("level", level);
        yaml.set("exp", exp);
        yaml.set("total-experience", totalExperience);
        yaml.set("allow-flight", allowFlight);
        yaml.set("flying", flying);
        yaml.set("fire-ticks", fireTicks);
        yaml.set("freeze-ticks", freezeTicks);
        yaml.set("remaining-air", remainingAir);
        yaml.set("fall-distance", fallDistance);
        yaml.set("effects", effects.stream().map(effect -> map(
                "type", effect.type(), "duration", effect.duration(), "amplifier", effect.amplifier(),
                "ambient", effect.ambient(), "particles", effect.particles(), "icon", effect.icon())).toList());
        yaml.set("attributes", attributes.stream().map(attribute -> map(
                "key", attribute.key(), "base", attribute.base(), "modifiers", attribute.modifiers())).toList());
        return yaml.saveToString();
    }

    /** @throws IllegalArgumentException if the text isn't a snapshot this version can read */
    public static PlayerSnapshot fromYaml(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("Not valid YAML", e);
        }
        if (yaml.getInt("version") != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unknown snapshot version " + yaml.get("version"));
        }
        var effects = yaml.getMapList("effects").stream().map(effect -> new SavedEffect(
                (String) effect.get("type"), number(effect, "duration").intValue(), number(effect, "amplifier").intValue(),
                (Boolean) effect.get("ambient"), (Boolean) effect.get("particles"), (Boolean) effect.get("icon"))).toList();
        var attributes = yaml.getMapList("attributes").stream().map(attribute -> new SavedAttribute(
                (String) attribute.get("key"), number(attribute, "base").doubleValue(),
                ((List<?>) attribute.get("modifiers")).stream().map(String.class::cast).toList())).toList();
        return new PlayerSnapshot(
                required(yaml, "items"),
                new SavedLocation(UUID.fromString(required(yaml, "location.world")), yaml.getDouble("location.x"),
                        yaml.getDouble("location.y"), yaml.getDouble("location.z"),
                        (float) yaml.getDouble("location.yaw"), (float) yaml.getDouble("location.pitch")),
                required(yaml, "game-mode"),
                yaml.getDouble("health"),
                yaml.getInt("food-level"),
                (float) yaml.getDouble("saturation"),
                (float) yaml.getDouble("exhaustion"),
                yaml.getInt("level"),
                (float) yaml.getDouble("exp"),
                yaml.getInt("total-experience"),
                yaml.getBoolean("allow-flight"),
                yaml.getBoolean("flying"),
                yaml.getInt("fire-ticks"),
                yaml.getInt("freeze-ticks"),
                yaml.getInt("remaining-air"),
                (float) yaml.getDouble("fall-distance"),
                effects,
                attributes);
    }

    private static String required(YamlConfiguration yaml, String path) {
        var value = yaml.getString(path);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + path);
        }
        return value;
    }

    private static Number number(Map<?, ?> map, String key) {
        return (Number) map.get(key);
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
