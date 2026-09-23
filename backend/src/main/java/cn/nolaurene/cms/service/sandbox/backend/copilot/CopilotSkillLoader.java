package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers skills using the directory convention implemented by the
 * GitHub Copilot SDK.
 *
 * <p>Each configured root is searched one level deep.  A child is a skill
 * only when it is a directory containing a regular {@code SKILL.md} file.
 * The first root wins when two roots expose the same skill name, matching the
 * Copilot CLI's priority semantics.  Invalid or unreadable entries are
 * skipped so one broken optional skill cannot make a session unusable.</p>
 */
public final class CopilotSkillLoader {

    public static final String SKILL_FILE_NAME = "SKILL.md";

    private final List<Path> roots;
    private final Set<String> disabledNames;

    /** Creates a loader with no configured roots. */
    public CopilotSkillLoader() {
        this(Collections.emptyList(), Collections.emptySet());
    }

    public CopilotSkillLoader(Collection<Path> roots) {
        this(roots, Collections.emptySet());
    }

    public CopilotSkillLoader(Collection<Path> roots, Collection<String> disabledNames) {
        this.roots = immutablePaths(roots);
        this.disabledNames = immutableNames(disabledNames);
    }

    public CopilotSkillLoader(Path root) {
        this(root == null ? Collections.emptyList() : List.of(root), Collections.emptySet());
    }

    public CopilotSkillLoader(Path root, Collection<String> disabledNames) {
        this(root == null ? Collections.emptyList() : List.of(root), disabledNames);
    }

