package com.raynigon.raylevation.gradle

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Evaluates the real gradle/scripts/external.gradle against a fake, minimal
 * stand-in for the Gradle DSL (project/tasks), so the updateK8sYaml task's
 * doFirst logic can run as a plain unit test, against copies of the real
 * manifests, without ever touching the checked-in files or booting Gradle.
 */
class UpdateK8sYamlTaskSpec extends Specification {

    private static final File EXTERNAL_SCRIPT = new File(".", "gradle/scripts/external.gradle").absoluteFile
    private static final List<String> MANIFESTS = ["install-minimum.yaml", "install-full.yaml"]

    @TempDir
    Path projectDir

    def "updates the image tag and version label in every manifest"() {
        given: "copies of the real manifests inside a throwaway project directory"
        File k8sDir = new File(projectDir.toFile(), "deployment/k8s")
        k8sDir.mkdirs()
        MANIFESTS.each { name ->
            File source = new File(".", "deployment/k8s/${name}").absoluteFile
            new File(k8sDir, name).text = source.text
        }

        def task = registerUpdateK8sYamlTask(projectDir.toFile(), "9.9.9")

        when: "the task's doFirst actions run"
        task.actions.each { it.call() }

        then: "both manifests carry the new image tag and version label"
        MANIFESTS.each { name ->
            String content = new File(k8sDir, name).text
            assert content.findAll(/ghcr\.io\/raynigon\/raylevation:.*/).every { it == "ghcr.io/raynigon/raylevation:v9.9.9" }
            assert content.findAll(/app\.kubernetes\.io\/version: .*/).every { it == "app.kubernetes.io/version: 9.9.9" }
        }
    }

    private static FakeTask registerUpdateK8sYamlTask(File projectDir, String version) {
        def binding = new Binding()
        binding.setVariable("project", new FakeProject(projectDir: projectDir, version: version))
        binding.setVariable("tasks", new FakeTasks())
        new GroovyShell(binding).evaluate(EXTERNAL_SCRIPT)
        return binding.getVariable("tasks").registry["updateK8sYaml"]
    }

    static class FakeProject {
        File projectDir
        String version

        File file(Object path) {
            new File(projectDir, path.toString())
        }
    }

    static class FakeTask {
        String group
        List<Closure> actions = []

        void doFirst(Closure action) {
            actions << action
        }
    }

    static class FakeTasks {
        Map<String, FakeTask> registry = [:]

        FakeTask register(String name, Closure body) {
            def task = new FakeTask()
            body.delegate = task
            body.resolveStrategy = Closure.DELEGATE_FIRST
            body.call()
            registry[name] = task
            return task
        }
    }
}
