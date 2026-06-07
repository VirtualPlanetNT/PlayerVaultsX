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

package com.drtshock.playervaults.vaultmanagement.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The original storage: one {@code <holder>.yml} file per player, each vault under a {@code vault<N>}
 * key, stored in the {@code Vaults/} folder.
 */
public class FlatFileStorage implements VaultStorage {
    private static final String VAULT_KEY = "vault%d";

    private final File directory;
    private final File backupsFolder;
    private final boolean backups;
    private final Logger logger;
    private final Map<String, YamlConfiguration> cache = new ConcurrentHashMap<>();

    public FlatFileStorage(File directory, File backupsFolder, boolean backups, Logger logger) {
        this.directory = directory;
        this.backupsFolder = backupsFolder;
        this.backups = backups;
        this.logger = logger;
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private File file(String holder) {
        return new File(this.directory, holder + ".yml");
    }

    /** Returns the cached config, else loads from disk, else (createIfMissing) an empty config, else null. */
    private YamlConfiguration read(String holder, boolean createIfMissing) {
        YamlConfiguration cached = this.cache.get(holder);
        if (cached != null) {
            return cached;
        }
        File file = file(holder);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return createIfMissing ? new YamlConfiguration() : null;
    }

    @Override
    public String loadVault(String holder, int number) {
        YamlConfiguration yaml = read(holder, false);
        return yaml == null ? null : yaml.getString(String.format(VAULT_KEY, number));
    }

    @Override
    public void saveVault(String holder, int number, String data) {
        YamlConfiguration yaml = read(holder, true);
        yaml.set(String.format(VAULT_KEY, number), data);
        if (this.cache.containsKey(holder)) {
            this.cache.put(holder, yaml);
        }

        File file = file(holder);
        if (file.exists() && this.backups && this.backupsFolder != null) {
            if (!this.backupsFolder.exists()) {
                this.backupsFolder.mkdirs();
            }
            file.renameTo(new File(this.backupsFolder, holder + ".yml"));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Failed to save vault file for: " + holder, e);
        }
    }

    @Override
    public boolean exists(String holder, int number) {
        YamlConfiguration yaml = read(holder, false);
        return yaml != null && yaml.contains(String.format(VAULT_KEY, number));
    }

    @Override
    public Set<Integer> listVaults(String holder) {
        Set<Integer> out = new HashSet<>();
        YamlConfiguration yaml = read(holder, false);
        if (yaml == null) {
            return out;
        }
        for (String key : yaml.getKeys(false)) {
            if (key.startsWith("vault")) {
                try {
                    out.add(Integer.parseInt(key.substring(5)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public void deleteVault(String holder, int number) {
        File file = file(holder);
        YamlConfiguration yaml = read(holder, false);
        if (yaml == null) {
            return;
        }
        yaml.set(String.format(VAULT_KEY, number), null);
        if (this.cache.containsKey(holder)) {
            this.cache.put(holder, yaml);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Failed to save vault file for: " + holder, e);
        }
    }

    @Override
    public void deleteAll(String holder) {
        this.cache.remove(holder);
        File file = file(holder);
        if (file.exists()) {
            file.delete();
        }
    }

    @Override
    public int purge(long maxAgeMillis) {
        File[] files = this.directory.listFiles();
        if (files == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int removed = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            if (now - file.lastModified() > maxAgeMillis) {
                this.logger.info("Deleting vault file (cleanup): " + file.getName());
                if (file.delete()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    @Override
    public boolean isEmpty() {
        File[] files = this.directory.listFiles((dir, name) -> name.endsWith(".yml"));
        return files == null || files.length == 0;
    }

    @Override
    public void preload(String holder) {
        File file = file(holder);
        if (file.exists()) {
            this.cache.put(holder, YamlConfiguration.loadConfiguration(file));
        }
    }

    @Override
    public void unload(String holder) {
        this.cache.remove(holder);
    }

    @Override
    public String name() {
        return "flatfile";
    }
}
