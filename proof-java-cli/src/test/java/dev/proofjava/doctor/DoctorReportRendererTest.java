package dev.proofjava.doctor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DoctorReportRendererTest {

    private static final MavenModule APP = new MavenModule("app", "app");
    private static final MavenModule DATA = new MavenModule("data", "data");

    @Test
    void aModuleWithABlockerIsExcludedFromTheSuggestedCommand() {
        ModuleDiagnosis blocked = new ModuleDiagnosis(APP,
            List.of(DoctorCheck.blocker("JACOCO_REPORT_STALE", "stale")), "app/target/site/jacoco/jacoco.xml", null, null);

        String rendered = DoctorReportRenderer.render(List.of(blocked));

        assertTrue(rendered.contains("BLOCKER"));
        assertTrue(rendered.contains("No module has a usable JaCoCo report yet"));
        assertFalse(rendered.contains("--module app=app"));
    }

    @Test
    void usableModulesProduceACopyPasteableAnalyzeCommand() {
        ModuleDiagnosis app = new ModuleDiagnosis(APP, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "app/target/site/jacoco/jacoco.xml", null, null);
        ModuleDiagnosis data = new ModuleDiagnosis(DATA, List.of(DoctorCheck.ok("JACOCO_REPORT_PRESENT", "found")),
            "data/target/site/jacoco/jacoco.xml", "data/target/proof-per-test-classpath.txt",
            "data/target/proof-mutation-classpath.txt");

        String rendered = DoctorReportRenderer.render(List.of(app, data));

        assertTrue(rendered.contains("--module app=app"));
        assertTrue(rendered.contains("--module data=data"));
        assertTrue(rendered.contains("--report app=app/target/site/jacoco/jacoco.xml"));
        assertTrue(rendered.contains("--report data=data/target/site/jacoco/jacoco.xml"));
        assertTrue(rendered.contains("L2/L3 evidence is also available for: data"));
        assertFalse(rendered.contains("L2/L3 evidence is also available for: data, app"), "app has no classpath, must not be listed");
    }

    @Test
    void noModulesAtAllStillRendersWithoutThrowing() {
        String rendered = DoctorReportRenderer.render(List.of());

        assertTrue(rendered.contains("0 module(s) found"));
    }
}
