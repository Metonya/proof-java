package dev.proofjava.doctor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import dev.proofjava.analysis.model.RepoPaths;

/**
 * Walks a Maven reactor from its root {@code pom.xml}, following {@code
 * <modules>} recursively, to discover the same module id/root pairs a user
 * would otherwise have to work out by hand and pass as repeated
 * {@code --module id=root} flags. This is the concrete fix for the WTA
 * dogfood's recurring "which modules, which paths" friction (docs/ROADMAP.md,
 * "L2/L3 classpath UX gap").
 *
 * <p>A {@code packaging=pom} module (an aggregator - WTA's own BOM module
 * is exactly this shape, and triggered the {@code jacoco:report} prefix
 * failure in the first dogfood round) is never itself reported as an
 * analyzable module - it has no {@code src/main/java} of its own - but its
 * {@code <modules>} are still followed, since a real module can nest under
 * an aggregator.
 *
 * <p>Read-only and best-effort: an unparsable or missing {@code pom.xml}
 * along a path simply stops that branch rather than failing the whole scan -
 * {@code doctor} is diagnosis, and a malformed one leaf pom should not hide
 * every other module's real findings.
 */
public final class MavenProjectScanner {

    private static final String MODULES_ELEMENT = "modules";

    private MavenProjectScanner() {
    }

    /** @return every real (non-aggregator) module found, in the order the reactor tree declares them. */
    public static List<MavenModule> scan(Path repoRoot) {
        List<MavenModule> modules = new ArrayList<>();
        scanOne(repoRoot, "", modules);
        return modules;
    }

    private static void scanOne(Path repoRoot, String relativeRoot, List<MavenModule> modules) {
        Path pomFile = repoRoot.resolve(relativeRoot.isEmpty() ? "pom.xml" : relativeRoot + "/pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            return;
        }
        ParsedPom parsed;
        try (InputStream in = Files.newInputStream(pomFile)) {
            parsed = readPom(in);
        } catch (IOException | XMLStreamException e) {
            return; // best-effort: an unreadable/malformed pom just stops this branch
        }

        boolean isAggregator = "pom".equals(parsed.packaging);
        if (!isAggregator && parsed.artifactId != null) {
            modules.add(new MavenModule(parsed.artifactId, relativeRoot.isEmpty() ? "." : relativeRoot));
        }
        for (String childModule : parsed.modules) {
            String childRelative = relativeRoot.isEmpty() ? childModule : relativeRoot + "/" + childModule;
            String normalized = RepoPaths.normalizeSeparators(childRelative);
            if (RepoPaths.isEscapingRepoRoot(normalized)) {
                continue; // a pom naming a module outside the repo is not something this scan follows
            }
            scanOne(repoRoot, normalized, modules);
        }
    }

    /** {@code artifactId}/{@code packaging} read only at depth 1 (direct child of {@code <project>}), so the same-named element inside {@code <parent>} or {@code <dependencies>} is never mistaken for the module's own. */
    private static ParsedPom readPom(InputStream in) throws XMLStreamException {
        XMLStreamReader r = secureFactory().createXMLStreamReader(in);
        try {
            PomState state = new PomState();
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    state.handleStartElement(r);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    state.handleEndElement();
                }
            }
            return new ParsedPom(state.artifactId, state.packaging, state.childModules);
        } finally {
            try {
                r.close();
            } catch (XMLStreamException ignored) {
                // best-effort close
            }
        }
    }

    /**
     * Mutable read-loop state for {@link #readPom} (SonarQube java:S3776 -
     * the flat {@code if}/{@code else if} chain over {@code depth}+name
     * combinations was what drove that method's own complexity over budget;
     * moving it here doesn't remove a single branch, it just gives the loop
     * a name instead of five bare local variables threaded through one method).
     *
     * <p>depth 1 is {@code <project>} itself (the first {@code START_ELEMENT}
     * the reader ever sees) - its direct children ({@code artifactId},
     * {@code packaging}, {@code modules}) are depth 2, and {@code <module>}
     * entries inside {@code <modules>} are depth 3.
     */
    private static final class PomState {
        private String artifactId;
        private String packaging;
        private final List<String> childModules = new ArrayList<>();
        private int depth;
        private String currentPath; // "modules" while inside that element

        void handleStartElement(XMLStreamReader r) throws XMLStreamException {
            depth++;
            String name = r.getLocalName();
            if (depth == 2 && "artifactId".equals(name) && artifactId == null) {
                artifactId = readCharacters(r);
                depth--; // readCharacters(r) already consumed this element's own END_ELEMENT
            } else if (depth == 2 && "packaging".equals(name)) {
                packaging = readCharacters(r);
                depth--;
            } else if (depth == 3 && "module".equals(name) && MODULES_ELEMENT.equals(currentPath)) {
                childModules.add(readCharacters(r));
                depth--;
            } else if (depth == 2 && MODULES_ELEMENT.equals(name)) {
                currentPath = MODULES_ELEMENT;
            }
            if (depth == 2 && isSkippedElement(name)) {
                skipSubtree(r); // never let a nested artifactId/packaging/module inside these count
                depth--;
            }
        }

        void handleEndElement() {
            depth--;
            if (depth == 2) {
                currentPath = null;
            }
        }

        private static boolean isSkippedElement(String name) {
            return "parent".equals(name) || "dependencies".equals(name) || "build".equals(name)
                || "properties".equals(name) || "profiles".equals(name);
        }

        /**
         * Consumes {@code r}'s current start element and every descendant,
         * leaving the reader positioned on the matching end element - same
         * shape as {@code JacocoXmlParser}'s subtree skip. Moved here
         * (SonarQube java:S3398) - {@link #handleStartElement} is its only
         * caller. Its own {@code subtreeDepth} is unrelated to this class's
         * {@code depth} field (named apart to avoid any shadowing confusion).
         */
        private static void skipSubtree(XMLStreamReader r) throws XMLStreamException {
            int subtreeDepth = 1;
            while (subtreeDepth > 0 && r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    subtreeDepth++;
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    subtreeDepth--;
                }
            }
        }

        /** Moved here (SonarQube java:S3398) - {@link #handleStartElement} is its only caller. */
        private static String readCharacters(XMLStreamReader r) throws XMLStreamException {
            StringBuilder sb = new StringBuilder();
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    sb.append(r.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    break;
                }
            }
            String text = sb.toString().trim();
            return text.isEmpty() ? null : text;
        }
    }

    /** Same OWASP StAX XXE posture as {@code JacocoXmlParser}'s {@code secureFactory()} - a pom.xml is local, but there is no reason to parse it any less defensively. */
    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private record ParsedPom(String artifactId, String packaging, List<String> modules) {
    }
}
