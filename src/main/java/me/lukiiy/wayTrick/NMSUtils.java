package me.lukiiy.wayTrick;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Optional;

class NMSUtils {
    /**
     * [Internal] Get a player's NMS ServerPlayer
     * @param p The player
     * @return Their {@link ServerPlayer}
     */
    public static ServerPlayer handler(Player p) {
        return ((CraftPlayer) p).getHandle();
    }

    /**
     * A helper function because NMS Icon has its fields public but not its constructor
     * @param style The style (assets)
     * @param color The color
     * @return An {@link Waypoint.Icon}
     */
    public static Waypoint.Icon icon(ResourceKey<WaypointStyleAsset> style, int color) {
        Waypoint.Icon icon = new Waypoint.Icon();

        icon.style = style;
        icon.color = Optional.of(color);

        return icon;
    }
}
