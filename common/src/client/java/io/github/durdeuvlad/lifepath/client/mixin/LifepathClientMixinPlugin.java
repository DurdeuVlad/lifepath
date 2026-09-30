package io.github.durdeuvlad.lifepath.client.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Side gate for the client mixin config. Fabric skips the whole config on
 * a dedicated server via {@code "environment": "client"} in
 * {@code fabric.mod.json}; NeoForge's {@code [[mixins]]} TOML block has no
 * such field, so without this plugin a dedicated server would try to
 * transform {@code net.minecraft.client.*} targets and fail hard. The
 * plugin checks target-class <b>bytecode presence</b> — never
 * {@code Class.forName} (loading a mixin target early can break the
 * transform) — and skips any mixin whose target doesn't exist.
 *
 * <p>Must reference no Minecraft classes: mixin plugins load before the
 * game's classloader is ready.
 */
public final class LifepathClientMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String resource = targetClassName.replace('.', '/') + ".class";
		try {
			return MixinService.getService().getResourceAsStream(resource) != null;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {
	}
}
