package me.lukiiy.wayTrick;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

class NMSUtils {
    public static ServerPlayer handler(Player p) {
        return ((CraftPlayer) p).getHandle();
    }
}
