package me.lukiiy.wayTrick;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
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
        targets.values().forEach(t -> t.sendWaypoint(viewer));
    }

    /**
     * Hide the waypoints from a player
     * @param viewer The player
     */
    public void removeViewer(Player viewer) {
        viewers.remove(viewer);

        targets.forEach((id, tracked) -> {
            tracked.clear(viewer);
            send(viewer, ClientboundTrackedWaypointPacket.removeWaypoint(id));
        });
    }

    /**
     * Track a target
     * @param target The target player
     * @param style The waypoint style
     */
    public void trackTarget(Player target, Waypoint.Icon style) {
        TrackedTarget tracked = new TrackedTarget(target, style, null);

        targets.put(target.getUniqueId(), tracked);
        viewers.forEach(tracked::sendWaypoint);
    }

    /**
     * Track a target with a custom position function
     * @param target The target player
     * @param style The waypoint style
     * @param positionMethod A function to calculate positioning
     */
    public void trackTarget(Player target, Waypoint.Icon style, BiFunction<Player, Player, Vec3> positionMethod) {
        TrackedTarget tracked = new TrackedTarget(target, style, positionMethod);

        targets.put(target.getUniqueId(), tracked);
        viewers.forEach(tracked::sendWaypoint);
    }

    /**
     * Stop tracking a player
     * @param target The player being tracked
     */
    public void untrackTarget(Player target) {
        UUID id = target.getUniqueId();
        TrackedTarget tracked = targets.remove(id);
        if (tracked == null) return;

        viewers.forEach(viewer -> {
            tracked.clear(viewer);

            send(viewer, ClientboundTrackedWaypointPacket.removeWaypoint(id));
        });
    }

    public void updateAll() {
        targets.values().forEach(t -> t.update(viewers));
    }

    public void clear() {
        targets.forEach((id, tracked) -> {
            tracked.clear();
            viewers.forEach(it -> send(it, ClientboundTrackedWaypointPacket.removeWaypoint(id)));
        });

        targets.clear();
        viewers.clear();
    }

    private static void send(Player viewer, Packet<?> packet) {
        NMSUtils.handler(viewer).connection.send(packet);
    }
}
