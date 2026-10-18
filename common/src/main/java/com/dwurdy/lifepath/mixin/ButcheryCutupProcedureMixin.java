package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.butchery.ButcheryProcessing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Butchery knife-cutup XP seam (NeoForge-only, gated by the {@code
 * lifepath.compat.butchery.mixins.json} config's {@code requiredMods}).
 * Verified against butchery 5.2 bytecode: every carcass right-clicked with a
 * knife-tag item routes through one of 138 generated
 * {@code *cutupProcedure} classes. Each guards "clicked block == my carcass
 * variant" inside {@code execute}, then converts the block and damages the
 * knife — {@code ItemStack.hurtAndBreak(I, ServerLevel, ServerPlayer,
 * Consumer)} is the commit marker in all 138 (the five classes without the
 * private {@code execute(Event, ...)} delegate carry it in the public body
 * instead, so both overloads are injected).
 *
 * <p>The carcass subject is captured at HEAD because the success branch
 * rewrites it to the drained variant before the marker fires. The emit
 * consumes the capture — variant-branch layout can reach the marker more
 * than once per call, never more than once per click. BOTH execute
 * overloads capture: the {@code onRightClickBlock} subscriber enters the
 * private 6-arg body directly (bytecode-verified), while procedure-chains
 * enter the public 5-arg wrapper — which delegates into the same 6-arg
 * body, so the second HEAD capture just re-reads the same block.
 */
@Pseudo
@Mixin(targets = {
		"net.mcreator.butchery.procedures.AxolotlcutupProcedure",
		"net.mcreator.butchery.procedures.BatCutupProcedure",
		"net.mcreator.butchery.procedures.BeecutupProcedure",
		"net.mcreator.butchery.procedures.BrownmooshroomcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.CamelcutupProcedure",
		"net.mcreator.butchery.procedures.CatcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.CavespidercutupProcedure",
		"net.mcreator.butchery.procedures.ChickencutupProcedure",
		"net.mcreator.butchery.procedures.CodcutupProcedure",
		"net.mcreator.butchery.procedures.CowcutupProcedure",
		"net.mcreator.butchery.procedures.CreepercarcasscutupProcedure",
		"net.mcreator.butchery.procedures.DolphincutupProcedure",
		"net.mcreator.butchery.procedures.DonkeycarcasscutupProcedure",
		"net.mcreator.butchery.procedures.DrownedcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.ElderguardiancutupProcedure",
		"net.mcreator.butchery.procedures.EnderdragoncutupProcedure",
		"net.mcreator.butchery.procedures.EndermancutupProcedure",
		"net.mcreator.butchery.procedures.EndermitecutupProcedure",
		"net.mcreator.butchery.procedures.EvokercutupProcedure",
		"net.mcreator.butchery.procedures.FoxcutupProcedure",
		"net.mcreator.butchery.procedures.FrogcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.GlowsquidcutupProcedure",
		"net.mcreator.butchery.procedures.GoatCutupProcedure",
		"net.mcreator.butchery.procedures.GuardiancutupProcedure",
		"net.mcreator.butchery.procedures.HangingAxolotlcutupProcedure",
		"net.mcreator.butchery.procedures.HangingbatcutupProcedure",
		"net.mcreator.butchery.procedures.HangingBrownmooshroomcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingcamelcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingcatcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingcavespidercutupProcedure",
		"net.mcreator.butchery.procedures.HangingchickencutupProcedure",
		"net.mcreator.butchery.procedures.HangingcowcutupProcedure",
		"net.mcreator.butchery.procedures.HangingcreepercarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingdolphincutupProcedure",
		"net.mcreator.butchery.procedures.HangingDonkeycarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingdrownedcutupProcedure",
		"net.mcreator.butchery.procedures.HangingendermancutupProcedure",
		"net.mcreator.butchery.procedures.HangingevokercutupProcedure",
		"net.mcreator.butchery.procedures.HangingfoxcutupProcedure",
		"net.mcreator.butchery.procedures.HangingglowsquidcutupProcedure",
		"net.mcreator.butchery.procedures.HanginggoatcutupProcedure",
		"net.mcreator.butchery.procedures.HangingguardiancutupProcedure",
		"net.mcreator.butchery.procedures.HanginghoglincutupProcedure",
		"net.mcreator.butchery.procedures.HanginghuskcutupProcedure",
		"net.mcreator.butchery.procedures.HangingllamacutupProcedure",
		"net.mcreator.butchery.procedures.HangingMulecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingocelotcutupProcedure",
		"net.mcreator.butchery.procedures.HangingPandacarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingphantomcutupProcedure",
		"net.mcreator.butchery.procedures.HangingpigcutupProcedure",
		"net.mcreator.butchery.procedures.HangingpiglinbrutecutupProcedure",
		"net.mcreator.butchery.procedures.HangingPiglincutupProcedure",
		"net.mcreator.butchery.procedures.HangingPillagercorpsecutupProcedure",
		"net.mcreator.butchery.procedures.HangingplayercutupProcedure",
		"net.mcreator.butchery.procedures.HangingPolarbearcutupProcedure",
		"net.mcreator.butchery.procedures.HangingrabbitcutupProcedure",
		"net.mcreator.butchery.procedures.HangingRedmooshroomcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregularblackhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregularbrownhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregularchestnuthorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregularcreamyhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregulardarkbrownhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregulargrayhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingregularwhitehorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingsheepcutupProcedure",
		"net.mcreator.butchery.procedures.HangingskeletoncutupProcedure",
		"net.mcreator.butchery.procedures.HangingskeletonhorsecutupProcedure",
		"net.mcreator.butchery.procedures.HangingskincatcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingsniffercutupProcedure",
		"net.mcreator.butchery.procedures.HangingSnowfoxcutupProcedure",
		"net.mcreator.butchery.procedures.HangingspidercutupProcedure",
		"net.mcreator.butchery.procedures.HangingsquidcutupProcedure",
		"net.mcreator.butchery.procedures.HangingstraycorpsecutupProcedure",
		"net.mcreator.butchery.procedures.HangingstridercutupProcedure",
		"net.mcreator.butchery.procedures.HangingturtlecutupProcedure",
		"net.mcreator.butchery.procedures.HangingvindicatorcorpsecutupProcedure",
		"net.mcreator.butchery.procedures.HangingwitchcutupProcedure",
		"net.mcreator.butchery.procedures.HangingWitherskeletoncutupProcedure",
		"net.mcreator.butchery.procedures.HangingwolfcutupProcedure",
		"net.mcreator.butchery.procedures.HangingzoglincutupProcedure",
		"net.mcreator.butchery.procedures.HangingzombiecutupProcedure",
		"net.mcreator.butchery.procedures.HangingZombiehorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.HangingZombiePiglincutupProcedure",
		"net.mcreator.butchery.procedures.HangingZombievillagercutupProcedure",
		"net.mcreator.butchery.procedures.HoglincutupProcedure",
		"net.mcreator.butchery.procedures.HuskcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.LlamacutupProcedure",
		"net.mcreator.butchery.procedures.MagmacutupProcedure",
		"net.mcreator.butchery.procedures.MediummagmacutupProcedure",
		"net.mcreator.butchery.procedures.MediumslimecutupProcedure",
		"net.mcreator.butchery.procedures.MulecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.OcelotcutupProcedure",
		"net.mcreator.butchery.procedures.PandacarcasscutupProcedure",
		"net.mcreator.butchery.procedures.PhantomcutupProcedure",
		"net.mcreator.butchery.procedures.PigcutupProcedure",
		"net.mcreator.butchery.procedures.PiglinbrutecutupProcedure",
		"net.mcreator.butchery.procedures.PiglincorpsecutupProcedure",
		"net.mcreator.butchery.procedures.PillagercorpsecutupProcedure",
		"net.mcreator.butchery.procedures.PlayercutupProcedure",
		"net.mcreator.butchery.procedures.PolarbearcutupProcedure",
		"net.mcreator.butchery.procedures.PufferfishcutupProcedure",
		"net.mcreator.butchery.procedures.RabbitcutupProcedure",
		"net.mcreator.butchery.procedures.RavagercutupProcedure",
		"net.mcreator.butchery.procedures.RedmooshroomcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegularblackhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegularbrownhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegularchestnuthorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegularcreamyhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegulardarkbrownhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegulargrayhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.RegularwhitehorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.SalmoncutupProcedure",
		"net.mcreator.butchery.procedures.SheepcutupProcedure",
		"net.mcreator.butchery.procedures.ShulkercutupProcedure",
		"net.mcreator.butchery.procedures.SilverfishcutupProcedure",
		"net.mcreator.butchery.procedures.SkeletoncutupProcedure",
		"net.mcreator.butchery.procedures.SkeletonhorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.SkincatcarcasscutupProcedure",
		"net.mcreator.butchery.procedures.SlimecutupProcedure",
		"net.mcreator.butchery.procedures.SniffercutupProcedure",
		"net.mcreator.butchery.procedures.SnowfoxcutupProcedure",
		"net.mcreator.butchery.procedures.SpidercutupProcedure",
		"net.mcreator.butchery.procedures.SquidcutupProcedure",
		"net.mcreator.butchery.procedures.StraycorpsecutupProcedure",
		"net.mcreator.butchery.procedures.StridercutupProcedure",
		"net.mcreator.butchery.procedures.TurtlecutupProcedure",
		"net.mcreator.butchery.procedures.VillagercutupProcedure",
		"net.mcreator.butchery.procedures.VindicatorcorpsecutupProcedure",
		"net.mcreator.butchery.procedures.WardencutupProcedure",
		"net.mcreator.butchery.procedures.WithercutupProcedure",
		"net.mcreator.butchery.procedures.WitherSkeletoncutupProcedure",
		"net.mcreator.butchery.procedures.WolfcutupProcedure",
		"net.mcreator.butchery.procedures.ZoglincutupProcedure",
		"net.mcreator.butchery.procedures.ZombiecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.ZombiehorsecarcasscutupProcedure",
		"net.mcreator.butchery.procedures.ZombiePiglincorpsecutupProcedure",
		"net.mcreator.butchery.procedures.ZombieVillagercutupProcedure"
}, remap = false)
public abstract class ButcheryCutupProcedureMixin {

	@Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$captureCarcass(LevelAccessor world, double x,
			double y, double z, Entity entity, CallbackInfo ci) {
		try {
			ButcheryProcessing.captureCarcass(world, x, y, z);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"butchery cutup capture failed", t);
		}
	}

	/**
	 * The real entry path: {@code onRightClickBlock} invokes the private
	 * {@code execute(Event, ...)} overload DIRECTLY (verified against
	 * butchery 5.2 bytecode — the 5-arg wrapper is only used for
	 * procedure-chaining). Capturing only at the public HEAD left
	 * {@code CUT_CARCASS} empty on every real click, so this overload gets
	 * its own capture — the delegate path re-captures the same (x,y,z)
	 * block, so both hooks can coexist harmlessly.
	 */
	@Inject(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$captureCarcassEvent(@Coerce Object event,
			LevelAccessor world, double x, double y, double z, Entity entity,
			CallbackInfo ci) {
		try {
			ButcheryProcessing.captureCarcass(world, x, y, z);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"butchery cutup event-path capture failed", t);
		}
	}

	@Inject(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"),
			remap = false, require = 0)
	private static void lifepath$onCut(@Coerce Object event, LevelAccessor world,
			double x, double y, double z, Entity entity, CallbackInfo ci) {
		try {
			ButcheryProcessing.onKnifeCommitted(entity);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"butchery cutup seam failed", t);
		}
	}

	@Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"),
			remap = false, require = 0)
	private static void lifepath$onCutInline(LevelAccessor world, double x,
			double y, double z, Entity entity, CallbackInfo ci) {
		try {
			ButcheryProcessing.onKnifeCommitted(entity);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"butchery cutup inline seam failed", t);
		}
	}

	@Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private static void lifepath$clearCut(LevelAccessor world, double x,
			double y, double z, Entity entity, CallbackInfo ci) {
		ButcheryProcessing.clearCut();
	}

	/** Same hygiene on the 6-arg overload — the event path's only frame. */
	@Inject(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private static void lifepath$clearCutEvent(@Coerce Object event,
			LevelAccessor world, double x, double y, double z, Entity entity,
			CallbackInfo ci) {
		ButcheryProcessing.clearCut();
	}
}
