gradle.beforeProject {
    if (name == "junit-vintage-engine") {
        afterEvaluate {
            val testCp = extensions.findByType(org.gradle.api.tasks.SourceSetContainer::class.java)
                ?.findByName("test")?.runtimeClasspath
            val outFile = layout.buildDirectory.file("coverdict-test-classpath.txt")
            if (testCp != null) {
                tasks.register("coverdictDumpClasspath") {
                    inputs.files(testCp)
                    outputs.file(outFile)
                    doLast {
                        val out = outFile.get().asFile
                        out.parentFile.mkdirs()
                        out.writeText(testCp.files.joinToString(";") { it.absolutePath })
                    }
                }
            }
        }
    }
}
