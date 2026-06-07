package com.drtshock.playervaults.util;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ComponentDispatcher {
    private static boolean isPaper;
    private static boolean spigotComponents;
    private static MethodHandle sendMessage;
    private static MethodHandle deserialize;
    private static Object gsonSerializer;

    static {
        try {
            Class<?> audienceClass = Class.forName("net..kyori.adventure.Audience".replace("..", "."));
            Class<?> componentClass = Class.forName("net..kyori.adventure.text.Component".replace("..", "."));

            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
            MethodType sendMessageType = MethodType.methodType(void.class, componentClass);

            sendMessage = publicLookup.findVirtual(audienceClass, "sendMessage", sendMessageType);

            Class<?> gsonSerializerClass = Class.forName("net..kyori.adventure.text.serializer.gson.GsonComponentSerializer".replace("..", "."));

            gsonSerializer = publicLookup.findStatic(gsonSerializerClass, "gson", MethodType.methodType(gsonSerializerClass)).invokeExact();

            deserialize = publicLookup.findVirtual(gsonSerializerClass, "deserializeFromTree", MethodType.methodType(componentClass, JsonElement.class));

            isPaper = true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignored) {
        } catch (Throwable e) {
            throw new RuntimeException("WHAT", e);
        }

        // Spigot's BaseComponent path relies on CommandSender#spigot(). Most Spigot builds have
        // it, but some legacy 1.8 forks (e.g. imanityspigot) do not, so detect it and otherwise
        // fall back to the universal CommandSender#sendMessage(String) below.
        if (!isPaper) {
            try {
                CommandSender.class.getMethod("spigot");
                spigotComponents = true;
            } catch (Throwable ignored) {
                spigotComponents = false;
            }
        }
    }

    public static void send(CommandSender commandSender, ComponentLike component) {
        if (isPaper) {
            try {
                Object comp = deserialize.invokeExact(gsonSerializer, GsonComponentSerializer.gson().serializeToTree(component.asComponent()));
                sendMessage.invoke(commandSender, comp);
                return;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        if (spigotComponents) {
            try {
                commandSender.spigot().sendMessage(ComponentSerializer.deserialize(GsonComponentSerializer.gson().serializeToTree(component.asComponent())));
                return;
            } catch (Throwable ignored) {
                // Older/forked servers may expose spigot() but choke on the BaseComponents;
                // fall through to the legacy plain-text path.
            }
        }

        // Universal fallback: works on every Bukkit version, including legacy 1.8 forks that
        // lack CommandSender#spigot(). Adventure downsamples colours/formatting to section codes.
        commandSender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component.asComponent()));
    }
}
