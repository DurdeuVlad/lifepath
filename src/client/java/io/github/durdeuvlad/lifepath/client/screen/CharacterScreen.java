package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * M6-1 character screen — the identity hub ("what am I?"). Pure read model:
 * renders {@link ClientCharacterState}'s synced snapshot + resolved identity
 * summary; it sends nothing and mutates nothing (there is no C2S channel for
 * character state).
 *
 * <p>Hierarchy (TIMELINE §1.3): identity first, then the actionable cards
 * (conditions/attunements/traits). Empty sections render explicit friendly
 * states rather than blank space.
 */
@Environment(EnvType.CLIENT)
public class CharacterScreen extends Screen {
	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;

	public CharacterScreen() {
		super(Text.translatable("screen.lifepath.character.title"));
	}

	@Override
	protected void init() {
		// M6-2: Skills entry point — the identity hub links to the skills list.
		addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
				Text.translatable("screen.lifepath.character.skills_button"),
				b -> client.setScreen(new SkillsScreen()))
				.dimensions(width / 2 - 60, height / 2 + 88, 120, 18)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - 110;
		int top = height / 2 - 80;
		int panelW = 220;
		int panelH = 160;
		context.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, PANEL);

		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.lifepath.character.title"),
				width / 2, top + 4, ACCENT);

		IdentitySummaryPayload id = ClientCharacterState.identity();
		IdentitySummaryPayload.IdentityCore core = id.identity();
		int y = top + 22;

		// --- Species (the identity hero line) ---
		y = section(context, left, y,
				Text.translatable("screen.lifepath.character.species"));
		if (core.speciesName().isEmpty()) {
			y = line(context, left, y,
					Text.translatable("screen.lifepath.character.no_species"), DIM);
		} else {
			y = line(context, left, y, Text.literal(core.speciesName()), TEXT);
			if (!core.speciesDescription().isEmpty()) {
				for (var wrapped : textRenderer.wrapLines(
						Text.literal(core.speciesDescription()), panelW - 8)) {
					context.drawTextWithShadow(textRenderer, wrapped, left, y, DIM);
					y += 10;
				}
			}
		}

		// --- Specialization + starting focus ---
		y = section(context, left, y + 4,
				Text.translatable("screen.lifepath.character.specialization"));
		if (core.specName().isEmpty()) {
			y = line(context, left, y,
					Text.translatable("screen.lifepath.character.no_specialization"), DIM);
		} else {
			y = line(context, left, y, Text.literal(core.specName()), TEXT);
			if (!id.specFocus().isEmpty()) {
				y = line(context, left, y, Text.translatable(
						"screen.lifepath.character.focus",
						String.join(", ", id.specFocus())), DIM);
			}
		}

		// --- Significant ids: conditions / attunements / traits ---
		y = listSection(context, left, y + 4,
				"screen.lifepath.character.conditions",
				id.sections().getOrDefault(IdentitySummary.SECTION_CONDITIONS, List.of()));
		y = listSection(context, left, y + 4,
				"screen.lifepath.character.attunements",
				id.sections().getOrDefault(IdentitySummary.SECTION_ATTUNEMENTS, List.of()));
		listSection(context, left, y + 4,
				"screen.lifepath.character.traits",
				id.sections().getOrDefault(IdentitySummary.SECTION_TRAITS, List.of()));
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
			List<String> entries) {
		y = section(context, x, y, Text.translatable(key));
		if (entries.isEmpty()) {
			return line(context, x, y,
					Text.translatable("screen.lifepath.character.none"), DIM);
		}
		for (String name : entries) {
			y = line(context, x, y, Text.literal(name), TEXT);
		}
		return y;
	}
}