    private static List<Path> immutablePaths(Collection<Path> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        for (Path value : values) {
            if (value != null) {
                result.add(value.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> immutableNames(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return Set.copyOf(result);
    }

    /** Returns the immutable, normalized roots configured for this loader. */
    public List<Path> roots() {
        return roots;
    }

    public List<Path> getRoots() {
        return roots;
    }

    /** Returns the immutable set of names suppressed by this loader. */
    public Set<String> disabledNames() {
        return disabledNames;
    }

    public Set<String> getDisabledNames() {
        return disabledNames;
    }

    /**
     * Discovers skills from the roots configured in this loader.
     *
     * <p>This convenience method never leaks checked filesystem exceptions;
     * unreadable roots and entries are ignored.  Use {@link #discoverChecked}
     * when callers need to distinguish an empty directory from an I/O error.</p>
     */
    public List<CopilotSkillDescriptor> load() {
        return discover(roots, disabledNames);
    }

    /** Descriptive alias matching the SDK's terminology. */
    public List<CopilotSkillDescriptor> loadSkills() {
        return load();
    }

    /** Alias for {@link #load()}. */
    public List<CopilotSkillDescriptor> discover() {
        return load();
    }

    /**
     * Parse the optional YAML frontmatter from one SKILL.md document.  The
     * returned map contains scalar values and block-scalar text, preserving
     * colons and quoted values without requiring a YAML dependency.
     */
    public static Map<String, String> parseFrontmatter(String source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(parseMarkdown(source).fields));
    }

    /** Return the markdown instruction body after frontmatter, if present. */
    public static String extractBody(String source) {
        return parseMarkdown(source).body;
    }

    /** Discovers all eligible skills under one root. */
    public List<CopilotSkillDescriptor> discover(Path root) {
        return discover(root, disabledNames);
    }

    public List<CopilotSkillDescriptor> loadSkills(Path root) {
        return discover(root, disabledNames);
    }

    /** Discovers all eligible skills under one root with explicit disables. */
    public List<CopilotSkillDescriptor> discover(Path root, Collection<String> disabledNames) {
        if (root == null) {
            return List.of();
        }
        return discover(List.of(root), disabledNames);
    }

    /**
     * Discovers skills from multiple roots.  Earlier roots have higher
     * priority for duplicate names.
     */
    public List<CopilotSkillDescriptor> discover(Collection<Path> roots,
                                                  Collection<String> disabledNames) {
        Set<String> disabled = immutableNames(disabledNames);
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }

        Map<String, CopilotSkillDescriptor> byName = new LinkedHashMap<>();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            try {
                discoverRoot(root, disabled, byName);
            } catch (IOException ignored) {
                // Optional skill roots are best-effort, like the Copilot CLI.
            } catch (SecurityException ignored) {
                // A denied root must not prevent other roots from loading.
            }
        }
        return List.copyOf(byName.values());
    }

    /**
     * Checked variant useful to applications that want to report root-level
     * filesystem failures.  Individual malformed files remain skippable.
     */
    public List<CopilotSkillDescriptor> discoverChecked(Path root) throws IOException {
        return discoverChecked(root, disabledNames);
    }

    public List<CopilotSkillDescriptor> discoverChecked(Path root,
                                                        Collection<String> disabledNames)
            throws IOException {
        if (root == null) {
            return List.of();
        }
        Set<String> disabled = immutableNames(disabledNames);
        Map<String, CopilotSkillDescriptor> byName = new LinkedHashMap<>();
        discoverRoot(root, disabled, byName);
        return List.copyOf(byName.values());
    }

    /**
     * Reads one skill directory after applying the same containment checks as
     * discovery.  The supplied root is the parent skill directory.
     */
    public CopilotSkillDescriptor load(Path root, Path skillDirectory) {
        if (root == null || skillDirectory == null) {
            return null;
        }
        try {
            Path safeRoot = realDirectory(root);
            Path candidate = skillDirectory.toAbsolutePath().normalize();
            if (!candidate.getParent().equals(root.toAbsolutePath().normalize())) {
                return null;
            }
            Path safeDirectory = realDirectory(candidate);
            if (!safeDirectory.getParent().equals(safeRoot)) {
                return null;
            }
            CopilotSkillDescriptor descriptor = readDescriptor(safeRoot, safeDirectory, safeRoot);
            return descriptor == null || disabledNames.contains(descriptor.name()) ? null : descriptor;
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    /**
     * Checked implementation retained for integrations that prefer explicit
     * I/O handling.
     */
    public List<CopilotSkillDescriptor> discoverChecked(Collection<Path> roots,
                                                        Collection<String> disabledNames)
            throws IOException {
        Set<String> disabled = immutableNames(disabledNames);
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Map<String, CopilotSkillDescriptor> byName = new LinkedHashMap<>();
        for (Path root : roots) {
            if (root != null) {
                discoverRoot(root, disabled, byName);
            }
        }
        return List.copyOf(byName.values());
    }

    private void discoverRoot(Path configuredRoot,
                               Set<String> disabled,
                               Map<String, CopilotSkillDescriptor> byName)
            throws IOException {
        Path root = realDirectory(configuredRoot);
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString()));

        for (Path child : children) {
            if (!Files.isDirectory(child)) {
                continue;
            }
            Path safeChild;
            try {
                safeChild = realDirectory(child);
            } catch (IOException | SecurityException ignored) {
                continue;
            }
            // A symlinked child must not escape the configured root.
            if (!safeChild.getParent().equals(root)) {
                continue;
            }
            CopilotSkillDescriptor descriptor;
            try {
                descriptor = readDescriptor(root, safeChild, root);
            } catch (IOException | SecurityException ignored) {
                continue;
            }
            if (descriptor == null || disabled.contains(descriptor.name())) {
                continue;
            }
            // First configured root wins, including when names are declared in
            // frontmatter and differ from their directory names.
            byName.putIfAbsent(descriptor.name(), descriptor);
        }
    }

    private static Path realDirectory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new IOException("Skill root is not a directory: " + path);
        }
        return absolute.toRealPath();
    }

    private static CopilotSkillDescriptor readDescriptor(Path root,
                                                          Path skillDirectory,
                                                          Path containmentRoot)
            throws IOException {
        Path skillFile = skillDirectory.resolve(SKILL_FILE_NAME).normalize();
        if (!skillFile.getParent().equals(skillDirectory)
                || !Files.isRegularFile(skillFile)) {
            return null;
        }
        Path safeFile = skillFile.toRealPath();
        if (!safeFile.getParent().equals(skillDirectory)
                || !safeFile.startsWith(containmentRoot)) {
            return null;
        }

        String source = Files.readString(safeFile, StandardCharsets.UTF_8);
        ParsedMarkdown parsed = parseMarkdown(source);
        String fallbackName = skillDirectory.getFileName() == null
                ? safeFile.getFileName().toString()
                : skillDirectory.getFileName().toString();
        String name = isBlank(parsed.name) ? fallbackName : parsed.name.trim();
        if (isBlank(name)) {
            return null;
        }
        return new CopilotSkillDescriptor(name, parsed.description, root, safeFile, parsed.body);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Parsed frontmatter plus markdown body. */
    private static final class ParsedMarkdown {
        private Map<String, String> fields = Map.of();
        private String name;
        private String description;
        private String body;
    }

    /**
     * Parses only the small YAML subset used by Copilot skills.  Keeping this
     * parser local avoids pulling a YAML dependency into the executor: scalar
     * values may be quoted, contain colons, or use YAML literal/folded blocks.
     */
    static ParsedMarkdown parseMarkdown(String source) {
        ParsedMarkdown parsed = new ParsedMarkdown();
        if (source == null) {
            parsed.body = "";
            return parsed;
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }

        int firstLineEnd = normalized.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? normalized : normalized.substring(0, firstLineEnd);
        if (!isFrontmatterDelimiter(firstLine)) {
            parsed.body = normalized;
            return parsed;
        }

        String[] lines = normalized.split("\n", -1);
        int end = -1;
        for (int i = 1; i < lines.length; i++) {
            if (isFrontmatterDelimiter(lines[i])) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            // An unterminated frontmatter block is treated as ordinary
            // markdown rather than silently discarding the whole document.
            parsed.body = normalized;
            return parsed;
        }

        Map<String, String> fields = parseFrontmatter(lines, 1, end);
        parsed.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        parsed.name = fields.get("name");
        parsed.description = fields.getOrDefault("description", "");
        StringBuilder body = new StringBuilder();
        for (int i = end + 1; i < lines.length; i++) {
            if (i > end + 1) {
                body.append('\n');
            }
            body.append(lines[i]);
        }
        parsed.body = body.toString();
        return parsed;
    }

    private static boolean isFrontmatterDelimiter(String line) {
        if (line == null || !line.equals(line.stripLeading())) {
            return false;
        }
        String marker = line.trim();
        return "---".equals(marker) || "...".equals(marker);
    }

    private static Map<String, String> parseFrontmatter(String[] lines, int start, int end) {
        Map<String, String> values = new HashMap<>();
        for (int i = start; i < end; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            int colon = findKeyColon(line);
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            if (key.isEmpty()) {
                continue;
            }
            String rawValue = line.substring(colon + 1).trim();
            String blockMarker = stripInlineComment(rawValue);
            if ("|".equals(blockMarker) || ">".equals(blockMarker)
                    || blockMarker.startsWith("|-") || blockMarker.startsWith("|+")
                    || blockMarker.startsWith(">-") || blockMarker.startsWith(">+")) {
                boolean folded = blockMarker.charAt(0) == '>';
                StringBuilder block = new StringBuilder();
                int indentation = Integer.MAX_VALUE;
                for (int lookahead = i + 1; lookahead < end; lookahead++) {
                    String continuation = lines[lookahead];
                    if (continuation.trim().isEmpty()) {
                        continue;
                    }
                    if (!Character.isWhitespace(continuation.charAt(0))) {
                        break;
                    }
                    indentation = Math.min(indentation, leadingWhitespace(continuation));
                }
                if (indentation == Integer.MAX_VALUE) {
                    indentation = 0;
                }
                int next = i + 1;
                while (next < end) {
                    String continuation = lines[next];
                    if (continuation.trim().isEmpty()) {
                        block.append('\n');
                        next++;
                        continue;
                    }
                    if (!Character.isWhitespace(continuation.charAt(0))) {
                        break;
                    }
                    String unindented = continuation.length() <= indentation
                            ? ""
                            : continuation.substring(indentation);
                    if (block.length() > 0) {
                        block.append(folded ? ' ' : '\n');
                    }
                    block.append(unindented);
                    next++;
                }
                values.put(key, block.toString().stripTrailing());
                i = next - 1;
            } else {
                // YAML permits a quoted scalar to span multiple physical
                // lines.  Join those lines before unquoting; this also keeps
                // colons in the continuation text from being mistaken for a
                // new field.
                StringBuilder scalar = new StringBuilder(rawValue);
                while (isQuotedScalarIncomplete(scalar.toString()) && i + 1 < end) {
                    scalar.append('\n').append(lines[++i].trim());
                }
                values.put(key, unquoteYamlScalar(stripInlineComment(scalar.toString())));
            }
        }
        return values;
    }

    private static int leadingWhitespace(String value) {
        int count = 0;
        while (count < value.length() && Character.isWhitespace(value.charAt(count))) {
            count++;
        }
        return count;
    }

    private static boolean isQuotedScalarIncomplete(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char quote = value.charAt(0);
        if (quote != '\'' && quote != '"') {
            return false;
        }
        if (quote == '\'') {
            for (int i = 1; i < value.length(); i++) {
                if (value.charAt(i) != '\'') {
                    continue;
                }
                if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
        boolean escaped = false;
        for (int i = 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quote == '"' && current == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (current == quote && !escaped) {
                return false;
            }
            escaped = false;
        }
        return true;
    }

    private static int findKeyColon(String line) {
        boolean single = false;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !quoted) {
                single = !single;
            } else if (c == '"' && !single && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (c == ':' && !single && !quoted) {
                return i;
            }
        }
        return -1;
    }

    private static String stripInlineComment(String value) {
        boolean single = false;
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !quoted) {
                single = !single;
            } else if (c == '"' && !single && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (c == '#' && !single && !quoted
                    && (i == 0 || Character.isWhitespace(value.charAt(i - 1)))) {
                return value.substring(0, i).trim();
            }
        }
        return value.trim();
    }

    private static String unquoteYamlScalar(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value.trim();
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (first == '\'' && last == '\'') {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (first != '"' || last != '"') {
            return value.trim();
        }
        String inner = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder(inner.length());
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    result.append(c);
                }
                continue;
            }
            switch (c) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                default -> result.append(c);
            }
            escaped = false;
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }
}
