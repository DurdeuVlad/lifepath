package com.dwurdy.lifepath.client.screen;

import com.dwurdy.lifepath.client.icon.ClientIcons;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import com.dwurdy.lifepath.platform.ClientOnly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * M6-2 skill detail — level, rank, aptitude, bonuses, next milestone, decay
 * state in plain language, and the HOW TO IMPROVE statement. Read-only.
 */
@ClientOnly
public class SkillDetailScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int BAR_BG = 0xFF2A2A33;
	private static final int BAR_FG = 0xFF55AA55;
	private static final int PANEL_W = 240;

	private final SkillCard card;
	private final Screen parent;

	public SkillDetailScreen(SkillCard card, Screen parent) {
		super(card.display().name());
		this.card = card;
		this.parent = parent;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 120;
		int panelW = PANEL_W;
		// Content-driven height: bonus rows, milestone-effect rows, and wrapped
		// hint lines all grow the layout — a fixed panel overflows.
		int panelH = Math.min(height - 16, contentHeight());
		int top = Math.max(8, height / 2 - panelH / 2);
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);
		// The panel caps at height-16 but content doesn't shrink — clip so
		// rows never paint below the panel on short viewports.
		context.enableScissor(left - 4, top - 4, left + panelW + 4, top + panelH + 4);

		// M12-2: skill icon sits beside the centered title — zero vertical
		// cost, and an absent icon leaves just the title.
		int titleW = font.width(card.display().name());
		ClientIcons.resolve("skill", card.display().icon())
				.ifPresent(tex -> context.blit(tex,
						width / 2 - titleW / 2 - 20, top + 1,
						0, 0, 16, 16, 16, 16));
		context.drawCenteredString(font,
				GuiText.fit(font, card.display().name(), panelW - 16),
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
							GuiText.fit(font, b.name(), panelW - 23),
							left + 17, y + 1, TEXT);
				} else {
					context.drawString(font,
							GuiText.fit(font,
									Component.literal("· ").append(b.name()),
									panelW - 12),
							left + 6, y + 1, TEXT);
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
			// M16: show WHAT the milestone unlocks — resolved names + icons,
			// same rendering contract as the bonuses block above.
			for (IdentitySummaryPayload.Entry effect : card.details()
					.nextMilestoneEffects()) {
				var icon = ClientIcons.resolve("ability", effect.icon());
				if (icon.isPresent()) {
					context.blit(icon.get(), left + 10, y + 1,
							8, 8, 0, 0, 16, 16, 16, 16);
					context.drawString(font,
							GuiText.fit(font, effect.name(), panelW - 27),
							left + 21, y + 1, DIM);
				} else {
					context.drawString(font,
							GuiText.fit(font,
									Component.literal("· ").append(effect.name()),
									panelW - 16),
							left + 10, y + 1, DIM);
				}
				y += 10;
			}
		}

		// Decay in friendly terms.
		y = section(context, left, y + 4,
				Component.translatable("screen.lifepath.skill.decay"));
		y = line(context, left, y, decayText(card.progress()), DIM);

		// HOW TO IMPROVE — the load-bearing plain-language statement.
		y = section(context, left, y + 4,
				Component.translatable("screen.lifepath.skill.how_to_improve"));
		Component hint = card.display().improveHint();
		for (var wrapped : font.split(
				hint.getString().isEmpty()
						? Component.translatable("screen.lifepath.skill.no_hint")
						: hint,
				panelW - 12)) {
			context.drawString(font, wrapped, left + 6, y, TEXT);
			y += 10;
		}

		context.drawCenteredString(font,
				GuiText.fit(font, Component.translatable(
						"screen.lifepath.skill.back_hint"), panelW - 16),
				width / 2, top + panelH - 10, DIM);
		context.disableScissor();
	}

	/**
	 * Mirrors {@link #render}'s row math so the panel wraps the actual
	 * content — a fixed 190px overflows once bonuses and milestone-effect
	 * rows stack up.
	 */
	private int contentHeight() {
		int h = 20;                        // title band (rows start at top+20)
		h += 20;                           // level/rank + aptitude
		h += 9 + 10;                       // xp bar + caption
		if (!card.details().bonuses().isEmpty()) {
			h += 15 + 10 * card.details().bonuses().size();
		}
		if (card.details().nextMilestoneLevel() > 0) {
			h += 15 + 10 + 10 * card.details().nextMilestoneEffects().size();
		}
		h += 15 + 10;                      // decay section + line
		h += 15;                           // how-to-improve header
		Component hint = card.display().improveHint();
		h += 10 * font.split(hint.getString().isEmpty()
				? Component.translatable("screen.lifepath.skill.no_hint")
				: hint, PANEL_W - 12).size();
		return h + 12;                     // back-hint row + bottom padding
	}

	private Component decayText(SkillCard.Progress p) {
		long graceEnd = p.graceEndsEpochMs();
		if (graceEnd == com.dwurdy.lifepath.skill.SkillSummary.DECAY_DISABLED) {
			return Component.translatable("screen.lifepath.skill.decay_off");
		}
		if (p.level() == 0 || graceEnd == com.dwurdy.lifepath.skill
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
		context.drawString(font, GuiText.fit(font, text, PANEL_W - 8),
				x + 6, y, color);
		return y + 10;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
