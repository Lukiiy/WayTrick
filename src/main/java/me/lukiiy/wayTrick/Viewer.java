package me.lukiiy.wayTrick;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.bukkit.entity.Player;

import java.util.UUID;

class Viewer {
    public final Player player;
    public final ServerPlayer handle;
    public final Vec3 position;
    public final Vec3 eye;
    public final UUID worldId;

    Viewer(Player player) {
        this.player = player;
        this.handle = NMSUtils.handler(player);
        this.position = handle.position();
        this.eye = handle.getEyePosition();
        this.worldId = player.getWorld().getUID();
    }
}