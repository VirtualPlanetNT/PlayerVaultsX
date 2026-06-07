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

/**
 * The small set of SQL that differs between H2 and MariaDB/MySQL. Everything else
 * ({@code SELECT}/{@code DELETE}) is standard and shared by {@link SqlVaultStorage}.
 * <p>
 * Both {@link #upsert} statements bind the same four parameters in the same order:
 * {@code (holder, vault_number, data, updated_at)}.
 */
public enum Dialect {
    H2 {
        @Override
        public String createTable(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "holder VARCHAR(64) NOT NULL, "
                    + "vault_number INT NOT NULL, "
                    + "data CLOB, "
                    + "updated_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (holder, vault_number))";
        }

        @Override
        public String upsert(String table) {
            return "MERGE INTO " + table + " (holder, vault_number, data, updated_at) "
                    + "KEY (holder, vault_number) VALUES (?, ?, ?, ?)";
        }
    },
    MARIADB {
        @Override
        public String createTable(String table) {
            return "CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "holder VARCHAR(64) NOT NULL, "
                    + "vault_number INT NOT NULL, "
                    + "data LONGTEXT, "
                    + "updated_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (holder, vault_number))";
        }

        @Override
        public String upsert(String table) {
            return "INSERT INTO " + table + " (holder, vault_number, data, updated_at) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE data = VALUES(data), updated_at = VALUES(updated_at)";
        }
    };

    public abstract String createTable(String table);

    public abstract String upsert(String table);
}
