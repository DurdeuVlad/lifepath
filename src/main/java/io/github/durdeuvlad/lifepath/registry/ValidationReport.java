package io.github.durdeuvlad.lifepath.registry;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.util.Identifier;

/**
 * M7-5: the grouped result of a full content-validation pass. Every detected
 * problem is collected — nothing short-circuits — then grouped by domain for
 * a single console/command summary: {@code domain -> file -> field ->
 * problem}. The same report object is produced on startup and on
 * {@code /lifepath reload} (one code path — {@link LifepathContent}'s
 * {@code content_validation} reloader).
 */
public record ValidationReport(List<Issue> issues) {

	public record Issue(Severity severity, String domain, Identifier file,
			String field, String message) {
	}

	public enum Severity {
		WARN, ERROR
	}

	public long errorCount() {
		return issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
	}

	public long warnCount() {
		return issues.stream().filter(i -> i.severity() == Severity.WARN).count();
	}

	public boolean hasErrors() {
		return errorCount() > 0;
	}

	/** One summary line: the reload command's compact verdict. */
	public String summaryLine() {
		return issues.isEmpty()
				? "content validation clean — no issues"
				: "content validation: " + errorCount() + " error(s), "
						+ warnCount() + " warning(s) across "
						+ issues.stream().map(Issue::domain).distinct().count() + " domain(s)";
	}

	/**
	 * Grouped detail lines for log/console: domain header then one line per
	 * issue naming file + field + problem.
	 */
	public List<String> detailLines() {
		Map<String, List<Issue>> byDomain = new TreeMap<>();
		for (Issue i : issues) {
			byDomain.computeIfAbsent(i.domain(), d -> new java.util.ArrayList<>()).add(i);
		}
		java.util.List<String> out = new java.util.ArrayList<>();
		byDomain.forEach((domain, list) -> {
			out.add("[" + domain + "]");
			for (Issue i : list) {
				out.add("  " + i.severity() + " " + i.file() + " " + i.field()
						+ ": " + i.message());
			}
		});
		return List.copyOf(out);
	}
}
