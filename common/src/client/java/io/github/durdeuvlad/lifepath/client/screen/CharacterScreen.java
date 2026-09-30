package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.ability.ClientAbilityState;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import java.util.ArrayList;
import java.util.List;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
@ClientOnly
public class CharacterScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int PANEL_W = 220;

	/** Clickable active-ability rows, rebuilt every render pass. */
	private final List<AbilityRow> abilityRows = new ArrayList<>();

	/** The skills entry point — re-anchored under the panel each render so
	 *  growing/shrinking content never leaves it stranded or overlapped. */
	private net.minecraft.client.gui.components.Button skillsButton;

	/** M14: the selection picker's manual entry point — label follows the
	 *  still-outstanding choice (species → specialization → re-pick). */
	private net.minecraft.client.gui.components.Button selectionButton;

	/** Hit rect for one clickable ability row (screen coordinates). */
	private record AbilityRow(String id, int x, int y, int w, int h) {}

	public CharacterScreen() {
		super(Component.translatable("screen.lifepath.character.title"));
	}

	@Override
	protected void init() {
		// M6-2: Skills entry point — the identity hub links to the skills list.
		skillsButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
				Component.translatable("screen.lifepath.character.skills_button"),
				b -> minecraft.setScreen(new SkillsScreen()))
				.bounds(width / 2 - 102,
						panelTop() + panelHeight(
								ClientCharacterState.identity()) + 6,
						96, 18)
				.build());
		// M14: Choose… — opens the guided selection screen at whichever pick
		// is still outstanding; the screen requests a fresh catalog on open.
		selectionButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
				Component.translatable("screen.lifepath.character.choose_species"),
				b -> minecraft.setScreen(new SelectionScreen(selectionStep())))
				.bounds(width / 2 + 6,
						panelTop() + panelHeight(
								ClientCharacterState.identity()) + 6,
						96, 18)
				.build());
	}

	/** Which pick the Choose button should open on, in identity state. */
	private SelectionScreen.Step selectionStep() {
		IdentitySummaryPayload.IdentityCore core =
				ClientCharacterState.identity().identity();
		if (core.speciesName().getString().isEmpty()) {
			return SelectionScreen.Step.SPECIES;
		}
		return core.specName().getString().isEmpty()
				? SelectionScreen.Step.SPECIALIZATION
				: SelectionScreen.Step.SPECIES;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 110;
		int panelW = PANEL_W;
		IdentitySummaryPayload id = ClientCharacterState.identity();
		IdentitySummaryPayload.IdentityCore core = id.identity();
		// The panel wraps measured content — sections flow past a fixed
		// height, so it must grow or the key hint lands on the last rows.
		int panelH = panelHeight(id);
		int top = panelTop(panelH);
		if (skillsButton != null) {
			skillsButton.setY(top + panelH + 6);
		}
		if (selectionButton != null) {
			selectionButton.setY(top + panelH + 6);
			selectionButton.setMessage(Component.translatable(
					core.speciesName().getString().isEmpty()
							? "screen.lifepath.character.choose_species"
							: core.specName().getString().isEmpty()
									? "screen.lifepath.character.choose_focus"
									: "screen.lifepath.character.change_species"));
		}
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);

		context.drawCenteredString(font,
				Component.translatable("screen.lifepath.character.title"),
				width / 2, top + 4, ACCENT);

		int y = top + 22;

		// --- Species (the identity hero line) ---
		y = section(context, left, y,
				Component.translatable("screen.lifepath.character.species"));
		if (core.speciesName().getString().isEmpty()) {
			y = line(context, left, y,
					Component.translatable("screen.lifepath.character.no_species"), DIM);
		} else {
			int rowTop = y;
			ClientIcons.resolve("species", core.speciesIcon())
					.ifPresent(tex -> context.blit(tex, left + 6, rowTop - 1,
							0, 0, 16, 16, 16, 16));
			context.drawString(font,
					GuiText.fit(font, core.speciesName(), panelW - 32),
					left + 26, rowTop + 4, TEXT);
			y = rowTop + 18;
			// M12-2: the description is displaced from the default view —
			// hover the hero row to read it (name stays always-on).
			if (!core.speciesDescription().getString().isEmpty() && mouseX >= left
					&& mouseX <= left + panelW && mouseY >= rowTop - 2
					&& mouseY <= rowTop + 16) {
				context.renderTooltip(font,
						font.split(
								core.speciesDescription(), panelW - 8),
						mouseX, mouseY);
			}
		}

		// --- Specialization + starting focus ---
		y = section(context, left, y + 4,
				Component.translatable("screen.lifepath.character.specialization"));
		if (core.specName().getString().isEmpty()) {
			y = line(context, left, y,
					Component.translatable("screen.lifepath.character.no_specialization"), DIM);
		} else {
			final int specRowY = y;
			ClientIcons.resolve("specialization", core.specIcon())
					.ifPresent(tex -> context.blit(tex, left + 6,
							specRowY - 1, 0, 0, 16, 16, 16, 16));
			context.drawString(font,
					GuiText.fit(font, core.specName(), panelW - 32),
					left + 26, y + 4, TEXT);
			y += 18;
			if (!id.specFocus().isEmpty()) {
				String focusNames = id.specFocus().stream()
						.map(e -> e.name().getString())
						.collect(java.util.stream.Collectors.joining(", "));
				y = line(context, left, y, Component.translatable(
						"screen.lifepath.character.focus", focusNames), DIM);
			}
		}
		// M6-4 mandated line — a spec choice must never read as a lockout.
		for (var wrapped : font.split(Component.translatable(
				"screen.lifepath.character.spec_note"), panelW - 8)) {
			context.drawString(font, wrapped, left + 6, y, DIM);
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
			ResourceLocation selected = ClientAbilityState.selected();
			if (selected != null
					&& !id.abilities().containsKey(selected.toString())) {
				ClientAbilityState.clear();
				selected = null;
			}
			y = section(context, left, y + 4,
					Component.translatable("screen.lifepath.character.abilities"));
			// Actives lead — they're the clickable rows — then passives; both
			// groups keep the server's owned-set order within themselves.
			List<IdentitySummaryPayload.AbilityEntry> ordered = new ArrayList<>();
			for (var e : id.abilities().values()) {
				if (e.active()) {
					ordered.add(e);
				}
			}
			for (var e : id.abilities().values()) {
				if (!e.active()) {
					ordered.add(e);
				}
			}
			for (IdentitySummaryPayload.AbilityEntry e : ordered) {
				boolean sel = selected != null
						&& e.id().equals(selected.toString());
				final int rowY = y;
				ClientIcons.resolve("ability", e.icon())
						.ifPresent(tex -> context.blit(tex, left + 6,
								rowY, 10, 10, 0, 0, 16, 16, 16, 16));
				Component label = e.active()
						? (sel ? Component.literal("> ").append(e.name())
								: e.name())
						: Component.translatable(
								"screen.lifepath.character.ability_passive",
								e.name());
				context.drawString(font, GuiText.fit(font, label, panelW - 25),
						left + 19, y + 1, sel ? ACCENT : (e.active() ? TEXT : DIM));
				if (e.active()) {
					abilityRows.add(new AbilityRow(e.id(), left, y, panelW, 11));
				}
				y += 11;
			}
			if (selected == null
					&& io.github.durdeuvlad.lifepath.client.LifepathClient
							.abilityKey != null) {
				// Wrapped, not clipped — a truncated hint teaches nothing.
				for (var wrapped : font.split(Component.translatable(
						"screen.lifepath.character.abilities_hint",
						io.github.durdeuvlad.lifepath.client.LifepathClient
								.abilityKey.getTranslatedKeyMessage()),
						panelW - 8)) {
					context.drawString(font, wrapped, left + 6, y + 2, DIM);
					y += 10;
				}
			}
		}

		// M6-4: visible keybind hints — the ability key is never discoverable
		// otherwise. Shows the ACTUAL bound key, not a hardcoded letter.
		if (io.github.durdeuvlad.lifepath.client.LifepathClient.abilityKey != null) {
			context.drawCenteredString(font,
					GuiText.fit(font, Component.translatable(
							"screen.lifepath.character.key_hint",
							io.github.durdeuvlad.lifepath.client.LifepathClient
									.abilityKey.getTranslatedKeyMessage()),
							panelW - 8),
					width / 2, top + panelH - 14, DIM);
		}
		// Widgets draw last — Skills/Choose must sit above the panel.
		// Rendered explicitly rather than via super.render: Screen.render
		// calls renderBackground again, and the framebuffer blur pass would
		// smear everything drawn so far under it.
		skillsButton.render(context, mouseX, mouseY, delta);
		selectionButton.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (AbilityRow row : abilityRows) {
				if (mouseX >= row.x() && mouseX < row.x() + row.w()
						&& mouseY >= row.y() && mouseY < row.y() + row.h()) {
					ResourceLocation picked = ResourceLocation.tryParse(row.id());
					if (picked != null) {
						ClientAbilityState.select(picked);
						return true;
					}
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
		h += 11 + (core.speciesName().getString().isEmpty() ? 11 : 18);
		h += 4 + 11 + (core.specName().getString().isEmpty() ? 11 : 18);
		if (!core.specName().getString().isEmpty() && !id.specFocus().isEmpty()) {
			h += 11;
		}
		h += 10 * font.split(Component.translatable(
				"screen.lifepath.character.spec_note"), PANEL_W - 8).size();
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_CONDITIONS, List.of()));
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_ATTUNEMENTS, List.of()));
		h += listHeight(id.sections().getOrDefault(
				IdentitySummary.SECTION_TRAITS, List.of()));
		if (!id.abilities().isEmpty()) {
			h += 4 + 11 + 11 * id.abilities().size();
			// Mirrors the render condition exactly — including the keybind
			// null-check — so the panel never carries dead space.
			if (ClientAbilityState.selected() == null
					&& io.github.durdeuvlad.lifepath.client.LifepathClient
							.abilityKey != null) {
				// Mirrors the wrapped hint above — the panel must grow
				// with it or the key hint lands on the last rows.
				h += 10 * font.split(Component.translatable(
						"screen.lifepath.character.abilities_hint",
						io.github.durdeuvlad.lifepath.client.LifepathClient
								.abilityKey.getTranslatedKeyMessage()),
						PANEL_W - 8).size();
			}
		}
		return h + 24;                           // key hint + bottom pad
	}

	private static int listHeight(List<?> entries) {
		return 4 + 11 + 11 * Math.max(1, entries.size());
	}

	private int section(GuiGraphics context, int x, int y, Component label) {
		context.drawString(font, label, x, y, ACCENT);
		return y + 11;
	}

	private int line(GuiGraphics context, int x, int y, Component text, int color) {
		context.drawString(font, GuiText.fit(font, text, PANEL_W - 8),
				x + 6, y, color);
		return y + 11;
	}

	private int listSection(GuiGraphics context, int x, int y, String key,
			String domain, List<IdentitySummaryPayload.Entry> entries) {
		y = section(context, x, y, Component.translatable(key));
		if (entries.isEmpty()) {
			return line(context, x, y,
					Component.translatable("screen.lifepath.character.none"), DIM);
		}
		for (IdentitySummaryPayload.Entry e : entries) {
			final int rowY = y;
			// 9-arg overload: draw box 10x10 sampling the whole 16x16 sprite
			// (the 8-arg form would crop, not scale).
			ClientIcons.resolve(domain, e.icon())
					.ifPresent(tex -> context.blit(tex, x + 6, rowY,
							10, 10, 0, 0, 16, 16, 16, 16));
			context.drawString(font, GuiText.fit(font, e.name(), PANEL_W - 25),
					x + 19, y + 1, TEXT);
			y += 11;
		}
		return y;
	}
}
