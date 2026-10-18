package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.create.CreateAssemblySupport;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create train assembly at a station → engineering XP (NeoForge-only,
 * gated by the {@code lifepath.compat.create.mixins.json} config's
 * {@code requiredMods}). Verified against Create 6.0.10 bytecode:
 * {@code StationBlockEntity.assemble(UUID)} is the single server-side
 * assembly path — {@code StationEditPacket.applySettings} calls it with
 * the initiating player's {@code getUUID()} when its {@code tryAssemble}
 * flag is set. The method early-returns through a dozen validation and
 * {@code AssemblyException} paths; the one committed-success point is the
 * {@code Create.RAILWAYS.addTrain(train)} invoke near the tail (bytecode
 * ~1760), after the train is constructed, its station reservation made,
 * and its occupied signal blocks collected. Injecting there fires exactly
 * once per assembled train.
 *
 * <p>The UUID parameter is the initiating player — null-checked because
 * ComputerCraft's {@code StationPeripheral.assemble()} calls the same
 * method with a literal {@code null} owner (verified: {@code aconst_null}
 * at the call site), and unattended computer-driven assembly must stay
 * NON_PLAYER. The player is resolved through the block entity's own
 * level via the public vanilla {@code getLevel()}/{@code getBlockPos()}
 * ({@code StationBlockEntity} is a {@code SmartBlockEntity} →
 * {@code BlockEntity}); no Create types are referenced.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.station.StationBlockEntity",
		remap = false)
public abstract class CreateStationAssembleMixin {

	@Inject(method = "assemble(Ljava/util/UUID;)V",
			at = @At(value = "INVOKE",
					target = "Lcom/simibubi/create/content/trains/GlobalRailwayManager;addTrain(Lcom/simibubi/create/content/trains/entity/Train;)V",
					remap = false),
			remap = false, require = 0)
	private void lifepath$onTrainAssembled(UUID ownerUuid, CallbackInfo ci) {
		if (ownerUuid == null) {
			// ComputerCraft-driven assembly — no player to attribute.
			return;
		}
		try {
			BlockEntity self = (BlockEntity) (Object) this;
			Level level = self.getLevel();
			if (level == null || level.isClientSide()
					|| !(level.getPlayerByUUID(ownerUuid)
							instanceof ServerPlayer player)) {
				return;
			}
			CreateAssemblySupport.onAssembleRequest(player, self.getBlockPos(),
					ResourceLocation.fromNamespaceAndPath("create",
							"track_station"),
					"station");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("create station assemble seam failed", t);
		}
	}
}
