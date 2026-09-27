package io.github.durdeuvlad.lifepath.client.hud;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.hud.HudModel;
import io.github.durdeuvlad.lifepath.hud.HudModel.CooldownRow;
import io.github.durdeuvlad.lifepath.hud.HudModel.ResourceRow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * M6-3 HUD overlay — draws the {@link HudModel} view: relevant resources,
 * active cooldowns, and temporary states. Pure read side: every value comes
 * from the synced snapshot / identity payload; nothing here mutates
 * authoritative state. An empty view draws nothing — the HUD is absent when
 * idle by design.
 *
 * <p>Accessibility: every row is labeled text (never color-only). Resource
 * rows show label + band name + numeric value beside the bar; cooldown rows
 * show ability name + seconds remaining; state rows are plain text.
 */
@Environment(EnvType.CLIENT)
public final class LifepathHud {
	private static final int MARGIN = 8;
	private static final int BAR_W = 96;
	private static final int BAR_H = 7;
	private static final int ROW_STEP = 10;
	private static final int COL_TEXT = 0xFFE0E0E0;
	private static final int COL_BAR_BG = 0x80101010;
	private static final int COL_BAR_FILL = 0xFF58B0D8;
	private static final int COL_BAND = 0xFFFFC857;

	private LifepathHud() {
	}

	/** Entry point wired into {@code HudRenderCallback}. */
	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!LifepathConfig.getBoolean(LifepathConfig.CLIENT, "hud_enabled")) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		HudModel.View view = HudModel.compute(ClientCharacterState.identity(),
				ClientCharacterState.snapshot(),
				ClientCharacterState.resourceBands(),
				System.currentTimeMillis());
		if (view.isEmpty()) {
			return;
		}

		float scale = (float) LifepathConfig.getDouble(LifepathConfig.CLIENT,
				"hud_scale");
		String position = LifepathConfig.getString(LifepathConfig.CLIENT,
				"hud_position");

		int width = contentWidth(view, client);
		int height = contentHeight(view);
		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();
		int ax = switch (position) {
			case "top_right", "bottom_right" -> sw - MARGIN - width;
			default -> MARGIN;
		};
		int ay = switch (position) {
			case "bottom_left", "bottom_right" -> sh - MARGIN - height;
			default -> MARGIN;
		};

		context.getMatrices().push();
		context.getMatrices().translate(ax, ay, 0);
		context.getMatrices().scale(scale, scale, 1.0f);

		int y = 0;
		for (ResourceRow row : view.resources()) {
			drawResourceRow(context, client, row, y);
			y += ROW_STEP;
		}
		for (CooldownRow row : view.cooldowns()) {
			context.drawTextWithShadow(client.textRenderer,
					Text.translatable("hud.lifepath.cooldown", row.label(),
							String.format("%.0f", row.secondsLeft())),
					0, y, COL_TEXT);
			y += ROW_STEP;
		}
		for (String state : view.states()) {
			context.drawTextWithShadow(client.textRenderer, state, 0, y,
					COL_BAND);
			y += ROW_STEP;
		}
		context.getMatrices().pop();
	}

	private static void drawResourceRow(DrawContext context,
			MinecraftClient client, ResourceRow row, int y) {
		// Layout: [bar][text "Label BandName value"] — text carries the
		// information so color is never the only signal.
		context.fill(0, y, BAR_W, y + BAR_H, COL_BAR_BG);
		context.fill(0, y, Math.round(BAR_W * (float) row.fraction()), y + BAR_H,
				COL_BAR_FILL);
		String band = row.bandName().isEmpty() ? ""
				: " " + row.bandName();
		String text = row.label() + band + " "
				+ String.format("%.0f", row.value());
		context.drawTextWithShadow(client.textRenderer, text, BAR_W + 4, y,
				COL_TEXT);
	}

	private static int contentWidth(HudModel.View view, MinecraftClient client) {
		int w = BAR_W + 4;
		for (ResourceRow row : view.resources()) {
			w = Math.max(w, BAR_W + 4 + client.textRenderer.getWidth(row.label()
					+ " " + row.bandName() + " 000"));
		}
		for (CooldownRow row : view.cooldowns()) {
			w = Math.max(w, client.textRenderer.getWidth(row.label() + " 000"));
		}
		for (String state : view.states()) {
			w = Math.max(w, client.textRenderer.getWidth(state));
		}
		return w;
	}

	private static int contentHeight(HudModel.View view) {
		return (view.resources().size() + view.cooldowns().size()
				+ view.states().size()) * ROW_STEP;
	}
}
