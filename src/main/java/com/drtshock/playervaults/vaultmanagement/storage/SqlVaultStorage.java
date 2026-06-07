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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC-backed storage shared by the H2 and MariaDB backends. The only thing that differs between
 * them is the {@link Dialect} (table DDL + upsert). Connection pooling is provided by the supplied
 * {@link DataSource} (H2's {@code JdbcConnectionPool} / MariaDB's {@code MariaDbPoolDataSource}).
 */
public class SqlVaultStorage implements VaultStorage {
    private final DataSource dataSource;
    private final AutoCloseable closer;
    private final Dialect dialect;
    private final String table;
    private final String name;
    private final Logger logger;

    public SqlVaultStorage(DataSource dataSource, AutoCloseable closer, Dialect dialect, String table, String name, Logger logger) throws SQLException {
        this.dataSource = dataSource;
        this.closer = closer;
        this.dialect = dialect;
        this.table = table;
        this.name = name;
        this.logger = logger;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(dialect.createTable(table));
        }
    }

    @Override
    public String loadVault(String holder, int number) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT data FROM " + this.table + " WHERE holder = ? AND vault_number = ?")) {
            ps.setString(1, holder);
            ps.setInt(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            fail("load vault " + number + " for " + holder, e);
            return null;
        }
    }

    @Override
    public void saveVault(String holder, int number, String data) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(this.dialect.upsert(this.table))) {
            ps.setString(1, holder);
            ps.setInt(2, number);
            ps.setString(3, data);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            fail("save vault " + number + " for " + holder, e);
        }
    }

    @Override
    public boolean exists(String holder, int number) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + this.table + " WHERE holder = ? AND vault_number = ?")) {
            ps.setString(1, holder);
            ps.setInt(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            fail("check vault " + number + " for " + holder, e);
            return false;
        }
    }

    @Override
    public Set<Integer> listVaults(String holder) {
        Set<Integer> out = new HashSet<>();
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT vault_number FROM " + this.table + " WHERE holder = ?")) {
            ps.setString(1, holder);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            fail("list vaults for " + holder, e);
        }
        return out;
    }

    @Override
    public void deleteVault(String holder, int number) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + this.table + " WHERE holder = ? AND vault_number = ?")) {
            ps.setString(1, holder);
            ps.setInt(2, number);
            ps.executeUpdate();
        } catch (SQLException e) {
            fail("delete vault " + number + " for " + holder, e);
        }
    }

    @Override
    public void deleteAll(String holder) {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + this.table + " WHERE holder = ?")) {
            ps.setString(1, holder);
            ps.executeUpdate();
        } catch (SQLException e) {
            fail("delete all vaults for " + holder, e);
        }
    }

    @Override
    public int purge(long maxAgeMillis) {
        long threshold = System.currentTimeMillis() - maxAgeMillis;
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM " + this.table + " WHERE updated_at < ?")) {
            ps.setLong(1, threshold);
            return ps.executeUpdate();
        } catch (SQLException e) {
            fail("purge old vaults", e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + this.table);
             ResultSet rs = ps.executeQuery()) {
            return !rs.next() || rs.getLong(1) == 0L;
        } catch (SQLException e) {
            fail("check if storage is empty", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (this.closer != null) {
            try {
                this.closer.close();
            } catch (Exception e) {
                this.logger.log(Level.WARNING, "Failed to close " + this.name + " storage", e);
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    private void fail(String action, SQLException e) {
        this.logger.log(Level.SEVERE, "[" + this.name + "] Failed to " + action, e);
    }
}
