package com.dwurdy.lifepath.client.screen;

import com.dwurdy.lifepath.client.icon.ClientIcons;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import com.dwurdy.lifepath.platform.ClientOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
	private int scroll;

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
		// M26: tooltips are captured while rows render but drawn after the
		// scissor closes — same contract as SkillsScreen.
		List<FormattedCharSequence> tooltip = null;

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
		// The panel caps at height-16 but content doesn't shrink — clip the
		// BODY region only (below the title band, above the back-hint) so
		// scrolled rows can never paint over the chrome.
		context.enableScissor(left - 4, top + 18, left + panelW + 4, top + panelH - 14);
		int y = top + 20 - scroll;

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

		// M26: the numbers behind the current band — yield, quality, botch
		// chance, anvil cost — exactly what testers needed to verify, and
		// what players need to see the grind is worth it.
		SkillCard.BandStat cur = bandAt(card.details().bands(),
				card.details().bandThresholds(), card.progress().level());
		if (cur != null) {
			y = section(context, left, y + 4,
					Component.translatable("screen.lifepath.skill.odds"));
			y = oddsLines(context, left, y, cur);
		}
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

		// M26: the level ladder — every milestone as a hoverable row. Reached
		// rows read plain, the next one glows, future rows dim; hovering any
		// row shows the band odds + unlocks + XP cost at that level.
		if (!card.details().roadmap().isEmpty()) {
			y = section(context, left, y + 4,
					Component.translatable("screen.lifepath.skill.roadmap"));
			for (SkillCard.MilestoneRow row : card.details().roadmap()) {
				boolean reached = row.level() <= card.progress().level();
				boolean isNext = row.level()
						== card.details().nextMilestoneLevel();
				int color = reached ? TEXT : isNext ? ACCENT : DIM;
				Component label = row.descKey().isEmpty()
						? Component.translatable(
								"screen.lifepath.skill.milestone_level",
								row.level())
						: Component.translatable(
								"screen.lifepath.skill.milestone", row.level(),
								Component.translatable(row.descKey()));
				context.drawString(font,
						GuiText.fit(font, label, panelW - 12), left + 6, y,
						color);
				if (mouseX >= left && mouseX <= left + panelW
						&& mouseY >= y && mouseY < y + 10) {
					tooltip = roadmapTooltip(row);
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

		context.disableScissor();
		// Chrome paints outside the clip — scrolled content scrolls under it.
		context.drawCenteredString(font,
				GuiText.fit(font, Component.translatable(
						"screen.lifepath.skill.back_hint"), panelW - 16),
				width / 2, top + panelH - 10, DIM);
		if (tooltip != null) {
			context.renderTooltip(font, tooltip, mouseX, mouseY);
		}
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
		if (bandAt(card.details().bands(), card.details().bandThresholds(),
				card.progress().level()) != null) {
			h += 15 + 10 * oddsLineCount(bandAt(card.details().bands(),
					card.details().bandThresholds(), card.progress().level()));
		}
		if (!card.details().bonuses().isEmpty()) {
			h += 15 + 10 * card.details().bonuses().size();
		}
		if (card.details().nextMilestoneLevel() > 0) {
			h += 15 + 10 + 10 * card.details().nextMilestoneEffects().size();
		}
		if (!card.details().roadmap().isEmpty()) {
			h += 15 + 10 * card.details().roadmap().size();
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

	/**
	 * The band stat row whose {@code [firstLevel, nextFirstLevel)} window
	 * contains {@code level}; null when the skill has no outcome rule.
	 */
	private static SkillCard.BandStat bandAt(List<SkillCard.BandStat> bands,
			List<Integer> thresholds, int level) {
		if (bands.isEmpty()) {
			return null;
		}
		// Band order is the fixed RankBands enum order; bandThresholds[i] is
		// the first level of bands[i].
		int idx = 0;
		for (int i = 0; i < thresholds.size() && i < bands.size(); i++) {
			if (level >= thresholds.get(i)) {
				idx = i;
			}
		}
		return bands.get(idx);
	}

	/** Count of odds lines {@link #oddsLines} will emit for {@code b}. */
	private static int oddsLineCount(SkillCard.BandStat b) {
		int n = 1;                                   // yield
		if (!b.qualityTier().isEmpty()) n++;
		if (b.failChance() > 0) n++;
		if (b.anvilCostMult() != 1.0) n++;
		if (b.signItems()) n++;
		if (b.junkChance() > 0) n++;
		return n;
	}

	/** Renders one band's odds as compact lines; returns the next y. */
	private int oddsLines(GuiGraphics context, int left, int y,
			SkillCard.BandStat b) {
		y = line(context, left, y, Component.translatable(
				"screen.lifepath.skill.odds.yield", fmt(b.outputMult())), DIM);
		if (!b.qualityTier().isEmpty()) {
			y = line(context, left, y, Component.translatable(
					"screen.lifepath.skill.odds.quality",
					Component.translatable("lifepath.quality." + b.qualityTier())),
					DIM);
		}
		if (b.failChance() > 0) {
			y = line(context, left, y, Component.translatable(
					"screen.lifepath.skill.odds.fail",
					(int) Math.round(b.failChance() * 100)), DIM);
		}
		if (b.anvilCostMult() != 1.0) {
			y = line(context, left, y, Component.translatable(
					"screen.lifepath.skill.odds.anvil", fmt(b.anvilCostMult())),
					DIM);
		}
		if (b.signItems()) {
			y = line(context, left, y, Component.translatable(
					"screen.lifepath.skill.odds.sign"), DIM);
		}
		if (b.junkChance() > 0) {
			y = line(context, left, y, Component.translatable(
					"screen.lifepath.skill.odds.junk",
					(int) Math.round(b.junkChance() * 100)), DIM);
		}
		return y;
	}

	/** Full transparency tooltip for a roadmap row (M26). */
	private List<FormattedCharSequence> roadmapTooltip(SkillCard.MilestoneRow row) {
		List<Component> lines = new ArrayList<>();
		boolean reached = row.level() <= card.progress().level();
		// Header: level + band name at that level.
		lines.add(Component.translatable("screen.lifepath.skill.tip.level",
				row.level(),
				Component.translatable("lifepath.rank."
						+ bandKeyAt(row.level()))));
		lines.add(reached
				? Component.translatable("screen.lifepath.skill.tip.reached",
						(int) Math.round(row.xpTotal()))
				: Component.translatable("screen.lifepath.skill.tip.xp",
						(int) Math.round(row.xpTotal()),
						(int) Math.max(0, Math.round(row.xpTotal()
								- card.progress().xpTotal()))));
		SkillCard.BandStat b = bandAt(card.details().bands(),
				card.details().bandThresholds(), row.level());
		if (b != null) {
			lines.add(Component.translatable("screen.lifepath.skill.odds.yield",
					fmt(b.outputMult())));
			if (!b.qualityTier().isEmpty()) {
				lines.add(Component.translatable(
						"screen.lifepath.skill.odds.quality",
						Component.translatable("lifepath.quality."
								+ b.qualityTier())));
			}
			if (b.failChance() > 0) {
				lines.add(Component.translatable(
						"screen.lifepath.skill.odds.fail",
						(int) Math.round(b.failChance() * 100)));
			}
			if (b.anvilCostMult() != 1.0) {
				lines.add(Component.translatable(
						"screen.lifepath.skill.odds.anvil",
						fmt(b.anvilCostMult())));
			}
			if (b.signItems()) {
				lines.add(Component.translatable(
						"screen.lifepath.skill.odds.sign"));
			}
		}
		if (!row.effects().isEmpty()) {
			lines.add(Component.translatable(
					"screen.lifepath.skill.tip.unlocks"));
			for (IdentitySummaryPayload.Entry e : row.effects()) {
				lines.add(Component.literal("· ").append(e.name()));
			}
		}
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) {
			out.add(c.getVisualOrderText());
		}
		return out;
	}

	/** Band translation key at {@code level} using server-sent thresholds. */
	private String bandKeyAt(int level) {
		String[] keys = {"untrained", "novice", "apprentice", "skilled",
				"expert", "master", "legendary"};
		List<Integer> t = card.details().bandThresholds();
		int idx = 0;
		for (int i = 0; i < t.size() && i < keys.length; i++) {
			if (level >= t.get(i)) {
				idx = i;
			}
		}
		return keys[idx];
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? Integer.toString((int) v)
				: String.format(java.util.Locale.ROOT, "%.2f", v)
						.replaceAll("0+$", "").replaceAll("\\.$", "");
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
	public boolean mouseScrolled(double mouseX, double mouseY,
			double horizontalAmount, double verticalAmount) {
		// Content beyond the panel scrolls; the panel itself never moves.
		int panelH = Math.min(height - 16, contentHeight());
		int max = Math.max(0, contentHeight() - panelH);
		scroll = Math.max(0, Math.min(max, scroll - (int) (verticalAmount * 12)));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
