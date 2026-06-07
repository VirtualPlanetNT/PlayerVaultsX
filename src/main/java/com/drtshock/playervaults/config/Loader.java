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
package com.drtshock.playervaults.config;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.annotation.Comment;
import com.drtshock.playervaults.config.annotation.ConfigName;
import com.drtshock.playervaults.config.annotation.WipeOnReload;
import com.drtshock.playervaults.config.file.Translation;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.serializer.Serializer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binds a YAML file (with comments) to an annotated config object and back.
 * <p>
 * Config is declared as plain Java classes whose fields carry {@link Comment}, {@link ConfigName}
 * and {@link WipeOnReload} annotations; {@link #loadAndSave} reads the file into the object (missing
 * keys keep their field defaults) and then rewrites the file, normalising it and (re)emitting all
 * comments from the annotations. SnakeYAML is shaded + relocated so this works on every server
 * version (the bundled SnakeYAML on 1.8 predates comment support).
 */
public class Loader {
    /** Field types treated as leaves (everything else is recursed into as a nested section). */
    private static final Set<Class<?>> LEAF_TYPES = new HashSet<>();

    static {
        LEAF_TYPES.add(Boolean.TYPE);
        LEAF_TYPES.add(Byte.TYPE);
        LEAF_TYPES.add(Character.TYPE);
        LEAF_TYPES.add(Double.TYPE);
        LEAF_TYPES.add(Float.TYPE);
        LEAF_TYPES.add(Integer.TYPE);
        LEAF_TYPES.add(Long.TYPE);
        LEAF_TYPES.add(Short.TYPE);
        LEAF_TYPES.add(List.class);
        LEAF_TYPES.add(Map.class);
        LEAF_TYPES.add(Set.class);
        LEAF_TYPES.add(String.class);
        LEAF_TYPES.add(Translation.TL.class);
    }

    public static void loadAndSave(String fileName, Object config) throws IOException, IllegalAccessException {
        File file = getFile(fileName);
        bind(parse(file), config);
        write(file, config);
    }

    public static File getFile(String file) {
        Path configFolder = PlayerVaults.getInstance().getDataFolder().toPath();
        if (!configFolder.toFile().exists()) {
            configFolder.toFile().mkdirs();
        }
        return configFolder.resolve(file + ".yml").toFile();
    }

