package com.dwurdy.lifepath.client.screen;

import com.dwurdy.lifepath.client.character.ClientCharacterState;
import com.dwurdy.lifepath.client.icon.ClientIcons;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import java.util.ArrayList;
import java.util.List;
import com.dwurdy.lifepath.platform.ClientOnly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * M6-2 skills list — every skill with level, rank band, and XP progress.
 * Rows are clickable → {@link SkillDetailScreen}. Read-only; renders
 * {@link ClientCharacterState#skills()} exactly as the server resolved them.
 */
@ClientOnly
public class SkillsScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int BAR_BG = 0xFF2A2A33;
	private static final int BAR_FG = 0xFF55AA55;
	private static final int ROW_H = 18;
	private static final int ROWS_VISIBLE = 8;

	private int scroll;

	public SkillsScreen() {
		super(Component.translatable("screen.lifepath.skills.title"));
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 120;
		int top = height / 2 - 90;
		int panelW = 240;
		int panelH = ROWS_VISIBLE * ROW_H + 30;
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);

		context.drawCenteredString(font,
				Component.translatable("screen.lifepath.skills.title"),
				width / 2, top + 4, ACCENT);

		List<SkillCard> skills = ClientCharacterState.skills();
		if (skills.isEmpty()) {
			context.drawCenteredString(font,
					GuiText.fit(font, Component.translatable(
							"screen.lifepath.skills.empty"), panelW - 16),
					width / 2, top + panelH / 2, DIM);
			return;
		}

		context.enableScissor(left, top + 18, left + panelW, top + panelH);
		// Tooltips are captured while rows render but drawn after the scissor
		// closes — renderTooltip draws immediately, so anything painted here
		// would be clipped to the panel edge.
		List<FormattedCharSequence> tooltip = null;
		int y = top + 20 - scroll;
		for (int i = 0; i < skills.size(); i++) {
			SkillCard c = skills.get(i);
			if (y + ROW_H > top + 18 && y < top + panelH) {
				boolean hovered = mouseX >= left && mouseX <= left + panelW
						&& mouseY >= y && mouseY < y + ROW_H;
				if (hovered) {
					context.fill(left, y - 1, left + panelW, y + ROW_H - 1,
							0x22FFFFFF);
				}
				// M12-2: skill icon leads the row (declared → placeholder →
				// none); the name + level text stay — icons augment, never
				// replace. The gutter is reserved even when the icon is
				// absent so rows keep one aligned column.
				final int iconRowY = y;
				ClientIcons.resolve("skill", c.display().icon())
						.ifPresent(tex -> context.blit(tex,
								left + 6, iconRowY + 1, 0, 0, 16, 16, 16, 16));
				// Name/rank clip at the XP bar's left edge, not the panel's —
				// long translations must never draw under the bar.
				int textW = panelW - 86 - 32;
				context.drawString(font,
						GuiText.fit(font, c.display().name(), textW),
						left + 26, y, TEXT);
				String levelText = Component.translatable(
						"screen.lifepath.skills.level", c.progress().level())
						.getString() + " · " + Component.translatable(
								"lifepath.rank." + c.display().rankKey()).getString();
				context.drawString(font, GuiText.fit(font, levelText, textW),
						left + 26, y + 9, DIM);

				// Progress bar: xpIn/xpNeed (0-need = max level → full bar).
				int barX = left + panelW - 86;
				int barY = y + 4;
				double frac = c.progress().xpNeed() <= 0 ? 1.0
						: Math.min(1.0, c.progress().xpIn() / c.progress().xpNeed());
				context.fill(barX, barY, barX + 80, barY + 5, BAR_BG);
				context.fill(barX, barY, barX + (int) (80 * frac), barY + 5, BAR_FG);
				if (mouseX >= barX && mouseX <= barX + 80
						&& mouseY >= barY && mouseY < barY + 6) {
					tooltip = List.of(Component.translatable(
							"screen.lifepath.skills.xp",
							(int) c.progress().xpIn(),
							(int) c.progress().xpNeed()).getVisualOrderText());
				} else if (hovered && mouseX < barX) {
					// M16: hovering the name/level zone explains the skill —
					// the description was already on the wire, never rendered.
					tooltip = new ArrayList<>();
					tooltip.add(c.display().name().getVisualOrderText());
					if (!c.display().description().getString().isEmpty()) {
						tooltip.addAll(font.split(c.display().description(), 200));
					}
					// "How to train it" — the flavor line alone left testers
					// asking what a skill actually *does* (Beta 8 bug set).
					if (!c.display().improveHint().getString().isEmpty()) {
						tooltip.addAll(font.split(c.display().improveHint()
								.copy().withStyle(ChatFormatting.GRAY), 200));
					}
					// M26: the odds at the current level — yield, quality,
					// botch — the numbers players used to have to guess.
					SkillCard.BandStat b = bandAt(c.details().bands(),
							c.details().bandThresholds(),
							c.progress().level());
					if (b != null) {
						tooltip.add(Component.translatable(
								"screen.lifepath.skills.odds",
								fmt(b.outputMult()),
								b.qualityTier().isEmpty()
										? Component.translatable(
												"lifepath.quality.standard")
										: Component.translatable(
												"lifepath.quality."
														+ b.qualityTier()),
								(int) Math.round(b.failChance() * 100))
								.getVisualOrderText());
					}
				}
			}
			y += ROW_H;
		}
		context.disableScissor();
		if (tooltip != null) {
			context.renderTooltip(font, tooltip, mouseX, mouseY);
		}

		// M6-4 discoverability: rows are clickable — say so.
		context.drawCenteredString(font,
				Component.translatable("screen.lifepath.skills.click_hint"),
				width / 2, top + panelH + 8, DIM);
	}

	/** Band row at {@code level} (server thresholds); null = no rule. */
	private static SkillCard.BandStat bandAt(List<SkillCard.BandStat> bands,
			List<Integer> thresholds, int level) {
		if (bands.isEmpty()) {
			return null;
		}
		int idx = 0;
		for (int i = 0; i < thresholds.size() && i < bands.size(); i++) {
			if (level >= thresholds.get(i)) {
				idx = i;
			}
		}
		return bands.get(idx);
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? Integer.toString((int) v)
				: String.format(java.util.Locale.ROOT, "%.2f", v)
						.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		List<SkillCard> skills = ClientCharacterState.skills();
		int left = width / 2 - 120;
		int top = height / 2 - 90;
		int y = top + 20 - scroll;
		int idx = (int) ((mouseY - y) / ROW_H);
		if (button == 0 && mouseX >= left && mouseX <= left + 240
				&& idx >= 0 && idx < skills.size()) {
			double rowTop = y + idx * ROW_H;
			if (mouseY >= rowTop && mouseY < rowTop + ROW_H
					&& mouseY >= top + 18) {
				minecraft.setScreen(new SkillDetailScreen(skills.get(idx), this));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY,
			double horizontalAmount, double verticalAmount) {
		List<SkillCard> skills = ClientCharacterState.skills();
		int max = Math.max(0, skills.size() * ROW_H - ROWS_VISIBLE * ROW_H);
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * ROW_H));
		return true;
	}
}
