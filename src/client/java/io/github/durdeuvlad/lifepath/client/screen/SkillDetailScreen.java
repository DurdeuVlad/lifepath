package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * M6-2 skill detail — level, rank, aptitude, bonuses, next milestone, decay
 * state in plain language, and the HOW TO IMPROVE statement. Read-only.
 */
@Environment(EnvType.CLIENT)
public class SkillDetailScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int BAR_BG = 0xFF2A2A33;
	private static final int BAR_FG = 0xFF55AA55;

	private final SkillCard card;
	private final Screen parent;

	public SkillDetailScreen(SkillCard card, Screen parent) {
		super(Component.literal(card.display().name()));
		this.card = card;
		this.parent = parent;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 120;
		int top = height / 2 - 95;
		int panelW = 240;
		int panelH = 190;
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);

		// M12-2: skill icon sits beside the centered title — zero vertical
		// cost, and an absent icon leaves just the title.
		int titleW = font.width(card.display().name());
		ClientIcons.resolve("skill", card.display().icon())
				.ifPresent(tex -> context.blit(tex,
						width / 2 - titleW / 2 - 20, top + 1,
						0, 0, 16, 16, 16, 16));
		context.drawCenteredString(font, card.display().name(),
				width / 2, top + 4, ACCENT);
		int y = top + 20;

		// Level + rank + aptitude.
		y = line(context, left, y, Component.translatable(
				"screen.lifepath.skill.level_rank", card.progress().level(),
				Component.translatable("lifepath.rank." + card.display().rankKey())),
				TEXT);
		y = line(context, left, y, Component.translatable(
				"screen.lifepath.skill.aptitude", card.display().aptitude()), DIM);

		// XP progress + hover-precision numbers.
		int barY = y;
		double frac = card.progress().xpNeed() <= 0 ? 1.0
				: Math.min(1.0, card.progress().xpIn() / card.progress().xpNeed());
		context.fill(left + 6, barY, left + panelW - 6, barY + 6, BAR_BG);
		context.fill(left + 6, barY, left + 6 + (int) ((panelW - 12) * frac),
				barY + 6, BAR_FG);
		y += 9;
		y = line(context, left, y, card.progress().xpNeed() <= 0
				? Component.translatable("screen.lifepath.skill.maxed")
				: Component.translatable("screen.lifepath.skill.xp",
						(int) card.progress().xpIn(), (int) card.progress().xpNeed()),
				DIM);

		// Current bonuses (milestone effects already unlocked).
		if (!card.details().bonuses().isEmpty()) {
			y = section(context, left, y + 4,
					Component.translatable("screen.lifepath.skill.bonuses"));
			for (IdentitySummaryPayload.Entry b : card.details().bonuses()) {
				// Badge icon when the referenced def declares one (abilities
				// are the common milestone effect); bullet when not — rows
				// without art must not look broken.
				var icon = ClientIcons.resolve("ability", b.icon());
				if (icon.isPresent()) {
					// 9-arg form: 8x8 box sampling the full 16x16 sprite.
					context.blit(icon.get(), left + 6, y + 1,
							8, 8, 0, 0, 16, 16, 16, 16);
					context.drawString(font,
							Component.literal(b.name()), left + 17, y + 1, TEXT);
				} else {
					context.drawString(font,
							Component.literal("· " + b.name()), left + 6, y + 1, TEXT);
				}
				y += 10;
			}
		}

		// Next milestone.
		if (card.details().nextMilestoneLevel() > 0) {
			y = section(context, left, y + 4,
					Component.translatable("screen.lifepath.skill.next_milestone"));
			String descKey = card.details().nextMilestoneText();
			Component milestone = descKey.isEmpty()
					? Component.translatable("screen.lifepath.skill.milestone_level",
							card.details().nextMilestoneLevel())
					: Component.translatable("screen.lifepath.skill.milestone",
							card.details().nextMilestoneLevel(),
							Component.translatable(descKey));
			y = line(context, left, y, milestone, TEXT);
		}

		// Decay in friendly terms.
		y = section(context, left, y + 4,
				Component.translatable("screen.lifepath.skill.decay"));
		y = line(context, left, y, decayText(card.progress()), DIM);

		// HOW TO IMPROVE — the load-bearing plain-language statement.
		y = section(context, left, y + 4,
				Component.translatable("screen.lifepath.skill.how_to_improve"));
		String hint = card.display().improveHint();
		for (var wrapped : font.split(
				hint.isEmpty()
						? Component.translatable("screen.lifepath.skill.no_hint")
						: Component.literal(hint),
				panelW - 12)) {
			context.drawString(font, wrapped, left + 6, y, TEXT);
			y += 10;
		}

		context.drawCenteredString(font,
				Component.translatable("screen.lifepath.skill.back_hint"),
				width / 2, top + panelH - 8, DIM);
	}

	private Component decayText(SkillCard.Progress p) {
		long graceEnd = p.graceEndsEpochMs();
		if (graceEnd == io.github.durdeuvlad.lifepath.skill.SkillSummary.DECAY_DISABLED) {
			return Component.translatable("screen.lifepath.skill.decay_off");
		}
		if (p.level() == 0 || graceEnd == io.github.durdeuvlad.lifepath.skill
				.SkillSummary.NEVER_PRACTICED) {
			return Component.translatable("screen.lifepath.skill.decay_none");
		}
		if (p.protectedFloor() >= p.level()) {
			return Component.translatable("screen.lifepath.skill.decay_floored",
					p.protectedFloor());
		}
		long now = System.currentTimeMillis();
		if (now < graceEnd) {
			long hours = (graceEnd - now) / 3_600_000L;
			if (hours >= 48) {
				return Component.translatable("screen.lifepath.skill.decay_protected_days",
						hours / 24);
			}
			return Component.translatable("screen.lifepath.skill.decay_protected_hours",
					Math.max(1, hours));
		}
		return Component.translatable("screen.lifepath.skill.decay_active",
				p.protectedFloor());
	}

	private int section(GuiGraphics context, int x, int y, Component label) {
		context.drawString(font, label, x, y, ACCENT);
		return y + 11;
	}

	private int line(GuiGraphics context, int x, int y, Component text, int color) {
		context.drawString(font, text, x + 6, y, color);
		return y + 10;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
