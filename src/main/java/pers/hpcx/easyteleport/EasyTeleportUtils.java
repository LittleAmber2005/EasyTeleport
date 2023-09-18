package pers.hpcx.easyteleport;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.minecraft.util.Formatting.*;

public final class EasyTeleportUtils {
    
    public static final String MOD_ID = "easyteleport";
    public static final String CONFIG_COMMENTS = "easy-teleport mod config";
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("easy-teleport.properties");
    public static final Path ANCHOR_PATH = FabricLoader.getInstance().getGameDir().resolve("world").resolve("public-anchors.dat");
    
    private EasyTeleportUtils() {
    }
    
    public static MutableText green(String str) {
        return Text.literal(str).formatted(GREEN);
    }
    
    public static MutableText red(String str) {
        return Text.literal(str).formatted(RED);
    }
    
    public static MutableText yellow(String str) {
        return Text.literal(str).formatted(YELLOW);
    }
    
    public static MutableText gold(String str) {
        return Text.literal(str).formatted(GOLD);
    }
    
    public static MutableText purple(String str) {
        return Text.literal(str).formatted(LIGHT_PURPLE);
    }
    
    public static MutableText gray(String str) {
        return Text.literal(str).formatted(GRAY);
    }
    
    public static MutableText player(ServerPlayerEntity player) {
        return gold(player.getName().getString());
    }
    
    public static MutableText position(Vec3d position) {
        return gray("(%.02f, %.02f, %.02f)".formatted(position.x, position.y, position.z));
    }
    
    public static MutableText anchor(String anchorName, TeleportAnchor anchor) {
        return anchor(anchorName, anchor, false, false);
    }
    
    public static MutableText anchor(String anchorName, TeleportAnchor anchor, boolean isTemp, boolean isPublic) {
        if (isTemp) {
            return position(anchor.position());
        }
        return (isPublic ? purple(anchorName) : yellow(anchorName)).append(gray(" at ")).append(position(anchor.position()));
    }
    
    public static void teleport(ServerPlayerEntity player, ServerPlayerEntity target) {
        Vec3d position = target.getPos();
        player.teleport(target.getServerWorld(), position.x, position.y, position.z, player.getYaw(), player.getPitch());
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        target.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
    
    public static void teleport(ServerPlayerEntity player, TeleportAnchor anchor) {
        Vec3d position = anchor.position();
        player.teleport(player.getServer().getWorld(anchor.world()), position.x, position.y, position.z, player.getYaw(), player.getPitch());
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
    
    public static void send(ServerPlayerEntity player, boolean success, MutableText... texts) {
        send(player.getCommandSource(), success, texts);
    }
    
    public static void send(ServerCommandSource source, boolean success, MutableText... texts) {
        if (texts.length == 0) {
            return;
        }
        MutableText comp = MutableText.of(TextContent.EMPTY);
        for (MutableText text : texts) {
            comp.append(text);
        }
        if (success) {
            source.sendMessage(comp);
        } else {
            source.sendError(comp);
        }
    }
    
    public static void playerNotFound(ServerPlayerEntity player) {
        send(player, false, gray("Player not found."));
    }
    
    public static UUID selectPlayerID(ServerPlayerEntity player, Collection<GameProfile> profiles) {
        Iterator<GameProfile> iterator = profiles.iterator();
        if (!iterator.hasNext()) {
            playerNotFound(player);
            return null;
        }
        UUID id = iterator.next().getId();
        if (iterator.hasNext()) {
            send(player, false, red("Please specify only one player."));
            return null;
        }
        if (id.equals(player.getGameProfile().getId())) {
            send(player, false, red("Cannot teleport to yourself."));
            return null;
        }
        if (player.getServer().getPlayerManager().getPlayer(id) == null) {
            playerNotFound(player);
            return null;
        }
        return id;
    }
    
    public static void notifyRequestTimedOut(String request, MinecraftServer server, UUID sourceID, UUID targetID) {
        ServerPlayerEntity source = server.getPlayerManager().getPlayer(sourceID);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetID);
        if (source != null && target != null) {
            send(source, true, gray(request + " request to "), player(target), gray(" has timed out."));
            send(target, true, gray(request + " request from "), player(source), gray(" has timed out."));
        }
    }
    
    public static int storePublicAnchors(ServerPlayerEntity player, Map<String, TeleportAnchor> publicAnchors) {
        NbtCompound anchorData = new NbtCompound();
        for (String anchorName : publicAnchors.keySet()) {
            anchorData.put(anchorName, publicAnchors.get(anchorName).toCompound());
        }
        try {
            if (!ANCHOR_PATH.toFile().exists()) {
                Files.createFile(ANCHOR_PATH);
            }
            NbtIo.write(anchorData, ANCHOR_PATH.toFile());
        } catch (IOException e) {
            send(player, false, gray("Failed to write anchor data: "), red(e.getMessage()));
            return 0;
        }
        return 1;
    }
    
    public static int storeProperty(ServerPlayerEntity player, String key, String value) {
        send(player, true, purple(key), green(" set to "), yellow(value), green("."));
        Properties properties = new Properties();
        
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            properties.load(in);
        } catch (IOException e) {
            send(player, false, gray("Failed to read config file: "), red(e.getMessage()));
            return 0;
        }
        
        properties.setProperty(key, value);
        
        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(out, CONFIG_COMMENTS);
        } catch (IOException e) {
            send(player, false, gray("Failed to write config file: "), red(e.getMessage()));
            return 0;
        }
        
        return 1;
    }
    
    public static int restoreProperties(ServerPlayerEntity player, String[] keys, String[] values) {
        for (int i = 0; i < keys.length; i++) {
            send(player, true, purple(keys[i]), green(" set to "), yellow(values[i]), green("."));
        }
        try {
            Files.deleteIfExists(CONFIG_PATH);
            return 1;
        } catch (IOException e) {
            send(player, false, gray("Failed to write config file: "), red(e.getMessage()));
            return 0;
        }
    }
}
