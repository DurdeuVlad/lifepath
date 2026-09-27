package io.github.durdeuvlad.lifepath.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * M12-1: the {@code icon} datapack field shared by every UI-rendered content
 * type. An icon value is a <b>reference</b>, not a file check — the texture
 * itself lives under {@code assets/} and is resolved client-side only, so a
 * dedicated server never touches art.
 *
 * <p><b>Field syntax</b> (data files):
 * <ul>
 *   <li>{@code "icon": "species/human"} — shorthand resolved to
 *       {@code lifepath:textures/gui/species/human.png}
 *   <li>{@code "icon": "species/human.png"} — same, {@code .png} already present
 *   <li>{@code "icon": "othermod:textures/gui/species/human.png"} — explicit
 *       identifier, taken literally (escape hatch for pack-provided art)
 * </ul>
 *
 * <p><b>Leniency contract:</b> a malformed icon must never cost the file its
 * content — {@link #resolve} records a warning (surfaced by the M7-5
 * validation pass via {@link #drainWarnings()}) and returns empty so the def
 * loads icon-less. By contrast a non-string {@code icon} (number, object)
 * still fails the file like any other wrong-typed field.
 */
public final class IconRef {
	/** Asset directory shorthand values resolve under (no leading slash). */
	public static final String GUI_ROOT = "textures/gui/";

	/**
	 * Warnings recorded while content files decode. Shape mirrors
	 * {@code ValidationReport.Issue} without importing the registry layer —
	 * {@code LifepathContent.validateAll} drains and re-wraps them.
	 */
	public record Warning(String domain, Identifier file, String field, String message) {
	}

	// Domain loaders can run on parallel reload threads — synchronize;
	// warnings are rare enough that contention is a non-issue.
	private static final List<Warning> PENDING =
			java.util.Collections.synchronizedList(new ArrayList<>());

	private IconRef() {
	}

	/**
	 * Decodes one {@code icon} string into the texture {@link Identifier} it
	 * points at. Explicit {@code ns:path} values are literal; bare paths are
	 * shorthand for {@code lifepath:textures/gui/<path>.png}. Malformed input
	 * warns and returns {@code null} — callers store {@code Optional.empty()}.
	 */
	@Nullable
	public static Identifier resolve(String domain, Identifier file, String raw) {
		if (raw.isBlank()) {
			warn(domain, file, "malformed icon '" + raw + "' — icon ignored");
			return null;
		}
		Identifier base = raw.indexOf(':') >= 0
				? Identifier.tryParse(raw)
				: Identifier.tryParse("lifepath:" + raw);
		if (base == null || base.getPath().isBlank()) {
			warn(domain, file, "malformed icon '" + raw + "' — icon ignored");
			return null;
		}
		if (raw.indexOf(':') >= 0) {
			return base;
		}
		String path = base.getPath();
		if (!path.endsWith(".png")) {
			path = path + ".png";
		}
		return Identifier.of(base.getNamespace(), GUI_ROOT + path);
	}

	/**
	 * Drains warnings accumulated since the last call. Called once per
	 * validation pass (after the domain loaders), so a reload always reports
	 * the CURRENT files' problems and never replays stale ones.
	 */
	public static List<Warning> drainWarnings() {
		synchronized (PENDING) {
			List<Warning> out = List.copyOf(PENDING);
			PENDING.clear();
			return out;
		}
	}

	private static void warn(String domain, Identifier file, String message) {
		PENDING.add(new Warning(domain, file, "icon", message));
	}
}
