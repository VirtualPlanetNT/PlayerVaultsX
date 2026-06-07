package com.drtshock.playervaults.config.file;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.annotation.Comment;
import com.drtshock.playervaults.util.ComponentDispatcher;
import com.google.common.collect.ImmutableMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
public class Translation {
    public static class TL extends ArrayList<String> {
        private static transient PlayerVaults plugin;

        // Legacy '&'/'§' colour & format codes -> MiniMessage tag names.
        private static final transient Map<Character, String> LEGACY_TAGS = buildLegacyTags();

        private static @NonNull Map<Character, String> buildLegacyTags() {
            Map<Character, String> m = new HashMap<>();
            m.put('0', "black");
            m.put('1', "dark_blue");
            m.put('2', "dark_green");
            m.put('3', "dark_aqua");
            m.put('4', "dark_red");
            m.put('5', "dark_purple");
            m.put('6', "gold");
            m.put('7', "gray");
            m.put('8', "dark_gray");
            m.put('9', "blue");
            m.put('a', "green");
            m.put('b', "aqua");
            m.put('c', "red");
            m.put('d', "light_purple");
            m.put('e', "yellow");
            m.put('f', "white");
            m.put('k', "obfuscated");
            m.put('l', "bold");
            m.put('m', "strikethrough");
            m.put('n', "underlined");
            m.put('o', "italic");
            m.put('r', "reset");
            return m;
        }

        private static @NonNull TL of(@NonNull String... strings) {
            TL list = new TL();
            Collections.addAll(list, strings);
            return list;
        }

        public static @NonNull TL copyOf(@NonNull Collection<String> collection) {
            TL list = new TL();
            list.addAll(collection);
            return list;
        }

        public class Builder {
            private transient String randomNum = "475087174643246031314442067831418947468567422217%%__USER__%%0876702565715325383665";
            private transient ImmutableMap.Builder<String, String> map;
            private transient TL title;

            private Builder(@NonNull TL title) {
                this.title = title;
            }

            private Builder(@NonNull String key, @Nullable String value) {
                this.with(key, value);
            }

            public @NonNull Builder with(@NonNull String key, @Nullable String value) {
                if (this.map == null) {
                    this.map = ImmutableMap.builder();
                }
                this.map.put(key, value);
                return this;
            }

            public void send(@NonNull CommandSender sender) {
                TL.this.send(sender, this.map == null ? Collections.emptyMap() : this.map.build(), this.title);
            }

            public @NonNull String getLegacy() {
                return TL.this.getLegacy(this.map == null ? Collections.emptyMap() : this.map.build(), this.title);
            }
        }

        public @NonNull Builder with(@NonNull String key, @Nullable String value) {
            return new Builder(key, value);
        }

        public @NonNull Builder title() {
            return new Builder(TL.plugin.getTL().title());
        }

        public @NonNull Builder title(@NonNull TL title) {
            return new Builder(title);
        }

        public void send(@NonNull CommandSender sender) {
            this.send(sender, Collections.emptyMap(), null);
        }

        private void send(@NonNull CommandSender sender, @NonNull Map<String, String> map, @Nullable TL title) {
            this.forEach(line -> {
                // An empty / blank message means "disabled": send nothing at all (no blank line).
                if (line == null || line.isBlank()) {
                    return;
                }
                ComponentDispatcher.send(sender, this.getComponent(line, map, title));
            });
        }

        private @NonNull Component getComponent(@NonNull String line, @NonNull Map<String, String> map, @Nullable TL title) {
            if (title != null && !title.isEmpty() && !title.get(0).isBlank()) {
                line = title.get(0) + line;
            }
            line = convertLegacy(line);
            TagResolver.Builder resolver = TagResolver.builder();
            map.forEach((k, v) -> resolver.resolver(Placeholder.unparsed(k, v)));
            return MiniMessage.miniMessage().deserialize(line, resolver.build());
        }

