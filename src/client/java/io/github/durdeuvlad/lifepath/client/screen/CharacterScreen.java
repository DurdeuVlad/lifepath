package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.ability.ClientAbilityState;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * M6-1 character screen — the identity hub ("what am I?"). Pure read model:
 * renders {@link ClientCharacterState}'s synced snapshot + resolved identity
 * summary; it sends nothing and mutates nothing (there is no C2S channel for
 * character state).
 *
 * <p>Hierarchy (TIMELINE §1.3): identity first, then the actionable cards
 * (conditions/attunements/traits). Empty sections render explicit friendly
 * states rather than blank space.
 *
 * <p>M12-2: species/specialization render as icon + name hero rows and every
 * significant id (conditions/attunements/traits) carries its badge icon —
 * {@link ClientIcons} resolves declared texture → domain placeholder →
 * none, so rows keep working while final art is pending. Names stay visible
 * (icons augment, never replace); the species description moved to a hover
 * tooltip over its hero row to keep the default view identity-at-a-glance.
 *
 * <p>Abilities section: every owned ability renders with its icon; rows
 * whose trigger kind is ACTIVE are clickable and bind the ability key —
 * pressing it with nothing picked sends the server-side AUTO pick instead.
 * Passives render dimmed and are never offered as targets.
 */