    // ------------------------------------------------------------------ load

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(File file) throws IOException {
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object loaded = yaml.load(reader);
            return loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static void bind(Map<String, Object> data, Object object) throws IllegalAccessException {
        for (Field field : getFields(object.getClass())) {
            if (field.isSynthetic()) {
                continue;
            }
            if ((field.getModifiers() & Modifier.TRANSIENT) != 0) {
                if (field.getAnnotation(WipeOnReload.class) != null) {
                    field.setAccessible(true);
                    field.set(object, null);
                }
                continue;
            }
            field.setAccessible(true);
            String key = nameOf(field);
            Object raw = data.get(key);

            if (LEAF_TYPES.contains(field.getType())) {
                if (raw == null) {
                    continue; // absent -> keep default
                }
                Object def = field.get(object);
                try {
                    field.set(object, convertLeaf(field.getType(), raw, def));
                } catch (Exception ex) {
                    PlayerVaults.getInstance().getLogger().warning("Bad value for '" + key + "' in config, keeping default: " + ex.getMessage());
                    field.set(object, def);
                }
            } else {
                Object child = field.get(object);
                if (child != null && raw instanceof Map) {
                    bind((Map<String, Object>) raw, child);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertLeaf(Class<?> type, Object raw, Object def) {
        if (Translation.TL.class.isAssignableFrom(type)) {
            if (raw instanceof List) {
                return Translation.TL.copyOf(toStringList((List<Object>) raw));
            }
            return Translation.TL.copyOf(Collections.singletonList(String.valueOf(raw)));
        }
        if (List.class.isAssignableFrom(type)) {
            return raw instanceof List ? new ArrayList<>((List<Object>) raw) : new ArrayList<>(Collections.singletonList(raw));
        }
        if (Set.class.isAssignableFrom(type)) {
            return raw instanceof List ? new HashSet<>((List<Object>) raw) : new HashSet<>(Collections.singleton(raw));
        }
        if (Map.class.isAssignableFrom(type)) {
            return raw instanceof Map ? new LinkedHashMap<>((Map<Object, Object>) raw) : def;
        }
        if (type == Boolean.TYPE || type == Boolean.class) {
            return raw instanceof Boolean ? raw : Boolean.parseBoolean(String.valueOf(raw));
        }
        if (type == String.class) {
            return String.valueOf(raw);
        }
        if (type == Character.TYPE || type == Character.class) {
            String s = String.valueOf(raw);
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        Number number = raw instanceof Number ? (Number) raw : Double.valueOf(String.valueOf(raw));
        if (type == Integer.TYPE || type == Integer.class) {
            return number.intValue();
        }
        if (type == Long.TYPE || type == Long.class) {
            return number.longValue();
        }
        if (type == Double.TYPE || type == Double.class) {
            return number.doubleValue();
        }
        if (type == Float.TYPE || type == Float.class) {
            return number.floatValue();
        }
        if (type == Short.TYPE || type == Short.class) {
            return number.shortValue();
        }
        if (type == Byte.TYPE || type == Byte.class) {
            return number.byteValue();
        }
        return def;
    }

    private static List<String> toStringList(List<Object> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    // ------------------------------------------------------------------ save

    private static void write(File file, Object config) throws IOException, IllegalAccessException {
        DumperOptions options = new DumperOptions();
        options.setProcessComments(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setSplitLines(false); // never fold long messages/comments onto multiple lines
        options.setWidth(Integer.MAX_VALUE);

        StringWriter writer = new StringWriter();
        Serializer serializer = new Serializer(new Emitter(writer, options), new Resolver(), options, null);
        serializer.open();
        serializer.serialize(buildMapping(config));
        serializer.close();

        // Strip trailing whitespace SnakeYAML leaves on blank separator/comment lines. Safe: scalar
        // values that end in spaces are quoted, so their line ends with the quote char, not a space.
        String yaml = writer.toString().replaceAll("(?m)[ \\t]+$", "");
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private static MappingNode buildMapping(Object object) throws IllegalAccessException {
        List<NodeTuple> tuples = new ArrayList<>();
        boolean first = true;
        for (Field field : getFields(object.getClass())) {
            if (field.isSynthetic() || (field.getModifiers() & Modifier.TRANSIENT) != 0) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(object);
            Node valueNode = LEAF_TYPES.contains(field.getType()) ? buildLeaf(field.getType(), value) : buildMapping(value);

            ScalarNode keyNode = scalar(Tag.STR, nameOf(field));
            Comment comment = field.getAnnotation(Comment.class);
            if (comment != null) {
                List<CommentLine> lines = new ArrayList<>();
                if (!first) {
                    lines.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
                }
                for (String line : comment.value().split("\n", -1)) {
                    lines.add(new CommentLine(null, null, " " + line, CommentType.BLOCK));
                }
                keyNode.setBlockComments(lines);
            }
            tuples.add(new NodeTuple(keyNode, valueNode));
            first = false;
        }
        return new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
    }

    private static Node buildLeaf(Class<?> type, Object value) {
        if (value == null) {
            return scalar(Tag.NULL, "null");
        }
        if (Translation.TL.class.isAssignableFrom(type)) {
            Translation.TL tl = (Translation.TL) value;
            if (tl.size() == 1) {
                return scalar(Tag.STR, tl.get(0));
            }
            return sequence(tl);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return sequence((Collection<?>) value);
        }
        if (Map.class.isAssignableFrom(type)) {
            List<NodeTuple> tuples = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                tuples.add(new NodeTuple(scalar(Tag.STR, String.valueOf(entry.getKey())), scalarOf(entry.getValue())));
            }
            return new MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK);
        }
        return scalarOf(value);
    }

    private static SequenceNode sequence(Collection<?> values) {
        List<Node> nodes = new ArrayList<>();
        for (Object value : values) {
            nodes.add(scalarOf(value));
        }
        return new SequenceNode(Tag.SEQ, nodes, DumperOptions.FlowStyle.BLOCK);
    }

    private static ScalarNode scalarOf(Object value) {
        if (value == null) {
            return scalar(Tag.NULL, "null");
        }
        if (value instanceof Boolean) {
            return scalar(Tag.BOOL, value.toString());
        }
        if (value instanceof Double || value instanceof Float) {
            return scalar(Tag.FLOAT, value.toString());
        }
        if (value instanceof Number) {
            return scalar(Tag.INT, value.toString());
        }
        return scalar(Tag.STR, value.toString());
    }

    private static ScalarNode scalar(Tag tag, String value) {
        // PLAIN is requested but SnakeYAML's emitter auto-upgrades to a quoted style whenever the
        // content can't be represented plainly (e.g. MiniMessage tags, leading '<', ' #', ': ').
        return new ScalarNode(tag, value, null, null, DumperOptions.ScalarStyle.PLAIN);
    }

    // ------------------------------------------------------------------ shared

    private static String nameOf(Field field) {
        ConfigName configName = field.getAnnotation(ConfigName.class);
        return configName == null || configName.value().isEmpty() ? field.getName() : configName.value();
    }

    private static List<Field> getFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Collections.addAll(fields, c.getDeclaredFields());
        }
        return fields;
    }
}
