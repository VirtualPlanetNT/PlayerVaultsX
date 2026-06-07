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

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the configured {@link VaultStorage} and performs the one-time flatfile → SQL import.
 */
public final class Storages {
    private Storages() {
    }

    public static VaultStorage create(PlayerVaults plugin) {
        Config.Storage cfg = plugin.getConf().getStorage();
        String type = cfg.getStorageType() == null ? "flatfile" : cfg.getStorageType().trim().toLowerCase(Locale.ROOT);
        Logger log = plugin.getLogger();
        try {
            switch (type) {
                case "h2":
                    return h2(plugin, cfg, log);
                case "mariadb":
                case "mysql":
                    return mariadb(plugin, cfg, log);
                case "flatfile":
                    return flatfile(plugin);
                default:
                    log.warning("Unknown storage type '" + type + "', falling back to flatfile.");
                    return flatfile(plugin);
            }
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Failed to initialise '" + type + "' storage, falling back to flatfile.", t);
            return flatfile(plugin);
        }
    }

    private static VaultStorage flatfile(PlayerVaults plugin) {
        boolean backups = plugin.isBackupsEnabled();
        File backupsFolder = backups ? plugin.getBackupsFolder() : null;
        return new FlatFileStorage(plugin.getVaultData(), backupsFolder, backups, plugin.getLogger());
    }

    private static VaultStorage h2(PlayerVaults plugin, Config.Storage cfg, Logger log) throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), cfg.getH2().getFile());
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:" + dbFile.getAbsolutePath() + ";DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        h2.setPassword("");
        HikariDataSource ds = pool(h2, "PlayerVaultsX-H2", 10);
        SqlVaultStorage storage = new SqlVaultStorage(ds, ds::close, Dialect.H2, "vaults", "h2", log);
        migrateFromFlatfile(plugin, storage, log);
        return storage;
    }

    private static VaultStorage mariadb(PlayerVaults plugin, Config.Storage cfg, Logger log) throws SQLException {
        Config.Storage.MariaDB m = cfg.getMariaDB();
        StringBuilder url = new StringBuilder("jdbc:mariadb://")
                .append(m.getHost()).append(':').append(m.getPort()).append('/').append(m.getDatabase());
        String props = m.getProperties() == null ? "" : m.getProperties().trim();
        if (!props.isEmpty()) {
            url.append('?').append(props);
        }

        // Basic (lazy) DataSource: it only connects when HikariCP asks for a connection, so HikariCP
        // fully owns pooling/validation/reconnection. Credentials are passed to the driver, never the URL.
        MariaDbDataSource mariadb = new MariaDbDataSource();
        mariadb.setUser(m.getUsername());
        mariadb.setPassword(m.getPassword());
        mariadb.setUrl(url.toString());

        HikariDataSource ds = pool(mariadb, "PlayerVaultsX-MariaDB", m.getPoolSize());
        String table = (m.getTablePrefix() == null ? "" : m.getTablePrefix()) + "vaults";
        SqlVaultStorage storage = new SqlVaultStorage(ds, ds::close, Dialect.MARIADB, table, "mariadb", log);
        migrateFromFlatfile(plugin, storage, log);
        return storage;
    }

    /** Wraps a driver's lazy {@link DataSource} in a HikariCP pool with sane production defaults. */
    private static HikariDataSource pool(DataSource backing, String poolName, int maxPoolSize) {
        HikariConfig hc = new HikariConfig();
        hc.setDataSource(backing);
        hc.setPoolName(poolName);
        hc.setMaximumPoolSize(Math.max(1, maxPoolSize));
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(TimeUnit.SECONDS.toMillis(15));
        hc.setKeepaliveTime(TimeUnit.MINUTES.toMillis(5));   // ping idle connections so they don't go stale
        hc.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));    // recycle before MySQL/MariaDB wait_timeout
        return new HikariDataSource(hc);
    }

    /** Non-destructive one-time import of existing flatfile ({@code Vaults/}) files into an empty SQL store. */
    private static void migrateFromFlatfile(PlayerVaults plugin, VaultStorage sql, Logger log) {
        File dir = plugin.getVaultData();
        File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0 || !sql.isEmpty()) {
            return;
        }
        log.info("Importing existing flatfile vaults into " + sql.name() + " storage (one-time)...");
        int players = 0;
        int vaults = 0;
        for (File file : files) {
            String fileName = file.getName();
            String holder = fileName.substring(0, fileName.length() - ".yml".length());
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            boolean imported = false;
            for (String key : yaml.getKeys(false)) {
                if (!key.startsWith("vault")) {
                    continue;
                }
                int number;
                try {
                    number = Integer.parseInt(key.substring(5));
                } catch (NumberFormatException e) {
                    continue;
                }
                String data = yaml.getString(key);
                if (data != null && !data.isEmpty()) {
                    sql.saveVault(holder, number, data);
                    vaults++;
                    imported = true;
                }
            }
            if (imported) {
                players++;
            }
        }
        log.info("Imported " + vaults + " vaults for " + players + " players into " + sql.name() + ". Flatfile data kept as backup.");
    }
}
