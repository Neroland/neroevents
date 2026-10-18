package za.co.neroland.neroevents.creatures;

import java.util.Optional;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroevents.event.ServerPayloadContext;

/**
 * One source of raid targets. A target is an <b>approach anchor</b>: a surface position outside the
 * settlement's protected footprint, {@code protectedRadius + spawnRadiusMax} from its centre in a seeded
 * direction, so the spawn ring sits just beyond the wall and the mobs walk in. Never published.
 *
 * <p>The designed order is colony or outpost, then station, then installation, then the density anchor.
 * No provider is registered yet: colonies and installations cannot be enumerated, and a station target
 * would be refused by station protection, so every raid anchors where players are.
 */
public interface RaidTargetProvider {

    String id();

    Optional<BlockPos> approachAnchor(ServerPayloadContext ctx, int spawnRadiusMax);
}