@Environment(EnvType.CLIENT)
public class CharacterScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int PANEL_W = 220;

	/** Clickable active-ability rows, rebuilt every render pass. */
	private final List<AbilityRow> abilityRows = new ArrayList<>();

	/** Hit rect for one clickable ability row (screen coordinates). */
	private record AbilityRow(String id, int x, int y, int w, int h) {}

	public CharacterScreen() {
		super(Text.translatable("screen.lifepath.character.title"));
	}

	@Override
	protected void init() {
		// M6-2: Skills entry point — the identity hub links to the skills list.
		addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
				Text.translatable("screen.lifepath.character.skills_button"),
				b -> client.setScreen(new SkillsScreen()))
				.dimensions(width / 2 - 60,
						panelTop() + panelHeight(
								ClientCharacterState.identity()) + 6,
						120, 18)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 110;
		int panelW = PANEL_W;
		IdentitySummaryPayload id = ClientCharacterState.identity();
		IdentitySummaryPayload.IdentityCore core = id.identity();
		// The panel wraps measured content — sections flow past a fixed
		// height, so it must grow or the key hint lands on the last rows.
		int panelH = panelHeight(id);
		int top = panelTop(panelH);
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);

		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.lifepath.character.title"),
				width / 2, top + 4, ACCENT);

		int y = top + 22;

		// --- Species (the identity hero line) ---
		y = section(context, left, y,
				Text.translatable("screen.lifepath.character.species"));
		if (core.speciesName().isEmpty()) {
			y = line(context, left, y,
					Text.translatable("screen.lifepath.character.no_species"), DIM);
		} else {
			int rowTop = y;
			ClientIcons.resolve("species", core.speciesIcon())
					.ifPresent(tex -> context.drawTexture(tex, left + 6, rowTop - 1,
							0, 0, 16, 16, 16, 16));
			context.drawTextWithShadow(textRenderer, Text.literal(core.speciesName()),
					left + 26, rowTop + 4, TEXT);
			y = rowTop + 18;
			// M12-2: the description is displaced from the default view —
			// hover the hero row to read it (name stays always-on).
			if (!core.speciesDescription().isEmpty() && mouseX >= left
					&& mouseX <= left + panelW && mouseY >= rowTop - 2
					&& mouseY <= rowTop + 16) {
				context.drawOrderedTooltip(textRenderer,
						textRenderer.wrapLines(
								Text.literal(core.speciesDescription()), panelW - 8),
						mouseX, mouseY);
			}
		}

		// --- Specialization + starting focus ---
		y = section(context, left, y + 4,
				Text.translatable("screen.lifepath.character.specialization"));
		if (core.specName().isEmpty()) {
			y = line(context, left, y,
					Text.translatable("screen.lifepath.character.no_specialization"), DIM);
		} else {
			final int specRowY = y;
			ClientIcons.resolve("specialization", core.specIcon())
					.ifPresent(tex -> context.drawTexture(tex, left + 6,
							specRowY - 1, 0, 0, 16, 16, 16, 16));
			context.drawTextWithShadow(textRenderer, Text.literal(core.specName()),
					left + 26, y + 4, TEXT);
			y += 18;
			if (!id.specFocus().isEmpty()) {
				String focusNames = String.join(", ", id.specFocus().stream()
						.map(IdentitySummaryPayload.Entry::name).toList());
				y = line(context, left, y, Text.translatable(
						"screen.lifepath.character.focus", focusNames), DIM);
			}
		}
		// M6-4 mandated line — a spec choice must never read as a lockout.
		for (var wrapped : textRenderer.wrapLines(Text.translatable(
				"screen.lifepath.character.spec_note"), panelW - 8)) {
			context.drawTextWithShadow(textRenderer, wrapped, left + 6, y, DIM);
			y += 10;
		}

		// --- Significant ids: conditions / attunements / traits ---
		y = listSection(context, left, y + 4,
				"screen.lifepath.character.conditions", "condition",
				id.sections().getOrDefault(IdentitySummary.SECTION_CONDITIONS, List.of()));
		y = listSection(context, left, y + 4,
				"screen.lifepath.character.attunements", "attunement",
				id.sections().getOrDefault(IdentitySummary.SECTION_ATTUNEMENTS, List.of()));
		y = listSection(context, left, y + 4,
				"screen.lifepath.character.traits", "trait",
				id.sections().getOrDefault(IdentitySummary.SECTION_TRAITS, List.of()));

		// --- Abilities: click an ACTIVE row to bind the ability key ---
		abilityRows.clear();
		if (!id.abilities().isEmpty()) {
			// A respec/species change can drop the bound ability — clear a
			// stale pick so the key falls back to AUTO rather than failing
			// silently. Guarded on non-empty: the pre-sync empty payload must
			// never wipe a live selection.
			Identifier selected = ClientAbilityState.selected();
			if (selected != null
					&& !id.abilities().containsKey(selected.toString())) {
				ClientAbilityState.clear();
				selected = null;
			}
			y = section(context, left, y + 4,
					Text.translatable("screen.lifepath.character.abilities"));
			for (IdentitySummaryPayload.AbilityEntry e
					: id.abilities().values()) {
				boolean sel = selected != null
						&& e.id().equals(selected.toString());
				final int rowY = y;
				ClientIcons.resolve("ability", e.icon())
						.ifPresent(tex -> context.drawTexture(tex, left + 6,
								rowY, 10, 10, 0, 0, 16, 16, 16, 16));
				Text label = e.active()
						? Text.literal((sel ? "> " : "") + e.name())
						: Text.translatable(
								"screen.lifepath.character.ability_passive",
								e.name());
				context.drawTextWithShadow(textRenderer, label, left + 19,
						y + 1, sel ? ACCENT : (e.active() ? TEXT : DIM));
				if (e.active()) {
					abilityRows.add(new AbilityRow(e.id(), left, y, panelW, 11));
				}
				y += 11;
			}
			if (selected == null
					&& io.github.durdeuvlad.lifepath.client.LifepathClient
							.abilityKey != null) {
				y = line(context, left, y + 2, Text.translatable(
						"screen.lifepath.character.abilities_hint",
						io.github.durdeuvlad.lifepath.client.LifepathClient
								.abilityKey.getBoundKeyLocalizedText()), DIM);
			}
		}

		// M6-4: visible keybind hints — the ability key is never discoverable
		// otherwise. Shows the ACTUAL bound key, not a hardcoded letter.
		if (io.github.durdeuvlad.lifepath.client.LifepathClient.abilityKey != null) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("screen.lifepath.character.key_hint",
							io.github.durdeuvlad.lifepath.client.LifepathClient
									.abilityKey.getBoundKeyLocalizedText()),
					width / 2, top + panelH - 14, DIM);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (AbilityRow row : abilityRows) {
			if (mouseX >= row.x() && mouseX < row.x() + row.w()
					&& mouseY >= row.y() && mouseY < row.y() + row.h()) {
				Identifier picked = Identifier.tryParse(row.id());
				if (picked != null) {
					ClientAbilityState.select(picked);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private int panelTop() {
		return panelTop(panelHeight(ClientCharacterState.identity()));
	}

	/** Center the grown panel vertically; clamp so it never starts off-screen. */
	private int panelTop(int panelH) {
		return Math.max(8, (height - panelH) / 2);
	}

	/**
	 * Measured content height so the panel wraps whatever sections exist —
	 * a fixed height either clipped rows or left the key hint floating over
	 * text. Mirrors the render flow line-for-line.
	 */
	private int panelHeight(IdentitySummaryPayload id) {
		IdentitySummaryPayload.IdentityCore core = id.identity();
		int h = 22;                              // title gap
		h += 11 + (core.speciesName().isEmpty() ? 11 : 18);
		h += 4 + 11 + (core.specName().isEmpty() ? 11 : 18);
		if (!core.specName().isEmpty() && !id.specFocus().isEmpty()) {
			h += 11;
		}
		h += 10 * textRenderer.wrapLines(Text.translatable(
				"screen.lifepath.character.spec_note"), PANEL_W - 8).size();
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_CONDITIONS, List.of()));
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_ATTUNEMENTS, List.of()));
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_TRAITS, List.of()));
		if (!id.abilities().isEmpty()) {
			h += 4 + 11 + 11 * id.abilities().size();
			if (ClientAbilityState.selected() == null) {
				h += 13;                     // "click to bind" hint line
			}
		}
		return h + 24;                           // key hint + bottom pad
	}

	private static int listHeight(List<?> entries) {
		return 4 + 11 + 11 * Math.max(1, entries.size());
	}

	private int section(DrawContext context, int x, int y, Text label) {
		context.drawTextWithShadow(textRenderer, label, x, y, ACCENT);
		return y + 11;
	}

	private int line(DrawContext context, int x, int y, Text text, int color) {
		context.drawTextWithShadow(textRenderer, text, x + 6, y, color);
		return y + 11;
	}

	private int listSection(DrawContext context, int x, int y, String key,
			String domain, List<IdentitySummaryPayload.Entry> entries) {
		y = section(context, x, y, Text.translatable(key));
		if (entries.isEmpty()) {
			return line(context, x, y,
					Text.translatable("screen.lifepath.character.none"), DIM);
		}
		for (IdentitySummaryPayload.Entry e : entries) {
			final int rowY = y;
			// 9-arg overload: draw box 10x10 sampling the whole 16x16 sprite
			// (the 8-arg form would crop, not scale).
			ClientIcons.resolve(domain, e.icon())
					.ifPresent(tex -> context.drawTexture(tex, x + 6, rowY,
							10, 10, 0, 0, 16, 16, 16, 16));
			context.drawTextWithShadow(textRenderer, Text.literal(e.name()),
					x + 19, y + 1, TEXT);
			y += 11;
		}
		return y;
	}
}
