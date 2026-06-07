/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler, Laxwashere, CmdrKittens
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
package com.drtshock.playervaults.config.file;

import com.drtshock.playervaults.config.annotation.Comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "InnerClassMayBeStatic", "unused"})
public class Config {
    public class Block {
        private boolean enabled = true;
        @Comment("""
                Material list for blocked items (does not support ID's), only effective if the feature is enabled.
                 If you don't know material names: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html
                
                Also, if you add "BLOCK_ALL_WITH_CUSTOM_MODEL_DATA" or "BLOCK_ALL_WITHOUT_CUSTOM_MODEL_DATA"
                 then either all items with custom model data will be blocked, or all items without custom model data will be blocked.""")
        private List<String> list = new ArrayList<>() {
            {
                this.add("PUMPKIN");
            }
        };

        @Comment("Enchantments to block from entering a vault at all.")
        private List<String> enchantmentsBlocked = new ArrayList<>();

        public boolean isEnabled() {
            return this.enabled;
        }

        public List<String> getList() {
            if (this.list == null) {
                this.list = new ArrayList<>();
            }
            return Collections.unmodifiableList(this.list);
        }

        public List<String> getEnchantmentsBlocked() {
            if (this.enchantmentsBlocked == null) {
                this.enchantmentsBlocked = new ArrayList<>();
            }
            return Collections.unmodifiableList(this.enchantmentsBlocked);
        }
    }

    public class Economy {
        @Comment("Set me to true to enable economy features!")
        private boolean enabled = false;
        private double feeToCreate = 100;
        private double feeToOpen = 10;
        private double refundOnDelete = 50;

        public boolean isEnabled() {
            return this.enabled;
        }

        public double getFeeToCreate() {
            return this.feeToCreate;
        }

        public double getFeeToOpen() {
            return this.feeToOpen;
        }

        public double getRefundOnDelete() {
            return this.refundOnDelete;
        }
    }

    public class PurgePlanet {
        private boolean enabled = false;
        @Comment("Time, in days, since last edit")
        private int daysSinceLastEdit = 30;

        public boolean isEnabled() {
            return this.enabled;
        }

        public int getDaysSinceLastEdit() {
            return this.daysSinceLastEdit;
        }
    }

    public class Storage {
        @Comment("""
                Where vaults are stored. Options:
                 flatfile - one YAML file per player (no setup, the default).
                 h2       - embedded SQL database in the plugin folder (no setup, single server).
                 mariadb  - external MariaDB/MySQL server (configure below).
                Switching to h2/mariadb auto-imports existing flatfile vaults once (flatfile is kept as a backup).""")
        private String storageType = "flatfile";

        private FlatFile flatFile = new FlatFile();
        private H2 h2 = new H2();
        private MariaDB mariadb = new MariaDB();

        public class FlatFile {
            @Comment("""
                    Backups
                     Enabling this will create backups of vaults automagically.""")
            private boolean backups = true;

            public boolean isBackups() {
                return this.backups;
            }
        }

        public class H2 {
            @Comment("Name of the embedded database file, created inside the plugin folder.")
            private String file = "vaults";

            public String getFile() {
                return this.file;
            }
        }

        public class MariaDB {
            private String host = "localhost";
            private int port = 3306;
            private String database = "playervaults";
            private String username = "root";
            private String password = "";

            @Comment("Prefix for this plugin's tables (lets several plugins share one database).")
            private String tablePrefix = "pv_";

            @Comment("Maximum number of pooled connections.")
            private int poolSize = 8;

            @Comment("Extra JDBC parameters appended to the connection URL (ampersand-separated).")
            private String properties = "useSSL=false&allowPublicKeyRetrieval=true";

            public String getHost() {
                return this.host;
            }

            public int getPort() {
                return this.port;
            }

            public String getDatabase() {
                return this.database;
            }

            public String getUsername() {
                return this.username;
            }

            public String getPassword() {
                return this.password;
            }

            public String getTablePrefix() {
                return this.tablePrefix;
            }

            public int getPoolSize() {
                return this.poolSize;
            }

            public String getProperties() {
                return this.properties;
            }
        }

        public String getStorageType() {
            return this.storageType;
        }

        public FlatFile getFlatFile() {
            return this.flatFile;
        }

        public H2 getH2() {
            return this.h2;
        }

        public MariaDB getMariaDB() {
            return this.mariadb;
        }
    }

    @Comment("""
            PlayerVaults
            Created by: https://github.com/drtshock/PlayerVaults/graphs/contributors/
            Resource page: https://www.spigotmc.org/resources/51204/
            Discord server: https://discordapp.com/invite/JZcWDEt/
            Made with love <3""")
    private boolean aPleasantHello = true;

    @Comment("""
            Debug Mode
             This will print everything the plugin is doing to console.
             You should only enable this if you're working with a contributor to fix something.""")
    private boolean debug = false;

    @Comment("""
            Can be 1 through 6.
            Default: 6""")
    private int defaultVaultRows = 6;

    @Comment("""
            Signs
             This will determine whether vault signs are enabled.
             If you don't know what this is or if it's for you, see the resource page.""")
    private boolean signs = false;

    @Comment("""
            Economy
             These are all of the settings for the economy integration. (Requires Vault)
              Bypass permission is: playervaults.free""")
    private Economy economy = new Economy();

    @Comment("""
            Blocked Items
             This will allow you to block specific materials from vaults.
              Bypass permission is: playervaults.bypassblockeditems""")
    private Block itemBlocking = new Block();

    @Comment("""
            Cleanup
             Enabling this will purge vaults that haven't been touched in the specified time frame.
              Reminder: This is only checked during startup.
                        This will not lag your server or touch the backups folder.""")
    private PurgePlanet purge = new PurgePlanet();

    @Comment("Sets the highest vault amount this plugin will test perms for")
    private int maxVaultAmountPermTest = 99;

    @Comment("Storage option. Currently only flatfile, but soon more! :)")
    private Storage storage = new Storage();

    public boolean isDebug() {
        return this.debug;
    }

    public int getDefaultVaultRows() {
        return this.defaultVaultRows;
    }

    public boolean isSigns() {
        return this.signs;
    }

    public Economy getEconomy() {
        return this.economy;
    }

    public Block getItemBlocking() {
        return this.itemBlocking;
    }

    public PurgePlanet getPurge() {
        return this.purge;
    }

    public int getMaxVaultAmountPermTest() {
        return this.maxVaultAmountPermTest;
    }

    public Storage getStorage() {
        return this.storage;
    }
}