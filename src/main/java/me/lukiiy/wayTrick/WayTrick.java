package me.lukiiy.wayTrick;

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

        for (Map.Entry<UUID, TrackedTarget> e : targets.entrySet()) {
            e.getValue().clear(viewer);
            removeWaypoint(viewer, e.getKey());
        }
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
        viewers.forEach(tracked::sendWaypoint);
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

        Set<Player> viewersSnapshot = Set.copyOf(viewers);

        viewersSnapshot.forEach(viewer -> {
            tracked.clear(viewer);
            removeWaypoint(viewer, id);
        });
    }

    public void updateAll() {
        Set<Player> viewerSnapshot = Set.copyOf(viewers);
        Collection<TrackedTarget> targets = List.copyOf(this.targets.values());

        targets.forEach(t -> t.update(viewerSnapshot));
    }

    public void clear() {
        targets.keySet().forEach(id -> viewers.forEach(viewer -> removeWaypoint(viewer, id)));
        targets.clear();
        viewers.clear();
    }

    private void removeWaypoint(Player viewer, UUID targetId) {
        NMSUtils.handler(viewer).connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(targetId));
    }
}
