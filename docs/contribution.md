# Contribution

* Star the project on [Github](https://github.com/raynigon/raylevation) and help spread the word :)
* [Post an issue](https://github.com/raynigon/raylevation/issues) if you find any bugs
* Contribute improvements or fixes using a [Pull Request](https://github.com/raynigon/raylevation/pulls).
  If you're going to contribute, thank you! Please just be sure to:
  * discuss with the authors on an issue ticket prior to doing anything big.
  * follow the style, naming and structure conventions of the rest of the project.
  * make commits atomic and easy to merge.
  * verify all tests are passing. Build the project with `./gradlew check` to do this.
  **N.B.** Gradle's Build Cache is enabled by default, but you can add `--no-build-cache` flag to disable it.

## Requirements

* JDK 21.
* [`unrar`](https://www.rarlab.com/rar_add.htm) installed and on `PATH`. The SRTM archives Raylevation
  downloads are RAR files, and both the `update-srtm` CLI command and the tests that exercise it
  (`TileUnpackService`) shell out to the `unrar` binary. No local Gradle install is needed, the
  project ships the Gradle Wrapper (`./gradlew`).

After cloning the repository, install the project's git hooks once:

```shell
./gradlew installLocalGitHooks
```

## Test suites

The project has three separate test suites, run by different Gradle tasks:

| Task | Source | What it covers | Part of `check`? |
|---|---|---|---|
| `./gradlew test` | `src/test/groovy`, `src/contractTest` | Spock unit/integration specs, plus the generated [Spring Cloud Contract](https://spring.io/projects/spring-cloud-contract) tests for `src/contractTest/resources/contracts/**/*.yaml` (folded into the same task, see `build.gradle.kts`) | Yes |
| `./gradlew smokeTest` | `src/smokeTest/groovy` | Hits real external services (the production SRTM host) to catch upstream breakage early | No, deliberately excluded (network, slow) |
| `./gradlew check` | — | Runs `test` plus linters (ktlint, Checkstyle, PMD, SpotBugs) | — |

To change what the HTTP API contract-verifies, edit the YAML files under
`src/contractTest/resources/contracts/v1/lookup/`, not generated code — the test classes are
generated from these contracts by the Spring Cloud Contract Gradle plugin.

## Previewing the documentation site

This site is built with [MkDocs](https://www.mkdocs.org/). To preview changes locally:

```shell
pip install -r requirements.txt
mkdocs serve
```

## Contributors
Logos are licensed under [Creative Commons 3.0](https://creativecommons.org/licenses/by/3.0/)
and was designed by: [Raisul](https://www.iconfinder.com/Raisul)

The list of all [Contributors](https://github.com/raynigon/raylevation/graphs/contributors) can be found on [Github](https://github.com/raynigon/raylevation/graphs/contributors).
