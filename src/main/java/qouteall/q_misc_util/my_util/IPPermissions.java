package qouteall.q_misc_util.my_util;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;

/**
 * Maps the old integer permission levels to the permission system of Minecraft 26.
 */
public final class IPPermissions {
    public static Permission level(int level) {
        return switch (level) {
            case 0, 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
    }
}
