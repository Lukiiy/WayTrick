package me.lukiiy.wayTrick;

import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiFunction;

public class WayTrick {
    private final Map<UUID, TrackedTarget> targets = new HashMap<>();
    private final Set<Player> viewers = new HashSet<>();

    /**
     * Show the waypoints to a player
     * @param viewer The player
     */
    public void addViewer(Player viewer) {
        viewers.add(viewer);
        targets.forEach((targetId, target) -> target.sendConnect(viewer));
    }

    /**
     * Hide the waypoints from a player
     * @param viewer The player
     */
    public void removeViewer(Player viewer) {
        viewers.remove(viewer);
        targets.keySet().forEach(targetId -> removeWaypoint(viewer, targetId));
    }

    /**
     * Track a target
     * @param target The target player
     * @param style The waypoint style
     */
    public void trackTarget(Player target, Waypoint.Icon style) {
        UUID targetId = target.getUniqueId();
        TrackedTarget tracked = new TrackedTarget(target, style, null);

        targets.put(targetId, tracked);
        viewers.forEach(tracked::sendConnect);
    }

    /**
     * Track a target with a custom position function
     * @param target The target player
     * @param style The waypoint style
     * @param positionMethod A function to calculate positioning
     */
    public void trackTarget(Player target, Waypoint.Icon style, BiFunction<Player, Player, Vec3> positionMethod) {
        UUID targetId = target.getUniqueId();
        TrackedTarget tracked = new TrackedTarget(target, style, positionMethod);

        targets.put(targetId, tracked);
        viewers.forEach(tracked::sendConnect);
    }

    /**
     * Stop tracking a player
     * @param target The player being tracked
     */
    public void untrackTarget(Player target) {
        UUID targetId = target.getUniqueId();
        TrackedTarget tracked = targets.remove(targetId);

        if (tracked != null) viewers.forEach(viewer -> removeWaypoint(viewer, targetId));
    }

    public void updateAll() {
        targets.values().forEach(target -> target.update(viewers));
    }

    public void clear() {
        targets.keySet().forEach(targetId -> viewers.forEach(viewer -> removeWaypoint(viewer, targetId)));
        targets.clear();
        viewers.clear();
    }

    private void removeWaypoint(Player viewer, UUID targetId) {
        ((CraftPlayer) viewer).getHandle().connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(targetId));
    }
}
