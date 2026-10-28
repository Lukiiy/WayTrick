package me.lukiiy.wayTrick;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.*;

public class Tracker implements AutoCloseable {
    private static final double REALLY_FAR_SQR = 332 * 332; // from NMS (WaypointTransmitter)
    private static final float AZIMUTH_THRESHOLD = 0.008726646f; // from NMS (WaypointTransmitter)
    private static final double ACCURATE_THRESHOLD = 64; // distance used to decide whether the target will display its accurate direction
    private static final double ACCCURATE_THRESHOLD_SQR = ACCURATE_THRESHOLD * ACCURATE_THRESHOLD;

    private final LocatorBar bar;
    private final ScheduledTask task;

    private final Map<UUID, Map<UUID, State>> states = new HashMap<>();
    private final Map<TrackedPoint, Position> fixed = new IdentityHashMap<>();
    private final Map<TrackedPoint, Waypoint.Icon> icons = new IdentityHashMap<>();

    public Tracker(LocatorBar bar) {
        this.bar = bar;
        this.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(bar.plugin(), _ -> tick(), 1L, 1L);
    }

    public void register(TrackedPoint trackedPoint) {
        states.put(trackedPoint.getId(), new HashMap<>());
    }

    public void unregister(TrackedPoint trackedPoint) {
        Map<UUID, State> viewers = states.remove(trackedPoint.getId());
        if (viewers != null) viewers.forEach(Tracker::dispose);

        fixed.remove(trackedPoint);
        icons.remove(trackedPoint);
    }

    public void showAllTo(Player viewer) {
        bar.pointsView().forEach(trackedPoint -> showTo(trackedPoint, viewer));
    }

    public void showTo(TrackedPoint trackedPoint, Player viewer) {
        if (!viewer.isOnline() || !bar.contains(trackedPoint)) return;

        State state = states.computeIfAbsent(trackedPoint.getId(), ignored -> new HashMap<>()).computeIfAbsent(viewer.getUniqueId(), ignored -> new State());
        Viewer current = new Viewer(viewer);

        untrack(current.handle, state);
        sync(current, trackedPoint, resolve(trackedPoint, viewer), state);
    }

    public void hideFrom(TrackedPoint trackedPoint, Player viewer) {
        Map<UUID, State> viewers = states.get(trackedPoint.getId());
        if (viewers == null) return;

        State state = viewers.remove(viewer.getUniqueId());
        if (state != null) dispose(viewer.getUniqueId(), state);
    }

    public void hideFromAll(TrackedPoint trackedPoint) {
        Map<UUID, State> viewers = states.get(trackedPoint.getId());
        if (viewers == null) return;

        viewers.forEach(Tracker::dispose);
        viewers.clear();
    }

    public void hideAllFrom(Player viewer) {
        bar.pointsView().forEach(trackedPoint -> hideFrom(trackedPoint, viewer));
    }

    public void changed(TrackedPoint trackedPoint) {
        fixed.remove(trackedPoint);
        icons.remove(trackedPoint);

        Map<UUID, State> viewers = states.get(trackedPoint.getId());
        if (viewers == null) return;

        Iterator<Map.Entry<UUID, State>> iterator = viewers.entrySet().iterator();

        while (iterator.hasNext()) {
            Player viewer = Bukkit.getPlayer(iterator.next().getKey());

            if (viewer == null || !viewer.isOnline()) {
                iterator.remove();
            } else {
                showTo(trackedPoint, viewer); // re-sends with fresh icon / wire id
            }
        }
    }

    @Override
    public void close() {
        task.cancel();

        states.values().forEach(viewers -> viewers.forEach(Tracker::dispose));

        states.clear();
        fixed.clear();
        icons.clear();
    }

