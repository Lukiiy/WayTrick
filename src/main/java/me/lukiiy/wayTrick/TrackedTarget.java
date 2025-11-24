package me.lukiiy.wayTrick;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

class TrackedTarget {
    private final Player target;
    private final Waypoint.Icon style;
    private final BiFunction<Player, Player, Vec3> positionFun;
    private final Map<Player, ViewerState> viewerState = new HashMap<>();

    private static final float VERY_FAR = 332f; // from NMS
    private static final float AZIMUTH_ANGLE_THRESHOLD = 0.008726646f; // from NMS

    TrackedTarget(Player target, Waypoint.Icon style, BiFunction<Player, Player, Vec3> positionMethod) {
        this.target = target;
        this.style = style;
        this.positionFun = positionMethod;
    }

    public void sendWaypoint(Player viewer) {
        ServerPlayer craftViewer = NMSUtils.handler(viewer);
        Vec3 targetPos = positionFun != null ? positionFun.apply(target, viewer) : NMSUtils.handler(target).position();
        BlockPos blockPos = BlockPos.containing(targetPos);
        ChunkPos chunkPos = new ChunkPos(blockPos);
        UUID targetId = target.getUniqueId();

        ViewerState state = viewerState.computeIfAbsent(viewer, v -> new ViewerState());

        if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
            float angle = calcAzimuth(craftViewer.position(), targetPos);

            state.mode = Mode.AZIMUTH;
            state.lastAngle = angle;
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(targetId, style, angle));
        } else if (isChunkLoaded(viewer, chunkPos)) {
            state.mode = Mode.BLOCK;
            state.lastPos = blockPos;
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(targetId, style, blockPos));
        } else {
            state.mode = Mode.CHUNK;
            state.lastChunk = chunkPos;
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(targetId, style, chunkPos));
        }
    }

    void update(Set<Player> viewers) {
        ServerPlayer craftTarget = NMSUtils.handler(target);

        for (Player viewer : viewers) {
            ServerPlayer craftViewer = NMSUtils.handler(viewer);
            Vec3 targetPos = positionFun != null ? positionFun.apply(target, viewer) : craftTarget.position();
            BlockPos pos = BlockPos.containing(targetPos);
            ChunkPos chunkPos = new ChunkPos(pos);
            ViewerState state = viewerState.get(viewer);
            Mode mode = state.mode == null ? Mode.BLOCK : state.mode;
            UUID targetId = target.getUniqueId();

            if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
                if (mode != Mode.AZIMUTH) {
                    state.mode = Mode.AZIMUTH;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(targetId));

                    float angle = calcAzimuth(craftViewer.position(), targetPos);

                    state.lastAngle = angle;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(targetId, style, angle));

                    state.lastPos = null;
                    state.lastChunk = null;
                } else {
                    float old = state.lastAngle == null ? 0f : state.lastAngle;
                    float neo = calcAzimuth(craftViewer.position(), targetPos);

                    if (Math.abs(neo - old) > AZIMUTH_ANGLE_THRESHOLD) {
                        state.lastAngle = neo;
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(targetId, style, neo));
                    }
                }
            } else if (isChunkLoaded(viewer, chunkPos)) {
                if (mode != Mode.BLOCK) {
                    state.mode = Mode.BLOCK;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(targetId));
                    state.lastPos = pos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(targetId, style, pos));

                    state.lastChunk = null;
                    state.lastAngle = null;
                } else {
                    BlockPos lastPos = state.lastPos;

                    if (!pos.equals(lastPos)) {
                        state.lastPos = pos;
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(targetId, style, pos));
                    }
                }
            } else {
                if (mode != Mode.CHUNK) {
                    state.mode = Mode.CHUNK;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(targetId));
                    state.lastChunk = chunkPos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(targetId, style, chunkPos));

                    state.lastPos = null;
                    state.lastAngle = null;
                } else {
                    ChunkPos lastChunk = state.lastChunk;

                    if (lastChunk == null || chunkPos.getChessboardDistance(lastChunk) > 0) {
                        state.lastChunk = chunkPos;
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(targetId, style, chunkPos));
                    }
                }
            }
        }
    }

    public enum Mode {
        BLOCK, CHUNK, AZIMUTH
    }

    private float calcAzimuth(Vec3 viewerPos, Vec3 targetPos) {
        Vec3 vector = viewerPos.subtract(targetPos).yRot(-90);

        return (float) Mth.atan2(vector.z, vector.x);
    }

    private boolean isChunkLoaded(Player viewer, ChunkPos pos) {
        return WaypointTransmitter.isChunkVisible(pos, NMSUtils.handler(viewer));
    }

    public void clear() {
        viewerState.clear();
    }

    public void clear(Player viewer) {
        viewerState.remove(viewer);
    }
}
