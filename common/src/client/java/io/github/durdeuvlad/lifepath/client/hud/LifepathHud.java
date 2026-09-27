package io.github.durdeuvlad.lifepath.client.hud;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.hud.HudModel;
import io.github.durdeuvlad.lifepath.hud.HudModel.CooldownRow;
import io.github.durdeuvlad.lifepath.hud.HudModel.ResourceRow;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
 *
 * <p>M12-3: every row leads with a 10px badge icon resolved through
 * {@link ClientIcons} (declared → placeholder → none). The gutter is
 * reserved regardless so rows stay aligned when an icon is absent; all
 * labels remain — icons augment, never replace.
 */
@ClientOnly
public final class LifepathHud {
	private static final int MARGIN = 8;
	private static final int BAR_W = 96;
	private static final int BAR_H = 7;
	private static final int ROW_STEP = 10;
	private static final int ICON_W = 12;
	private static final int COL_TEXT = 0xFFE0E0E0;
	private static final int COL_BAR_BG = 0x80101010;
	private static final int COL_BAR_FILL = 0xFF58B0D8;
	private static final int COL_BAND = 0xFFFFC857;

	private LifepathHud() {
	}

	/** Entry point wired into {@code HudRenderCallback}. */
	public static void render(GuiGraphics context, DeltaTracker tickCounter) {
		if (!LifepathConfig.getBoolean(LifepathConfig.CLIENT, "hud_enabled")) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
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
		int sw = context.guiWidth();
		int sh = context.guiHeight();
		int ax = switch (position) {
			case "top_right", "bottom_right" -> sw - MARGIN - width;
			default -> MARGIN;
		};
		int ay = switch (position) {
			case "bottom_left", "bottom_right" -> sh - MARGIN - height;
			default -> MARGIN;
		};

		context.pose().pushPose();
		context.pose().translate(ax, ay, 0);
		context.pose().scale(scale, scale, 1.0f);

		int y = 0;
		for (ResourceRow row : view.resources()) {
			drawResourceRow(context, client, row, y);
			y += ROW_STEP;
		}
		for (CooldownRow row : view.cooldowns()) {
			drawIcon(context, "ability", row.icon(), y);
			context.drawString(client.font,
					Component.translatable("hud.lifepath.cooldown", row.label(),
							String.format("%.0f", row.secondsLeft())),
					ICON_W, y, COL_TEXT);
			y += ROW_STEP;
		}
		for (IdentitySummaryPayload.Entry state : view.states()) {
			drawIcon(context, "condition", state.icon(), y);
			context.drawString(client.font, state.name(),
					ICON_W, y, COL_BAND);
			y += ROW_STEP;
		}
		context.pose().popPose();
	}

	private static void drawIcon(GuiGraphics context, String domain,
			String iconRef, int y) {
		// 9-arg form: 10x10 box sampling the full 16x16 sprite.
		ClientIcons.resolve(domain, iconRef)
				.ifPresent(tex -> context.blit(tex, 0, y,
						10, 10, 0, 0, 16, 16, 16, 16));
	}

	private static void drawResourceRow(GuiGraphics context,
			Minecraft client, ResourceRow row, int y) {
		// Layout: [icon][bar][text "Label BandName value"] — text carries the
		// information so color is never the only signal.
		drawIcon(context, "resource", row.icon(), y);
		context.fill(ICON_W, y, ICON_W + BAR_W, y + BAR_H, COL_BAR_BG);
		context.fill(ICON_W, y,
				ICON_W + Math.round(BAR_W * (float) row.fraction()), y + BAR_H,
				COL_BAR_FILL);
		String band = row.bandName().isEmpty() ? ""
				: " " + row.bandName();
		String text = row.label() + band + " "
				+ String.format("%.0f", row.value());
		context.drawString(client.font, text,
				ICON_W + BAR_W + 4, y, COL_TEXT);
	}

	private static int contentWidth(HudModel.View view, Minecraft client) {
		int w = ICON_W + BAR_W + 4;
		for (ResourceRow row : view.resources()) {
			w = Math.max(w, ICON_W + BAR_W + 4 + client.font
					.width(row.label() + " " + row.bandName() + " 000"));
		}
		for (CooldownRow row : view.cooldowns()) {
			w = Math.max(w, ICON_W + client.font
					.width(row.label() + " 000"));
		}
		for (IdentitySummaryPayload.Entry state : view.states()) {
			w = Math.max(w, ICON_W + client.font
					.width(state.name()));
		}
		return w;
	}

	private static int contentHeight(HudModel.View view) {
		return (view.resources().size() + view.cooldowns().size()
				+ view.states().size()) * ROW_STEP;
	}
}
