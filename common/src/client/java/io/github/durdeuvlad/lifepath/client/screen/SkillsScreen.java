package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import java.util.List;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
					Component.translatable("screen.lifepath.skills.empty"),
					width / 2, top + panelH / 2, DIM);
			return;
		}

		context.enableScissor(left, top + 18, left + panelW, top + panelH);
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
				context.drawString(font,
						Component.literal(c.display().name()), left + 26, y, TEXT);
				String levelText = Component.translatable(
						"screen.lifepath.skills.level", c.progress().level())
						.getString() + " · " + Component.translatable(
								"lifepath.rank." + c.display().rankKey()).getString();
				context.drawString(font, levelText,
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
					context.renderTooltip(font,
							Component.translatable("screen.lifepath.skills.xp",
									(int) c.progress().xpIn(),
									(int) c.progress().xpNeed()),
							mouseX, mouseY);
				}
			}
			y += ROW_H;
		}
		context.disableScissor();

		// M6-4 discoverability: rows are clickable — say so.
		context.drawCenteredString(font,
				Component.translatable("screen.lifepath.skills.click_hint"),
				width / 2, top + panelH + 8, DIM);
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
