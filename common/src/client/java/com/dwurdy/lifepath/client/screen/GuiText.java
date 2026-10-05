package com.dwurdy.lifepath.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import com.dwurdy.lifepath.platform.ClientOnly;

/**
 * Fit-to-width helpers for screen text. Every place text can overflow a
 * panel (card names, detail lines, longer translations) must go through
 * these — a hard {@code plainSubstrByWidth} clip cuts mid-word with no
 * sign of truncation, so {@link #fit} always appends "…" when it trims.
 */
@ClientOnly
final class GuiText {
	private static final String ELLIPSIS = "…";

	private GuiText() {
	}

	/** Clip a plain string to {@code maxWidth}, marking truncation. */
	static String fit(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width(ELLIPSIS))
				+ ELLIPSIS;
	}

	/** Clip a component to {@code maxWidth}, marking truncation. */
	static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text.getVisualOrderText();
		}
		FormattedCharSequence clipped = net.minecraft.locale.Language.getInstance()
				.getVisualOrder(font.substrByWidth(text,
						maxWidth - font.width(ELLIPSIS)));
		return FormattedCharSequence.composite(clipped,
				FormattedCharSequence.forward(ELLIPSIS, Style.EMPTY));
	}
}
