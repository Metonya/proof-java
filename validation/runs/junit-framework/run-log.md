# junit-framework - corpus phase run log

Repo: https://github.com/junit-team/junit-framework.git @ 9cd9a3cfb6cd98aec355bd49fc8d801058762441
Module: junit-vintage-engine at junit-vintage-engine
Build tool: Gradle (junit-vintage-engine)
JaCoCo: repo-pinned version (see gradle/libs.versions.toml), not overridden by this harness
Surefire excludes: n/a (Maven-only)
pom.xml patches: n/a (Maven-only)
Gradle init script (required on a fresh (non -SkipBuild) build - NOT (re)applied this run, this run reused the clone's existing state as-is):
// allprojects{} reaches across project boundaries, which junit-framework
// rejects (org.gradle.isolated-projects=true in its gradle.properties) with
// "cannot access 'Project.plugins' functionality on subprojects via
// 'allprojects'" - both in the outer build and in its gradle/plugins
// included build (see D-36). gradle.beforeProject configures each project
// from within its own configuration phase instead, which Isolated Projects
// allows.
gradle.beforeProject {
    plugins.withId("jacoco") {
        tasks.withType<JacocoReport>().configureEach {
            reports { xml.required.set(true) }
        }
    }
}

Commands (run from the junit-framework clone root):
  gradlew.bat --no-daemon --init-script <temp-file-above> :junit-vintage-engine:test :junit-vintage-engine:jacocoTestReport
Language level: 17

  java -jar coverdict.jar analyze --repo . --base 9cd9a3cfb6cd98aec355bd49fc8d801058762441~50 --module junit-vintage-engine=junit-vintage-engine --source-roots junit-vintage-engine=junit-vintage-engine/src/main/java --test-roots junit-vintage-engine=junit-vintage-engine/src/test/java --report junit-vintage-engine=junit-vintage-engine/build/reports/jacoco/test/jacocoTestReport.xml --language-level 17 --out verdict-base.json
  java -jar coverdict.jar analyze --repo . --no-vcs (same module/source/test/report args) --out verdict-no-vcs.json
