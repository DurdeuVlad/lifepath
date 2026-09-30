package io.github.durdeuvlad.lifepath.client.screen;

import io.github.durdeuvlad.lifepath.client.icon.ClientIcons;
import io.github.durdeuvlad.lifepath.client.platform.ClientPlatform;
import io.github.durdeuvlad.lifepath.client.selection.ClientSelectionState;
import io.github.durdeuvlad.lifepath.network.c2s.RequestSelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.c2s.SelectSpecializationPayload;
import io.github.durdeuvlad.lifepath.network.c2s.SelectSpeciesPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import java.util.List;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * M14 selection screen — the player-facing picker for species and
 * specialization (replaces {@code /lifepath species choose}).
 *
 * <p>Guided, not command-driven: the catalog arrives already resolved
 * server-side (names, descriptions, icons, "what you get" lines, and a
 * per-entry availability that knows the player's unlocks). Cards are
 * inspect-before-commit — click one to read what it grants, then Confirm
 * sends a C2S request the server re-validates; denials arrive in chat as
 * {@code selection_denied} feedback. Confirming species chains straight into
 * the specialization step when one is still pickable (TIMELINE onboarding:
 * species → focus → play).
 *
 * <p>Locked options stay visible but dimmed with a reason ("Requires
 * unlock", "Admin-only", "Already chosen") — hidden species only surface
 * once unlocked, per the catalog the server built.
 */
@ClientOnly
public class SelectionScreen extends Screen {
	public enum Step { SPECIES, SPECIALIZATION }

	private static final int TEXT = 0xFFE0E0E0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int DIM = 0xFF909090;
	private static final int WARN = 0xFFFFAA55;
	private static final int PANEL = 0xC0101015;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int CARD_EDGE = 0xFF4A4A55;
	private static final int CARD_SEL = 0xFF55FFFF;
	private static final int CARD_W = 150;
	private static final int CARD_H = 58;
	private static final int CARD_GAP = 6;
	private static final int COLS = 2;
	private static final int PANEL_W = COLS * CARD_W + CARD_GAP + 16;
	private static final int HEADER_H = 30;
	private static final int FOOTER_H = 104;

	private Step step;
	private int scroll;
	/** Selected card id — resolved against the current catalog per frame so
	 *  a refresh (unlock landed, reload) can re-lock or drop a stale pick. */
	private @Nullable String selectedId;
	private Button confirmButton;
	private Button laterButton;

	public SelectionScreen(Step step) {
		super(Component.translatable(step == Step.SPECIES
				? "screen.lifepath.selection.species_title"
				: "screen.lifepath.selection.spec_title"));
		this.step = step;
	}

	@Override
	protected void init() {
		// Freshness at the moment of choice: the join push can be stale after
		// datapack reloads or mid-session unlocks — ask for a rebuild.
		if (minecraft != null && minecraft.player != null) {
			ClientPlatform.get().sendToServer(new RequestSelectionCatalogPayload());
		}
		int buttonY = panelTop() + panelHeight() - 24;
		confirmButton = addRenderableWidget(Button.builder(
				Component.translatable("screen.lifepath.selection.confirm"),
				b -> confirm())
				.bounds(width / 2 - 104, buttonY, 100, 18)
				.build());
		laterButton = addRenderableWidget(Button.builder(
				Component.translatable("screen.lifepath.selection.later"),
				b -> onClose())
				.bounds(width / 2 + 4, buttonY, 100, 18)
				.build());
		confirmButton.active = false;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		int left = width / 2 - PANEL_W / 2;
		int top = panelTop();
		int panelH = panelHeight();
		context.fill(left - 4, top - 4, left + PANEL_W + 4, top + panelH + 4, PANEL_EDGE);
		context.fill(left - 3, top - 3, left + PANEL_W + 3, top + panelH + 3, PANEL);

		context.drawCenteredString(font, stepTitle(), width / 2, top + 6, ACCENT);
		context.drawCenteredString(font, Component.translatable(
				"screen.lifepath.selection.pick_hint"), width / 2, top + 17, DIM);

		List<Entry> entries = entries();
		int gridTop = top + HEADER_H;
		int gridBottom = top + panelH - FOOTER_H;
		if (entries.isEmpty()) {
			context.drawCenteredString(font, Component.translatable(
					"screen.lifepath.selection.empty"),
					width / 2, gridTop + (gridBottom - gridTop) / 2, DIM);
		} else {
			renderGrid(context, entries, left + 8, gridTop, gridBottom,
					mouseX, mouseY);
		}

		renderFooter(context, left, top + panelH - FOOTER_H, entries, mouseX, mouseY);
		// Widgets draw last — Confirm/Later must sit above the panel/footer.
		super.render(context, mouseX, mouseY, delta);
	}

	/** Card grid: scrollable, scissored; locked entries dim with a reason line. */
	private void renderGrid(GuiGraphics context, List<Entry> entries, int gridLeft,
			int gridTop, int gridBottom, int mouseX, int mouseY) {
		context.enableScissor(0, gridTop, width, gridBottom);
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int x = gridLeft + (i % COLS) * (CARD_W + CARD_GAP);
			int y = gridTop + (i / COLS) * (CARD_H + CARD_GAP) - scroll;
			if (y + CARD_H <= gridTop || y >= gridBottom) {
				continue;
			}
			boolean available = e.availability() == Entry.AVAILABLE;
			boolean isSelected = e.id().equals(selectedId);
			boolean hovered = mouseX >= x && mouseX < x + CARD_W
					&& mouseY >= Math.max(y, gridTop) && mouseY < Math.min(y + CARD_H, gridBottom);
			int edge = isSelected ? CARD_SEL
					: available ? (hovered ? 0xFF8A8A9A : CARD_EDGE) : 0xFF2E2E36;
			context.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, edge);
			context.fill(x, y, x + CARD_W, y + CARD_H, available ? PANEL : 0xC008080C);

			ClientIcons.resolve(domain(), e.icon())
					.ifPresent(tex -> context.blit(tex, x + 4, y + 4,
							0, 0, 16, 16, 16, 16));
			context.drawString(font,
					GuiText.fit(font, e.name(), CARD_W - 28),
					x + 24, y + 8, available ? TEXT : DIM);
			// Word-wrap (never mid-word) then cap at 3 rows; a clipped tail
			// gets "…" and the full text stays readable on hover.
			List<String> desc = wrapPlain(e.description(), CARD_W - 10);
			int dy = y + 22;
			for (int i2 = 0; i2 < Math.min(3, desc.size()); i2++) {
				String row = desc.get(i2);
				if (i2 == 2 && desc.size() > 3) {
					row = font.plainSubstrByWidth(row,
							CARD_W - 10 - font.width("…")) + "…";
				}
				context.drawString(font, row, x + 5, dy,
						available ? DIM : 0xFF606068);
				dy += 10;
			}
			if (!available) {
				context.drawString(font,
						GuiText.fit(font, Component.translatable(statusKey(e)),
								CARD_W - 10),
						x + 5, y + CARD_H - 11, WARN);
			}
			if (hovered && !e.description().isEmpty()) {
				context.renderTooltip(font,
						font.split(Component.literal(e.description()), 240),
						mouseX, mouseY);
			}
		}
		context.disableScissor();
	}

	/**
	 * The consequence strip: what the inspected card grants, plus the spec
	 * permanence warning — consequences are read before commitment, never
	 * after (M6-4 zero-confusion).
	 */
	private void renderFooter(GuiGraphics context, int left, int footerTop,
			List<Entry> entries, int mouseX, int mouseY) {
		Entry shown = selected(entries);
		if (shown == null) {
			shown = hovered(entries, mouseX, mouseY);
		}
		if (shown != null && !shown.details().isEmpty()) {
			context.drawString(font, Component.translatable(
					"screen.lifepath.selection.details"), left + 8, footerTop + 4, ACCENT);
			int dy = footerTop + 14;
			for (int i = 0; i < Math.min(4, shown.details().size()); i++) {
				context.drawString(font,
						GuiText.fit(font, shown.details().get(i), PANEL_W - 16),
						left + 8, dy, DIM);
				dy += 10;
			}
		}
		if (step == Step.SPECIALIZATION) {
			// Between the details strip and the buttons — the last thing
			// read before committing a one-time pick. Wrapped so longer
			// translations stay inside the panel instead of bleeding out.
			int wy = footerTop + 52;
			for (var line : font.split(Component.translatable(
					"screen.lifepath.selection.spec_warning"), PANEL_W - 16)) {
				context.drawCenteredString(font, line, width / 2, wy, WARN);
				wy += 10;
			}
		}
		Entry sel = selected(entries);
		confirmButton.active = sel != null && sel.availability() == Entry.AVAILABLE;
	}

	/** The selected card resolved against the current catalog — null if the
	 *  refreshed list dropped it. */
	private @Nullable Entry selected(List<Entry> entries) {
		if (selectedId == null) {
			return null;
		}
		for (Entry e : entries) {
			if (e.id().equals(selectedId)) {
				return e;
			}
		}
		return null;
	}

	private @Nullable Entry hovered(List<Entry> entries, int mouseX, int mouseY) {
		int gridTop = panelTop() + HEADER_H;
		int gridBottom = panelTop() + panelHeight() - FOOTER_H;
		if (mouseY < gridTop || mouseY >= gridBottom) {
			return null;
		}
		int idx = cardIndexAt(mouseX, mouseY);
		return idx >= 0 && idx < entries.size() ? entries.get(idx) : null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int gridTop = panelTop() + HEADER_H;
			int gridBottom = panelTop() + panelHeight() - FOOTER_H;
			if (mouseY >= gridTop && mouseY < gridBottom) {
				int idx = cardIndexAt(mouseX, mouseY);
				List<Entry> entries = entries();
				if (idx >= 0 && idx < entries.size()) {
					Entry e = entries.get(idx);
					if (e.availability() == Entry.AVAILABLE) {
						selectedId = e.id();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private int cardIndexAt(double mouseX, double mouseY) {
		int gridLeft = width / 2 - PANEL_W / 2 + 8;
		double relX = mouseX - gridLeft;
		int col = (int) (relX / (CARD_W + CARD_GAP));
		if (col < 0 || col >= COLS || relX - col * (CARD_W + CARD_GAP) >= CARD_W) {
			return -1;
		}
		int row = (int) ((mouseY - (panelTop() + HEADER_H) + scroll)
				/ (CARD_H + CARD_GAP));
		if (row < 0 || (mouseY - (panelTop() + HEADER_H) + scroll)
				- row * (CARD_H + CARD_GAP) >= CARD_H) {
			return -1;
		}
		return row * COLS + col;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY,
			double horizontalAmount, double verticalAmount) {
		int rows = (entries().size() + COLS - 1) / COLS;
		int gridH = panelHeight() - HEADER_H - FOOTER_H;
		int max = Math.max(0, rows * (CARD_H + CARD_GAP) - CARD_GAP - gridH);
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * 20));
		return true;
	}

	/**
	 * Sends the pick, then continues the guided flow: a species confirm
	 * chains into the specialization step while a spec is still pickable;
	 * otherwise the screen is done. Success feedback (species_assigned /
	 * spec_assigned) arrives via the sync diff — no optimistic state here.
	 */
	private void confirm() {
		Entry sel = selected(entries());
		if (sel == null || sel.availability() != Entry.AVAILABLE) {
			return;
		}
		ResourceLocation id = ResourceLocation.tryParse(sel.id());
		if (id == null) {
			return;
		}
		if (step == Step.SPECIES) {
			ClientPlatform.get().sendToServer(new SelectSpeciesPayload(id));
			boolean specPickable = ClientSelectionState.catalog().specializations()
					.stream().anyMatch(e -> e.availability() == Entry.AVAILABLE);
			if (specPickable) {
				step = Step.SPECIALIZATION;
				selectedId = null;
				scroll = 0;
				return;
			}
		} else {
			ClientPlatform.get().sendToServer(new SelectSpecializationPayload(id));
		}
		onClose();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(null);
	}

	private List<Entry> entries() {
		SelectionCatalogPayload catalog = ClientSelectionState.catalog();
		return step == Step.SPECIES ? catalog.species() : catalog.specializations();
	}

	private String domain() {
		return step == Step.SPECIES ? "species" : "specialization";
	}

	private Component stepTitle() {
		return Component.translatable(step == Step.SPECIES
				? "screen.lifepath.selection.species_title"
				: "screen.lifepath.selection.spec_title");
	}

	/** Greedy word wrap on plain text. font.split can't be used here: it
	 *  returns FormattedCharSequence rows which can't be re-measured as
	 *  strings to mark a capped tail with "…". */
	private List<String> wrapPlain(String text, int width) {
		List<String> out = new java.util.ArrayList<>();
		for (String para : text.split("\n")) {
			String rest = para.strip();
			while (!rest.isEmpty()) {
				int idx = font.getSplitter().plainIndexAtWidth(rest, width,
						net.minecraft.network.chat.Style.EMPTY);
				String head;
				if (idx <= 0) {
					head = font.plainSubstrByWidth(rest, width);
				} else {
					head = rest.substring(0, idx);
					// plainIndexAtWidth is char-precise, not word-aware —
					// back off to the last space so a word never splits.
					if (idx < rest.length() && !Character.isWhitespace(
							rest.charAt(idx))) {
						int space = head.lastIndexOf(' ');
						if (space > 0) {
							head = head.substring(0, space);
						}
					}
				}
				if (head.isEmpty()) {
					break;
				}
				out.add(head.strip());
				rest = rest.substring(head.length()).strip();
			}
		}
		return out;
	}

	private String statusKey(Entry e) {
		return switch (e.availability()) {
			case Entry.NEEDS_UNLOCK -> "screen.lifepath.selection.locked_unlock";
			case Entry.ADMIN_ONLY -> "screen.lifepath.selection.locked_admin";
			case Entry.ALREADY_CHOSEN -> "screen.lifepath.selection.locked_chosen";
			default -> "screen.lifepath.selection.locked_unavailable";
		};
	}

	private int panelHeight() {
		return Math.min(height - 16, 30 + 3 * (CARD_H + CARD_GAP) + FOOTER_H);
	}

	private int panelTop() {
		return Math.max(8, (height - panelHeight()) / 2);
	}
}