        /**
         * Converts legacy {@code &}/{@code §} colour codes (and {@code &#rrggbb} hex) into the
         * equivalent MiniMessage tags, so messages can be authored in either format. Unknown codes
         * are left untouched.
         * <p>
         * To match legacy semantics, a <b>colour</b> code (and hex) emits {@code <reset>} first so it
         * clears any previously applied formatting (bold/italic/...); <b>format</b> codes
         * ({@code &l}, {@code &k}, ...) accumulate as in vanilla. This stops e.g. {@code &lBOLD &7rest}
         * from bleeding bold into the rest of the line.
         */
        private static @NonNull String convertLegacy(@NonNull String input) {
            if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
                return input;
            }
            int len = input.length();
            StringBuilder out = new StringBuilder(len + 16);
            for (int i = 0; i < len; i++) {
                char c = input.charAt(i);
                if ((c == '&' || c == '§') && i + 1 < len) {
                    char next = input.charAt(i + 1);
                    if (next == '#' && i + 7 < len && isHex(input, i + 2)) {
                        out.append("<reset><#").append(input, i + 2, i + 8).append('>');
                        i += 7;
                        continue;
                    }
                    char lower = Character.toLowerCase(next);
                    String tag = LEGACY_TAGS.get(lower);
                    if (tag != null) {
                        if (isLegacyColor(lower)) {
                            out.append("<reset>");
                        }
                        out.append('<').append(tag).append('>');
                        i++;
                        continue;
                    }
                }
                out.append(c);
            }
            return out.toString();
        }

        private static boolean isLegacyColor(char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
        }

        private static boolean isHex(@NonNull String s, int start) {
            for (int i = start; i < start + 6; i++) {
                if (Character.digit(s.charAt(i), 16) < 0) {
                    return false;
                }
            }
            return true;
        }

        public @NonNull String getLegacy() {
            return this.getLegacy(Collections.emptyMap(), null);
        }

        public @NonNull String getLegacy(@NonNull Map<String, String> map) {
            return this.getLegacy(map, null);
        }

        public @NonNull String getLegacy(@NonNull Map<String, String> map, @Nullable TL title) {
            return this.stream()
                    .map(line -> this.getComponent(line, map, title))
                    .filter(Objects::nonNull)
                    .map(component -> LegacyComponentSerializer.legacySection().serialize(component))
                    .collect(Collectors.joining("\n"));
        }

