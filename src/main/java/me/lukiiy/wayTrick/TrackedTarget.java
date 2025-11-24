package me.lukiiy.wayTrick;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.bukkit.craftbukkit.entity.CraftPlayer;
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
    private final Map<Player, BlockPos> viewerLastPos = new HashMap<>();
    private final Map<Player, ChunkPos> viewerLastChunk = new HashMap<>();
    private final Map<Player, Float> viewerLastAngle = new HashMap<>();
    private final Map<Player, TrackingMode> viewerMode = new HashMap<>();

    private static final float VERY_FAR = 332f; // from NMS
    private static final float AZIMUTH_ANGLE_THRESHOLD = 0.008726646f;

    TrackedTarget(Player target, Waypoint.Icon style, BiFunction<Player, Player, Vec3> positionMethod) {
        this.target = target;
        this.style = style;
        this.positionFun = positionMethod;
    }

    public void sendConnect(Player viewer) {
        ServerPlayer craftViewer = handler(viewer);
        Vec3 targetPos = positionFun != null ? positionFun.apply(target, viewer) : handler(target).position();
        BlockPos blockPos = BlockPos.containing(targetPos);
        ChunkPos chunkPos = new ChunkPos(blockPos);

        if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
            viewerMode.put(viewer, TrackingMode.AZIMUTH);

            float angle = calculateAzimuth(craftViewer.position(), targetPos);

            viewerLastAngle.put(viewer, angle);
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(target.getUniqueId(), style, angle));
        } else if (isChunkLoaded(viewer, chunkPos)) {
            viewerMode.put(viewer, TrackingMode.BLOCK);
            viewerLastPos.put(viewer, blockPos);
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(target.getUniqueId(), style, blockPos));
        } else {
            viewerMode.put(viewer, TrackingMode.CHUNK);
            viewerLastChunk.put(viewer, chunkPos);
            craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(target.getUniqueId(), style, chunkPos));
        }
    }

    void update(Set<Player> viewers) {
        ServerPlayer craftTarget = handler(target);

        for (Player viewer : viewers) {
            ServerPlayer craftViewer = handler(viewer);
            Vec3 targetPos = positionFun != null ? positionFun.apply(target, viewer) : craftTarget.position();
            BlockPos pos = BlockPos.containing(targetPos);
            ChunkPos chunkPos = new ChunkPos(pos);
            TrackingMode mode = viewerMode.getOrDefault(viewer, TrackingMode.BLOCK);

            if (craftViewer.position().distanceTo(targetPos) > VERY_FAR) {
                if (mode != TrackingMode.AZIMUTH) {
                    viewerMode.put(viewer, TrackingMode.AZIMUTH);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(target.getUniqueId()));

                    float angle = calculateAzimuth(craftViewer.position(), targetPos);

                    viewerLastAngle.put(viewer, angle);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointAzimuth(target.getUniqueId(), style, angle));
                } else {
                    float old = viewerLastAngle.getOrDefault(viewer, 0f);
                    float neo = calculateAzimuth(craftViewer.position(), targetPos);

                    if (Math.abs(neo - old) > AZIMUTH_ANGLE_THRESHOLD) {
                        viewerLastAngle.put(viewer, neo);
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointAzimuth(target.getUniqueId(), style, neo));
                    }
                }
            } else if (isChunkLoaded(viewer, chunkPos)) {
                if (mode != TrackingMode.BLOCK) {
                    viewerMode.put(viewer, TrackingMode.BLOCK);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(target.getUniqueId()));
                    viewerLastPos.put(viewer, pos);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(target.getUniqueId(), style, pos));
                } else {
                    BlockPos lastPos = viewerLastPos.get(viewer);

                    if (!pos.equals(lastPos)) {
                        viewerLastPos.put(viewer, pos);
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(target.getUniqueId(), style, pos));
                    }
                }
            } else {
                if (mode != TrackingMode.CHUNK) {
                    viewerMode.put(viewer, TrackingMode.CHUNK);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(target.getUniqueId()));
                    viewerLastChunk.put(viewer, chunkPos);
                    craftViewer.connection.send(ClientboundTrackedWaypointPacket.addWaypointChunk(target.getUniqueId(), style, chunkPos));
                } else {
                    ChunkPos lastChunk = viewerLastChunk.get(viewer);

                    if (chunkPos.getChessboardDistance(lastChunk) > 0) {
                        viewerLastChunk.put(viewer, chunkPos);
                        craftViewer.connection.send(ClientboundTrackedWaypointPacket.updateWaypointChunk(target.getUniqueId(), style, chunkPos));
                    }
                }
            }
        }
    }

    private enum TrackingMode {
        BLOCK, CHUNK, AZIMUTH;
    }

    private float calculateAzimuth(Vec3 viewerPos, Vec3 targetPos) {
        Vec3 vector = viewerPos.subtract(targetPos).yRot(-90);

        return (float) Mth.atan2(vector.z, vector.x);
    }

    private boolean isChunkLoaded(Player viewer, ChunkPos pos) {
        return WaypointTransmitter.isChunkVisible(pos, handler(viewer));
    }

    private static ServerPlayer handler(Player p) {
        return ((CraftPlayer) p).getHandle();
    }
}
