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
    private final UUID pointId = UUID.randomUUID();
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
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        ViewerState state = viewerState.computeIfAbsent(viewer, v -> new ViewerState());

        if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
            float angle = calcAzimuth(craftViewer.position(), targetPos);

            state.mode = Mode.AZIMUTH;
            state.lastAngle = angle;

            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(pointId, style, angle));
        } else if (WaypointTransmitter.isChunkVisible(chunkPos, craftViewer)) {
            state.mode = Mode.BLOCK;
            state.lastPos = blockPos;

            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(pointId, style, blockPos));
        } else {
            state.mode = Mode.CHUNK;
            state.lastChunk = chunkPos;

            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(pointId, style, chunkPos));
        }
    }

    public void update(Set<Player> viewers) {
        ServerPlayer craftTarget = positionFun == null ? NMSUtils.handler(target) : null;

        for (Player viewer : viewers) {
            ServerPlayer craftViewer = NMSUtils.handler(viewer);
            Vec3 targetPos = positionFun != null ? positionFun.apply(target, viewer) : craftTarget.position();
            BlockPos pos = BlockPos.containing(targetPos);
            ChunkPos chunkPos = ChunkPos.containing(pos);
            ViewerState state = viewerState.get(viewer);

            if (state == null || state.mode == null) {
                sendWaypoint(viewer);
                continue;
            }

            Mode mode = state.mode;

            if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
                if (mode != Mode.AZIMUTH) {
                    state.mode = Mode.AZIMUTH;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(pointId));

                    float angle = calcAzimuth(craftViewer.position(), targetPos);

                    state.lastAngle = angle;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(pointId, style, angle));
                } else {
                    float neo = calcAzimuth(craftViewer.position(), targetPos);

                    if (Mth.abs(Mth.wrapDegrees((float) Math.toDegrees(neo - state.lastAngle))) > .5f) {
                        state.lastAngle = neo;

                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(pointId, style, neo));
                    }
                }
            } else if (WaypointTransmitter.isChunkVisible(chunkPos, craftViewer)) {
                if (mode != Mode.BLOCK) {
                    state.mode = Mode.BLOCK;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(pointId));

                    state.lastPos = pos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(pointId, style, pos));
                } else if (!pos.equals(state.lastPos)) {
                    state.lastPos = pos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(pointId, style, pos));
                }
            } else {
                if (mode != Mode.CHUNK) {
                    state.mode = Mode.CHUNK;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(pointId));

                    state.lastChunk = chunkPos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(pointId, style, chunkPos));
                } else if (chunkPos.getChessboardDistance(state.lastChunk) > 0) {
                    state.lastChunk = chunkPos;
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(pointId, style, chunkPos));
                }
            }
        }
    }

    private static class ViewerState {
        BlockPos lastPos;
        ChunkPos lastChunk;
        float lastAngle;
        Mode mode;
    }

    public enum Mode {
        BLOCK,
        CHUNK,
        AZIMUTH
    }

    private float calcAzimuth(Vec3 viewerPos, Vec3 targetPos) {
        Vec3 vector = viewerPos.subtract(targetPos).yRot((float) -Math.PI / 2);

        return (float) Mth.atan2(vector.z, vector.x);
    }

    public void clear() {
        viewerState.clear();
    }

    public void clear(Player viewer) {
        viewerState.remove(viewer);
    }
}
