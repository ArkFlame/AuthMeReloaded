# Building and Development Commands

This page documents the main build, test, and tooling commands used in this repository. For
background on the module layout and the single-jar remaster direction, see
[ARCHITECTURE.md](ARCHITECTURE.md) and [MODULES.md](MODULES.md).

The reactor comprises **eight modules**: four built by default and four added by the
`java21-modules` profile (listed under Requirements below), producing **five plugin jars** —
Spigot-1.8, Spigot, Paper, Bungee and Velocity — plus the API jar. The former `authme-spigot-legacy`
and `authme-spigot-1.21` modules are now one `authme-spigot` module; its two-pass compile is covered
below.

## Requirements

- JDK 17+ for the default reactor modules: `authme-api`, `authme-core`, `authme-tools` and
  `authme-spigot-1.8` (Spigot 1.8.8, shaded libraries, Java 8 bytecode)
- JDK 21+ for the `java21-modules` profile, which adds `authme-bungee`, `authme-spigot`,
  `authme-paper` (the merged Paper/Folia jar) and `authme-velocity`. The merged `authme-spigot`
  module needs JDK 21 at build time to compile its dialog adapter; on a Java 17 server that adapter
  is skipped at runtime and the legacy adapter is used instead.
- JDK 25+ to compile `authme-velocity` (Java 25 bytecode), so the full reactor requires JDK 25+
- Maven 3.8.8+

## The merged Spigot jar

`authme-spigot` produces a single artifact, `AuthMeRemastered-<version>-Spigot.jar`, that supports
Spigot 1.16 through 1.21.x. It is compiled in two passes into the same jar:

1. Java 17 bytecode for `SpigotLegacyPlatformAdapter`, the baseline loadable on older servers.
2. Java 21 bytecode for `Spigot121PlatformAdapter`, `SpigotDialogHelper`, `DialogApiAvailability`
   and `PlayerSignOpenListener`, which provide dialog and sign support.

`AuthMe.loadPlatformAdapter()` selects the first compatible service entry, so one jar covers the
whole supported range; see [MODULES.md](MODULES.md) for the service order and the `api-version: 1.16`
detail (a plugin cannot declare a newer `api-version` than the server).

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

# Build the merged Spigot jar (Java 17 baseline + Java 21 dialog adapter)
mvn clean package -pl authme-spigot -am

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

# Run tests in the merged Spigot module and Paper
mvn test -am -pl authme-spigot,authme-paper

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
- module `plugin.yml` files

If you change a generator or template, commit both the source change and the regenerated output.
