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

import java.util.Set;

/**
 * Abstraction over where vault data lives. The stored value is the opaque, version-independent
 * serialized string produced by {@code CardboardBoxSerialization} — backends never interpret it,
 * so swapping flatfile/H2/MariaDB never affects item compatibility across MC versions.
 * <p>
 * Keys are {@code (holder, number)} where holder is usually a player UUID string.
 */
public interface VaultStorage {

    /** @return the serialized vault data, or {@code null} if that vault does not exist. */
    String loadVault(String holder, int number);

    /** Create or overwrite a vault's serialized data. */
    void saveVault(String holder, int number, String data);

    boolean exists(String holder, int number);

    /** @return the set of vault numbers a holder owns (may be empty). */
    Set<Integer> listVaults(String holder);

    void deleteVault(String holder, int number);

    void deleteAll(String holder);

    /**
     * Delete vaults whose last modification is older than {@code maxAgeMillis} ago.
     *
     * @return the number of vaults/files removed.
     */
    int purge(long maxAgeMillis);

    /** @return true if this store holds no vaults at all (used to drive one-time migration). */
    boolean isEmpty();

    /** Optional hint that a holder is about to be used (e.g. on join). */
    default void preload(String holder) {
    }

    /** Optional hint that a holder is no longer needed (e.g. on quit). */
    default void unload(String holder) {
    }

    /** Release any resources (connection pools, etc.). */
    default void close() {
    }

    /** Short identifier for logging, e.g. "flatfile", "h2", "mariadb". */
    String name();
}