    private void tick() {
        var waypoints = bar.pointsView();
        if (waypoints.isEmpty()) return;

        // One snapshot per viewer / target player per pass, shared across waypoints.
        Map<UUID, Viewer> viewerCache = new HashMap<>();
        Set<UUID> offline = new HashSet<>();
        Map<UUID, Position> targetPositions = new HashMap<>();

        for (TrackedPoint trackedPoint : waypoints) {
            Map<UUID, State> viewers = states.get(trackedPoint.getId());
            if (viewers == null || viewers.isEmpty()) continue;

            boolean perViewer = trackedPoint.isClientPosition();
            Position shared = perViewer ? null : resolveShared(trackedPoint, targetPositions);

            Iterator<Map.Entry<UUID, State>> iterator = viewers.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, State> entry = iterator.next();

                Viewer viewer = viewerOf(entry.getKey(), viewerCache, offline);
                if (viewer == null) {
                    iterator.remove();
                    continue;
                }

                Position target = perViewer ? snapshot(trackedPoint.resolvePosition(viewer.player)) : shared;
                sync(viewer, trackedPoint, target, entry.getValue());
            }
        }
    }

    private static Viewer viewerOf(UUID id, Map<UUID, Viewer> cache, Set<UUID> offline) {
        Viewer viewer = cache.get(id);
        if (viewer != null || offline.contains(id)) return viewer;

        Player player = Bukkit.getPlayer(id);

        if (player == null || !player.isOnline()) {
            offline.add(id);
            return null;
        }

        viewer = new Viewer(player);
        cache.put(id, viewer);
        return viewer;
    }

    private Position resolve(TrackedPoint trackedPoint, Player viewer) {
        if (trackedPoint.isClientPosition()) return snapshot(trackedPoint.resolvePosition(viewer));

        return resolveShared(trackedPoint, new HashMap<>());
    }

    private Position resolveShared(TrackedPoint trackedPoint, Map<UUID, Position> targetPositions) {
        Player target = trackedPoint.targetPlayer();

        if (target != null) {
            return targetPositions.computeIfAbsent(target.getUniqueId(), id -> snapshot(target));
        }

        return fixed.computeIfAbsent(trackedPoint, w -> snapshot(w.resolvePosition(null)));
    }

    /** Single entry point: drop, track or update depending on target + current state. */
    private void sync(Viewer viewer, TrackedPoint trackedPoint, Position target, State state) {
        if (!usable(target, viewer)) {
            untrack(viewer.handle, state);
            return;
        }

        Position aimed = aim(viewer, trackedPoint, target);

        if (state.mode == null) {
            track(viewer, trackedPoint, aimed, state);
        } else {
            update(viewer, trackedPoint, aimed, state);
        }
    }

    /**
     * Player targets: untouched. Client finds the entity by UUID and uses its
     * interpolated eye position while the sent block is within 3 blocks of it.
     * Everything else: push close targets out along the exact ray.
     */
    private static Position aim(Viewer viewer, TrackedPoint trackedPoint, Position target) {
        if (trackedPoint.targetPlayer() != null) return target;

        Vec3 exact = target.vector.add(0, .9, 0);
        Vec3 delta = exact.subtract(viewer.eye);
        double distSqr = delta.lengthSqr();

        if (distSqr >= ACCCURATE_THRESHOLD_SQR || distSqr < 1.0e-4) return target;

        Position result = snapshot(target.worldId, viewer.eye.add(delta.scale(ACCURATE_THRESHOLD / Math.sqrt(distSqr))));

        return WaypointTransmitter.isChunkVisible(result.chunk, viewer.handle) ? result : target;
    }

    private void track(Viewer viewer, TrackedPoint trackedPoint, Position target, State state) {
        UUID id = wireId(trackedPoint);
        Waypoint.Icon icon = icon(trackedPoint);
        state.wire = id;

        if (viewer.position.distanceToSqr(target.vector) > REALLY_FAR_SQR) {
            state.mode = Mode.AZIMUTH;
            state.angle = angle(viewer.position, target.vector);

            send(viewer.handle, ClientboundTrackedWaypointPacket.addWaypointAzimuth(id, icon, state.angle));
        } else if (WaypointTransmitter.isChunkVisible(target.chunk, viewer.handle)) {
            state.mode = Mode.BLOCK;
            state.pos = target.block;

            send(viewer.handle, ClientboundTrackedWaypointPacket.addWaypointPosition(id, icon, target.block));
        } else {
            state.mode = Mode.CHUNK;
            state.chunk = target.chunk;

            send(viewer.handle, ClientboundTrackedWaypointPacket.addWaypointChunk(id, icon, target.chunk));
        }
    }

    private void update(Viewer viewer, TrackedPoint trackedPoint, Position target, State state) {
        UUID id = state.wire;
        Waypoint.Icon icon = icon(trackedPoint);
        Mode wanted;

        if (viewer.position.distanceToSqr(target.vector) > REALLY_FAR_SQR) wanted = Mode.AZIMUTH;
        else if (WaypointTransmitter.isChunkVisible(target.chunk, viewer.handle)) wanted = Mode.BLOCK;
        else wanted = Mode.CHUNK;

        if (wanted != state.mode) {
            untrack(viewer.handle, state);
            track(viewer, trackedPoint, target, state);

            return;
        }

        switch (wanted) {
            case AZIMUTH -> {
                float angle = angle(viewer.position, target.vector);

                if (angleDifference(angle, state.angle) > AZIMUTH_THRESHOLD) {
                    state.angle = angle;
                    send(viewer.handle, ClientboundTrackedWaypointPacket.updateWaypointAzimuth(id, icon, angle));
                }
            }

            case BLOCK -> {
                if (target.block.equals(state.pos)) return;

                state.pos = target.block;

                send(viewer.handle, ClientboundTrackedWaypointPacket.updateWaypointPosition(id, icon, target.block));
            }

            case CHUNK -> {
                if (target.chunk.equals(state.chunk)) return;

                state.chunk = target.chunk;

                send(viewer.handle, ClientboundTrackedWaypointPacket.updateWaypointChunk(id, icon, target.chunk));
            }
        }
    }

    /**
     * Gets the target's uuid for the first point targeting them; Duplicates use their own ids to avoid clientside issues.
     * @param trackedPoint A waypoint
     * @return A UUID
     */
    private UUID wireId(TrackedPoint trackedPoint) {
        Player target = trackedPoint.targetPlayer();
        if (target == null) return trackedPoint.getId();

        UUID targetId = target.getUniqueId();

        for (TrackedPoint other : bar.pointsView()) {
            if (other == trackedPoint) break;

            Player otherP = other.targetPlayer();
            if (otherP != null && otherP.getUniqueId().equals(targetId)) return trackedPoint.getId();
        }

        return targetId;
    }

    /** remove from client if currently shown, reset state. */
    private static void untrack(ServerPlayer handle, State state) {
        if (state.mode == null) return;

        send(handle, ClientboundTrackedWaypointPacket.removeWaypoint(state.wire));

        state.mode = null;
    }

    private static void dispose(UUID viewerId, State state) {
        if (state.mode == null) return;

        Player viewer = Bukkit.getPlayer(viewerId);

        if (viewer != null && viewer.isOnline()) untrack(NMSUtils.handler(viewer), state); else state.mode = null;
    }

    private static Position snapshot(Player player) {
        if (player == null || !player.isOnline()) return null;

        return snapshot(player.getWorld().getUID(), NMSUtils.handler(player).position());
    }

    private static Position snapshot(Location location) {
        if (location == null || location.getWorld() == null) return null;

        return snapshot(location.getWorld().getUID(), new Vec3(location.getX(), location.getY(), location.getZ()));
    }

    private static Position snapshot(UUID worldId, Vec3 vector) {
        BlockPos block = BlockPos.containing(vector);

        return new Position(worldId, vector, block, ChunkPos.containing(block));
    }

    private static boolean usable(Position target, Viewer viewer) {
        return target != null && target.worldId.equals(viewer.worldId);
    }

    private Waypoint.Icon icon(TrackedPoint trackedPoint) {
        return icons.computeIfAbsent(trackedPoint, this::createIcon);
    }

    private Waypoint.Icon createIcon(TrackedPoint trackedPoint) {
        Waypoint.Icon icon = new Waypoint.Icon();
        Color color = trackedPoint.color();

        icon.style = styleKey(trackedPoint.style());
        if (color != null) icon.color = Optional.of((color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue());

        return icon;
    }

    private static ResourceKey<WaypointStyleAsset> styleKey(NamespacedKey key) {
        if (key == null) return WaypointStyleAssets.DEFAULT;

        Identifier id = Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());

        return ResourceKey.create(WaypointStyleAssets.ROOT_ID, id);
    }

    private static float angle(Vec3 viewer, Vec3 target) {
        Vec3 vector = viewer.subtract(target).yRot((float) (-Math.PI / 2));

        return (float) Mth.atan2(vector.z, vector.x);
    }

    private static float angleDifference(float a, float b) {
        float delta = a - b;

        while (delta > Math.PI) delta -= (float) (Math.PI * 2);
        while (delta < -Math.PI) delta += (float) (Math.PI * 2);

        return Math.abs(delta);
    }

    private static void send(ServerPlayer viewer, Packet<?> packet) {
        viewer.connection.send(packet);
    }

    private enum Mode {
        BLOCK,
        CHUNK,
        AZIMUTH
    }

    private static final class State {
        UUID wire;
        Mode mode;
        BlockPos pos;
        ChunkPos chunk;
        float angle;
    }

    private record Position(UUID worldId, Vec3 vector, BlockPos block, ChunkPos chunk) {}
}