        public boolean arrContains(@Nullable String[] array, @NonNull String target) {
            if (array == null) {
                return false;
            }
            for (String string : array) {
                if (target.equals(string)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class Placeholders {
        private TL title = TL.of("<dark_red>[<white>PlayerVaults<dark_red>]: ");
    }

    private static class Translations {
        private TL openVault = TL.of("<white>Opening vault <green><vault></green>");
        private TL openOtherVault = TL.of("<white>Opening vault <green><vault></green> of <green><player></green>");
        private TL invalidArgs = TL.of("<red>Invalid args!");
        private TL deleteVault = TL.of("<white>Deleted vault <green><vault></green>");
        private TL deleteOtherVault = TL.of("<white>Deleted vault <green><vault></green> of <green><player></green>");
        private TL deleteOtherVaultAll = TL.of("<dark_red>Deleted all vaults belonging to <green><player></green>");
        private TL playerOnly = TL.of("<red>Sorry but that can only be run by a player!");
        private TL mustBeNumber = TL.of("<red>You need to specify a valid number.");
        private TL noPerms = TL.of("<red>You don't have permission for that!");
        private TL insufficientFunds = TL.of("<red>You don't have enough money for that!");
        private TL refundAmount = TL.of("<white>You were refunded <green><price></green> for deleting that vault.");
        private TL costToCreate = TL.of("<white>You were charged <green><price></green> for creating a vault.");
        private TL costToOpen = TL.of("<white>You were charged <green><price></green> for opening that vault.");
        private TL vaultDoesNotExist = TL.of("<red>That vault does not exist!");
        private TL clickASign = TL.of("<white>Now click a sign!");
        private TL notASign = TL.of("<red>You must click a sign!");
        private TL setSign = TL.of("<white>You have successfully set a PlayerVault access sign!");
        private TL existingVaults = TL.of("<white><player> has vaults: <green><vault></green>");
        private TL vaultTitle = TL.of("<dark_red>Vault #<vault>");
        private TL openWithSign = TL.of("<white>Opening vault <green><vault></green> of <green><player></green>");
        private TL noOwnerFound = TL.of("<red>Cannot find vault owner: <green><player></green>");
        private TL convertPluginNotFound = TL.of("<red>No converter found for that plugin.");
        private TL convertComplete = TL.of("<white>Converted <green><count></green> players to PlayerVaults.");
        private TL convertBackground = TL.of("<white>Conversion has been forked to the background. See console for updates.");
        private TL locked = TL.of("<red>Vaults are currently locked while conversion occurs. Please try again in a moment!");
        private TL help = TL.of("<red>Usage: <white>/pv [1-<max>]");
        private TL noVaultsAvailable = TL.of("<red>You don't have access to any vaults.");
        private TL blockedItem = TL.of("<gold><item></gold> <red>is blocked from vaults.");
        private TL blockedItemWithModelData = TL.of("<red>This item is blocked from vaults.");
        private TL blockedItemWithoutModelData = TL.of("<red>This item is blocked from vaults.");
        private TL blockedItemWithEnchantments = TL.of("<red>This item's enchantments are blocked from vaults.");
        private TL signsDisabled = TL.of("<red>Vault signs are currently disabled.");
    }

    @Comment("""
            Messages support MiniMessage (https://docs.advntr.dev/minimessage/format.html) AND
            legacy '&' colour codes, e.g. '&#rrggbb' hex. Placeholders use <angle> brackets: <vault>,
            <player>, <price>, <count>, <item>, and <max> (max vaults the player can use, in 'help').
            On legacy clients (1.8) rich formatting is downsampled to the nearest colour.""")
    private Placeholders placeholders = new Placeholders();

    private Translations translations = new Translations();

    public Translation(@NonNull PlayerVaults plugin) {
        TL.plugin = plugin;
    }

    public @NonNull TL title() {
        return this.placeholders.title;
    }

    public @NonNull TL openVault() {
        return this.translations.openVault;
    }

    public @NonNull TL openOtherVault() {
        return this.translations.openOtherVault;
    }

    public @NonNull TL invalidArgs() {
        return this.translations.invalidArgs;
    }

    public @NonNull TL deleteVault() {
        return this.translations.deleteVault;
    }

    public @NonNull TL deleteOtherVault() {
        return this.translations.deleteOtherVault;
    }

    public @NonNull TL deleteOtherVaultAll() {
        return this.translations.deleteOtherVaultAll;
    }

    public @NonNull TL playerOnly() {
        return this.translations.playerOnly;
    }

    public @NonNull TL mustBeNumber() {
        return this.translations.mustBeNumber;
    }

    public @NonNull TL noPerms() {
        return this.translations.noPerms;
    }

    public @NonNull TL insufficientFunds() {
        return this.translations.insufficientFunds;
    }

    public @NonNull TL refundAmount() {
        return this.translations.refundAmount;
    }

    public @NonNull TL costToCreate() {
        return this.translations.costToCreate;
    }

    public @NonNull TL costToOpen() {
        return this.translations.costToOpen;
    }

    public @NonNull TL vaultDoesNotExist() {
        return this.translations.vaultDoesNotExist;
    }

    public @NonNull TL clickASign() {
        return this.translations.clickASign;
    }

    public @NonNull TL notASign() {
        return this.translations.notASign;
    }

    public @NonNull TL setSign() {
        return this.translations.setSign;
    }

    public @NonNull TL existingVaults() {
        return this.translations.existingVaults;
    }

    public @NonNull TL vaultTitle() {
        return this.translations.vaultTitle;
    }

    public @NonNull TL openWithSign() {
        return this.translations.openWithSign;
    }

    public @NonNull TL noOwnerFound() {
        return this.translations.noOwnerFound;
    }

    public @NonNull TL convertPluginNotFound() {
        return this.translations.convertPluginNotFound;
    }

    public @NonNull TL convertComplete() {
        return this.translations.convertComplete;
    }

    public @NonNull TL convertBackground() {
        return this.translations.convertBackground;
    }

    public @NonNull TL locked() {
        return this.translations.locked;
    }

    public @NonNull TL help() {
        return this.translations.help;
    }

    public @NonNull TL noVaultsAvailable() {
        return this.translations.noVaultsAvailable;
    }

    public @NonNull TL blockedItem() {
        return this.translations.blockedItem;
    }

    public @NonNull TL blockedItemWithModelData() {
        return this.translations.blockedItemWithModelData;
    }

    public @NonNull TL blockedItemWithoutModelData() {
        return this.translations.blockedItemWithoutModelData;
    }

    public @NonNull TL blockedItemWithEnchantments() {
        return this.translations.blockedItemWithEnchantments;
    }

    public @NonNull TL signsDisabled() {
        return this.translations.signsDisabled;
    }
}
