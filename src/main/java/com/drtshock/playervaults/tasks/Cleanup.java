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

package com.drtshock.playervaults.tasks;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.storage.VaultStorage;

public class Cleanup implements Runnable {

    private final long diff;

    public Cleanup(long diff) {
        this.diff = diff * 86400000L;
    }

    @Override
    public void run() {
        VaultStorage storage = PlayerVaults.getInstance().getStorage();
        if (storage == null) {
            return;
        }
        int removed = storage.purge(this.diff);
        if (removed > 0) {
            PlayerVaults.getInstance().getLogger().info("Cleanup removed " + removed + " vault(s) untouched for too long.");
        }
    }
}
