package dev.coverdict.doctor;

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

import dev.coverdict.analysis.model.RepoPaths;

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
            String artifactId = null;
            String packaging = null;
            List<String> childModules = new ArrayList<>();
            int depth = 0;
            // depth 1 is <project> itself (the first START_ELEMENT this reader
            // ever sees) - its direct children (artifactId, packaging,
            // modules) are depth 2, and <module> entries inside <modules>
            // are depth 3.
            String currentPath = null; // "modules" while inside that element
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String name = r.getLocalName();
                    if (depth == 2 && "artifactId".equals(name) && artifactId == null) {
                        artifactId = readCharacters(r);
                        depth--; // readCharacters(r) already consumed this element's own END_ELEMENT
                    } else if (depth == 2 && "packaging".equals(name)) {
                        packaging = readCharacters(r);
                        depth--;
                    } else if (depth == 3 && "module".equals(name) && "modules".equals(currentPath)) {
                        childModules.add(readCharacters(r));
                        depth--;
                    } else if (depth == 2 && "modules".equals(name)) {
                        currentPath = "modules";
                    }
                    if (depth == 2 && ("parent".equals(name) || "dependencies".equals(name) || "build".equals(name)
                            || "properties".equals(name) || "profiles".equals(name))) {
                        skipSubtree(r); // never let a nested artifactId/packaging/module inside these count
                        depth--;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth == 2) {
                        currentPath = null;
                    }
                }
            }
            return new ParsedPom(artifactId, packaging, childModules);
        } finally {
            try {
                r.close();
            } catch (XMLStreamException ignored) {
                // best-effort close
            }
        }
    }

    /** Consumes {@code r}'s current start element and every descendant, leaving the reader positioned on the matching end element - same shape as {@code JacocoXmlParser}'s subtree skip. */
    private static void skipSubtree(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

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
