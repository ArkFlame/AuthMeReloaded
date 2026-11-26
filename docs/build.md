# Building and Development Commands

This page documents the main build, test, and tooling commands used in this repository. For the
current module layout and the single-jar remaster direction, see [ARCHITECTURE.md](ARCHITECTURE.md)
and [MODULES.md](MODULES.md).

The reactor comprises **nine modules**: five built by default and four added by the
`java21-modules` profile (listed under Requirements below). The three Spigot modules are not yet
merged — the merge is blocked by their shared `SpigotPlatformAdapter` service class name and by
conflicting Java 8 / 17 / 21 bytecode levels — so all three jars continue to be built. See
[MODULES.md](MODULES.md) for the staged plan.

## Requirements

- JDK 17+ for the default reactor modules: `authme-api`, `authme-core`, `authme-tools`,
  `authme-spigot-1.8` and `authme-spigot-legacy`
- JDK 21+ for the `java21-modules` profile, which adds `authme-bungee`, `authme-spigot-1.21`,
  `authme-paper` (the merged Paper/Folia jar) and `authme-velocity`
- JDK 25+ to compile `authme-velocity` (Java 25 bytecode), so the full reactor requires JDK 25+
- Maven 3.8.8+

## Build commands

From the repository root:

```bash
# Build everything available for the current JDK
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build while skipping long hash tests (-P and -D activate the same profile)
mvn clean package -P skipLongHashTests
mvn clean package -DskipLongHashTests

# Build one deliverable with its dependencies
mvn clean package -pl authme-bungee -am
mvn clean package -pl authme-velocity -am
mvn clean package -pl authme-paper -am
mvn clean package -pl authme-spigot-1.21 -am

# Build without generating Javadoc
mvn clean package -P skipJavadocGeneration
```

On JDK 21+ builds, the full reactor also generates aggregated API docs for all built modules under
`target/site/apidocs` at the repository root. The aggregate Javadoc step uses the same JDK that
launches Maven, so it stays aligned with the active Java 21+ module set.

## Test commands

```bash
# Run tests in authme-core
mvn test -pl authme-core

# Run tests in authme-bungee
mvn test -pl authme-bungee -am

# Run tests in authme-velocity
mvn test -pl authme-velocity -am

# Run tests in the merged Paper/Folia module
mvn test -am -pl authme-paper

# Run tests in 1.21 version modules
mvn test -am -pl authme-spigot-1.21,authme-paper

# Run the full test suite
mvn test -P skipLongHashTests

# Run one authme-core test class or method
mvn test -Dtest=ClassName -pl authme-core
mvn test -Dtest=ClassName#methodName -pl authme-core

# Generate coverage for authme-core
mvn clean verify -pl authme-core -am
```

## Running development tools

The tooling lives in `authme-tools`, but you can run it directly from the repository root through the Maven reactor.

```bash
# Show the interactive task runner
mvn -q -pl authme-tools -am -P run-tools process-test-classes

# Regenerate repository docs
mvn -q -pl authme-tools -am -P run-tools process-test-classes "-Dexec.args=updateDocs"

# Regenerate generated command/plugin manifests
mvn -q -pl authme-tools -am -P run-tools process-test-classes "-Dexec.args=generateCommandsYml generatePluginYml"
```

## Generated files

- `docs/commands.md`
- `docs/config.md`
- `docs/hash_algorithms.md`
- `docs/permission_nodes.md`
- `docs/translations.md`
- `docs/proxies/bungee/config.yml`
- `docs/proxies/velocity/config.yml`
- `authme-core/src/main/resources/commands.yml`
- `authme-core/src/main/resources/plugin.yml`
- `authme-core/src/test/resources/plugin.yml`
- version-module `plugin.yml` files

If you change a generator or template, commit both the source change and the regenerated output.
