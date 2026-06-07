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

import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;

/**
 * {@code org.bukkit.inventory.InventoryView} is an <b>abstract class</b> on legacy servers
 * (1.8 / v1_8_R3) but an <b>interface</b> since 1.21. Code compiled against the modern API emits
 * {@code invokeinterface} for its methods, which blows up at runtime on 1.8 with
 * {@code IncompatibleClassChangeError: Found class ... but interface was expected}.
 * <p>
 * Calling those methods reflectively sidesteps the class/interface mismatch entirely, so the same
 * jar works on both. Call sites pass the view as a plain {@link Object} to avoid referencing the
 * type at all.
 */
public final class InventoryViewCompat {
    private InventoryViewCompat() {
    }

    private static final Method GET_TOP_INVENTORY;
    private static final Method GET_TITLE;

    static {
        Method top = null, title = null;
        try {
            Class<?> view = Class.forName("org.bukkit.inventory.InventoryView");
            top = view.getMethod("getTopInventory");
            title = view.getMethod("getTitle");
        } catch (Throwable ignored) {
        }
        GET_TOP_INVENTORY = top;
        GET_TITLE = title;
    }

    /**
     * @param view an {@code InventoryView} (passed as Object), may be null
     * @return the top inventory of the view, or {@code null} if unavailable
     */
    public static Inventory getTopInventory(Object view) {
        if (view == null || GET_TOP_INVENTORY == null) {
            return null;
        }
        try {
            return (Inventory) GET_TOP_INVENTORY.invoke(view);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * @param view an {@code InventoryView} (passed as Object), may be null
     * @return the view title, or an empty string if unavailable
     */
    public static String getTitle(Object view) {
        if (view == null || GET_TITLE == null) {
            return "";
        }
        try {
            Object title = GET_TITLE.invoke(view);
            return title == null ? "" : title.toString();
        } catch (Throwable e) {
            return "";
        }
    }
}
