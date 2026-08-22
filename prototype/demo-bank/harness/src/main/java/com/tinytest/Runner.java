package com.tinytest;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal test runner that records ONE JaCoCo exec file per test method.
 *
 * The mechanism is exactly what a real JUnit 5 TestExecutionListener would do:
 *   before test  -> agent.reset()
 *   after  test  -> agent.getExecutionData(true) and write it to <testId>.exec
 *
 * Used here only because the sandbox cannot reach Maven Central for junit jars.
 */
public class Runner {

    public static void main(String[] args) throws Exception {
        Path outDir = Paths.get(args[0]);
        Files.createDirectories(outDir);
        IAgent agent = RT.getAgent();

        int failed = 0;
        StringBuilder results = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            // accept either "com.foo.BarTest" or "com.foo.BarTest#someMethod"
            String arg = args[i];
            String only = null;
            int hash = arg.indexOf('#');
            if (hash > 0) {
                only = arg.substring(hash + 1);
                arg = arg.substring(0, hash);
            }
            Class<?> testClass = Class.forName(arg);
            for (Method m : testClass.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) {
                    continue;
                }
                if (only != null && !only.equals(m.getName())) {
                    continue;
                }
                String testId = testClass.getName() + "#" + m.getName();

                // reset BEFORE constructing the instance: field initialisers such as
                // `private final Foo foo = new Foo();` are part of the test's coverage.
                agent.reset();
                Object instance = testClass.getDeclaredConstructor().newInstance();
                String status = "PASS";
                try {
                    m.invoke(instance);
                } catch (Throwable t) {
                    status = "FAIL";
                    failed++;
                    System.out.println("  FAIL " + testId + " -> " + rootCause(t));
                }
                byte[] data = agent.getExecutionData(true);
                String file = testId.replace('#', '_').replace('.', '_') + ".exec";
                try (FileOutputStream fos = new FileOutputStream(outDir.resolve(file).toFile())) {
                    fos.write(data);
                }
                results.append(testId).append('\t').append(status).append('\t').append(file).append('\n');
                System.out.println("  " + status + " " + testId);
            }
        }
        Files.write(outDir.resolve("tests.tsv"), results.toString().getBytes());
        System.out.println("failed=" + failed);
    }

    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
