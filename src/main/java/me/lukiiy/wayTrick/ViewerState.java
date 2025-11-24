package me.lukiiy.wayTrick;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class ViewerState {
    BlockPos lastPos;
    ChunkPos lastChunk;
    Float lastAngle;
    TrackedTarget.Mode mode;
}
