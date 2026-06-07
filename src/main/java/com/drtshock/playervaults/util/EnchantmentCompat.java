/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.util;

import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Resolves enchantments by name across the full range of supported server versions.
 * <p>
 * Modern servers (1.13+/1.14+) expose {@code org.bukkit.Registry.ENCHANTMENT} and namespaced
 * keys, while legacy servers (e.g. 1.8) only expose {@link Enchantment#getByName(String)} /
 * {@code Enchantment.values()} with upper-snake names like {@code DAMAGE_ALL}.
 * <p>
 * Every version-specific API ({@code Registry}, {@code NamespacedKey}, {@code Keyed},
 * {@code getByName}, {@code values}) is reached purely through reflection, so this class links
 * and runs unchanged on 1.8 (where {@code org.bukkit.Registry} does not even exist) as well as
 * on 1.21+ (where the legacy statics may be gone).
 */
public final class EnchantmentCompat {
    private EnchantmentCompat() {
    }

    // Modern: org.bukkit.Registry.ENCHANTMENT, or null on legacy servers (e.g. 1.8).
    private static final Object REGISTRY;
    private static final Method REGISTRY_MATCH;    // Registry#match(String) -> Keyed
    private static final Method REGISTRY_GET;      // Registry#get(NamespacedKey) -> Keyed
    private static final Method REGISTRY_ITERATOR; // Iterable#iterator()
    private static final Method KEYED_GET_KEY;     // Keyed#getKey() -> NamespacedKey
    private static final Method NSK_MINECRAFT;     // NamespacedKey.minecraft(String)

    // Legacy (1.8): Enchantment statics, accessed reflectively (removed on modern runtimes).
    private static final Method ENCH_GET_BY_NAME;  // static Enchantment.getByName(String)
    private static final Method ENCH_VALUES;       // static Enchantment.values()
    private static final Method ENCH_GET_NAME;     // Enchantment#getName()

    // Common modern (lowercase) -> legacy 1.8 Enchantment.getByName names.
    private static final Map<String, String> LEGACY_ALIASES = buildLegacyAliases();

    static {
        Object registry = null;
        Method match = null, get = null, iterator = null, keyedKey = null, nsk = null;
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Field enchField = registryClass.getField("ENCHANTMENT");
            registry = enchField.get(null);
            try {
                match = registryClass.getMethod("match", String.class);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Class<?> nskClass = Class.forName("org.bukkit.NamespacedKey");
                get = registryClass.getMethod("get", nskClass);
                nsk = nskClass.getMethod("minecraft", String.class);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                iterator = Iterable.class.getMethod("iterator");
                keyedKey = Class.forName("org.bukkit.Keyed").getMethod("getKey");
            } catch (ReflectiveOperationException ignored) {
            }
        } catch (Throwable ignored) {
            // Legacy server: org.bukkit.Registry is not present (e.g. 1.8). Fall back below.
        }
        REGISTRY = registry;
        REGISTRY_MATCH = match;
        REGISTRY_GET = get;
        REGISTRY_ITERATOR = iterator;
        KEYED_GET_KEY = keyedKey;
        NSK_MINECRAFT = nsk;

        Method byName = null, values = null, getName = null;
        try {
            byName = Enchantment.class.getMethod("getByName", String.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            values = Enchantment.class.getMethod("values");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            getName = Enchantment.class.getMethod("getName");
        } catch (NoSuchMethodException ignored) {
        }
        ENCH_GET_BY_NAME = byName;
        ENCH_VALUES = values;
        ENCH_GET_NAME = getName;
    }

    /**
     * @return true when running on a modern server that exposes {@code org.bukkit.Registry}.
     */
    public static boolean isModern() {
        return REGISTRY != null;
    }

    /**
     * Resolves an enchantment from a config string, accepting both modern ids
     * ({@code sharpness}, {@code minecraft:sharpness}) and legacy names ({@code DAMAGE_ALL}).
     *
     * @param input the configured name
     * @return the matching enchantment, or {@code null} if none is found
     */
    public static Enchantment match(String input) {
        if (input == null) {
            return null;
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (REGISTRY != null) {
            // Modern: Registry#match handles "sharpness" and "minecraft:sharpness".
            if (REGISTRY_MATCH != null) {
                try {
                    if (REGISTRY_MATCH.invoke(REGISTRY, raw) instanceof Enchantment e) {
                        return e;
                    }
                } catch (Throwable ignored) {
                }
            }
            // Fallback for versions without Registry#match: Registry#get(NamespacedKey).
            if (REGISTRY_GET != null && NSK_MINECRAFT != null) {
                try {
                    Object key = NSK_MINECRAFT.invoke(null, stripNamespace(raw).toLowerCase(Locale.ROOT));
                    if (REGISTRY_GET.invoke(REGISTRY, key) instanceof Enchantment e) {
                        return e;
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        return legacyMatch(raw);
    }

    /**
     * @return a comma-separated, sorted list of valid enchantment ids for the running server,
     * suitable for an informational message.
     */
    public static String validOptions() {
        TreeSet<String> out = new TreeSet<>();
        if (REGISTRY != null && REGISTRY_ITERATOR != null && KEYED_GET_KEY != null) {
            try {
                Iterator<?> it = (Iterator<?>) REGISTRY_ITERATOR.invoke(REGISTRY);
                while (it.hasNext()) {
                    Object key = KEYED_GET_KEY.invoke(it.next());
                    if (key != null) {
                        out.add(key.toString());
                    }
                }
            } catch (Throwable ignored) {
            }
        } else if (ENCH_VALUES != null) {
            try {
                Object arr = ENCH_VALUES.invoke(null);
                int len = Array.getLength(arr);
                for (int i = 0; i < len; i++) {
                    Object ench = Array.get(arr, i);
                    if (ENCH_GET_NAME != null) {
                        Object name = ENCH_GET_NAME.invoke(ench);
                        if (name != null) {
                            out.add(name.toString().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return String.join(", ", out);
    }

    private static Enchantment legacyMatch(String raw) {
        if (ENCH_GET_BY_NAME == null) {
            return null;
        }
        String key = stripNamespace(raw).toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>(3);
        String alias = LEGACY_ALIASES.get(key);
        if (alias != null) {
            candidates.add(alias);
        }
        candidates.add(key.toUpperCase(Locale.ROOT));
        candidates.add(raw.toUpperCase(Locale.ROOT));
        for (String name : candidates) {
            try {
                if (ENCH_GET_BY_NAME.invoke(null, name) instanceof Enchantment e) {
                    return e;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String stripNamespace(String s) {
        int idx = s.indexOf(':');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private static Map<String, String> buildLegacyAliases() {
        Map<String, String> m = new HashMap<>();
        // Armor
        m.put("protection", "PROTECTION_ENVIRONMENTAL");
        m.put("fire_protection", "PROTECTION_FIRE");
        m.put("feather_falling", "PROTECTION_FALL");
        m.put("blast_protection", "PROTECTION_EXPLOSIONS");
        m.put("projectile_protection", "PROTECTION_PROJECTILE");
        m.put("respiration", "OXYGEN");
        m.put("aqua_affinity", "WATER_WORKER");
        m.put("thorns", "THORNS");
        m.put("depth_strider", "DEPTH_STRIDER");
        // Sword
        m.put("sharpness", "DAMAGE_ALL");
        m.put("smite", "DAMAGE_UNDEAD");
        m.put("bane_of_arthropods", "DAMAGE_ARTHROPODS");
        m.put("knockback", "KNOCKBACK");
        m.put("fire_aspect", "FIRE_ASPECT");
        m.put("looting", "LOOT_BONUS_MOBS");
        // Tool
        m.put("efficiency", "DIG_SPEED");
        m.put("silk_touch", "SILK_TOUCH");
        m.put("unbreaking", "DURABILITY");
        m.put("fortune", "LOOT_BONUS_BLOCKS");
        // Bow
        m.put("power", "ARROW_DAMAGE");
        m.put("punch", "ARROW_KNOCKBACK");
        m.put("flame", "ARROW_FIRE");
        m.put("infinity", "ARROW_INFINITE");
        // Fishing
        m.put("luck_of_the_sea", "LUCK");
        m.put("lure", "LURE");
        return m;
    }
}
