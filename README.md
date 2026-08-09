<div align="center">

# viGeyserUpdater

**A crash-safe update manager for Geyser companion plugins and extensions.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper%20%7C%20Spigot%20%7C%20Folia-supported-4A90E2?style=flat-square)
![Velocity](https://img.shields.io/badge/Velocity-3.4-5C6BC0?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-2EA44F?style=flat-square)

Keep GeyserModelEngine, GeyserUtils, and Boar current on Paper-family servers and Velocity proxies—automatically or on demand.

</div>

---

## Why viGeyserUpdater?

Updating a live Minecraft stack is more than replacing a JAR. Downloads can stop halfway, release hosts can become unavailable, storage can reject a write, or the process can terminate between moving the old and new files.

viGeyserUpdater treats each installation as a recoverable transaction:

- automatic and manual update modes;
- independent enable/disable controls for every managed artifact;
- GitHub Releases and Modrinth source providers;
- HTTPS host allowlisting, redirect validation, timeouts, and download limits;
- retry with exponential backoff for connection failures, HTTP `429`, and HTTP `5xx`;
- upstream SHA-256 verification when provided, plus JAR structure validation;
- atomic state and transaction journals;
- startup recovery after an interrupted installation;
- one dedicated worker thread—no network or file I/O on the server thread;
- platform adapters for Paper, Spigot, Folia, and Velocity.

> [!IMPORTANT]
> Installed updates take effect after a **full server or proxy restart**. Plugin reloaders are not supported.

## Managed artifacts

The bundled configuration manages these artifacts by default. Every entry can be disabled independently with `enabled: false`.

| Artifact ID | Bukkit family | Velocity | Upstream source |
|---|:---:|:---:|---|
| `geyser-model-engine` | Yes | — | GitHub Release `latest` |
| `geyser-model-engine-extension` | Yes | Yes | GitHub Release `latest` |
| `geyser-utils-bukkit` | Yes | — | GitHub Release `latest` |
| `geyser-utils-velocity` | — | Yes | GitHub Release `latest` |
| `geyser-utils-extension` | Yes | Yes | GitHub Release `latest` |
| `boar` | Yes | Yes | Modrinth, `geyser` artifact |

Upstream projects:

- [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)
- [GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils)
- [Boar](https://github.com/opencollab-incubator/Boar)

## Technology stack

| Layer | Technology | Purpose |
|---|---|---|
| Language/runtime | Java 21 | Records, modern concurrency, NIO, and the built-in HTTP client |
| Build | Gradle 8.14.4 with Kotlin DSL | Reproducible multi-module builds |
| Packaging | Shadow 8.3.8 | Produces isolated, deployable platform JARs |
| Bukkit adapter | Paper API 1.21.4 | Paper, Spigot, and Folia lifecycle and commands |
| Proxy adapter | Velocity API 3.4 | Velocity lifecycle, scheduler, permissions, and commands |
| HTTP | Java `HttpClient` | HTTPS downloads, explicit redirects, and bounded requests |
| Configuration | SnakeYAML 2.4 | Safe YAML parsing with duplicate-key protection |
| State | Gson 2.13.1 | Small atomic state and transaction journal files |
| Testing | JUnit 5.13.4 | Unit, fault-injection, host-failure, and live integration tests |
| CI | GitHub Actions | Java 21 build, test, and artifact packaging |

Gson and SnakeYAML are relocated inside the final JARs. Platform APIs remain `compileOnly` and are never shaded.

## Requirements

- Java 21
- Paper, Spigot, or Folia in the modern Java 21 server line
- Velocity 3.4 for proxy installations
- A local Geyser installation when managing Geyser extensions

The default extension paths are:

```text
plugins/Geyser-Spigot/extensions
plugins/Geyser-Velocity/extensions
```

Custom Geyser directory names are supported by changing each artifact's `destination`.

## Installation

1. Download or build the JAR for your platform.
2. Place it in the server or proxy `plugins/` directory.
3. Start the server once to generate `plugins/viGeyserUpdater/config.yml`.
4. Review the enabled artifacts and destination paths.
5. Restart after an update has been installed.

Build from source on Windows:

```powershell
.\gradlew.bat clean test build
```

Build outputs:

```text
bukkit/build/libs/viGeyserUpdater-Bukkit-1.0.0.jar
velocity/build/libs/viGeyserUpdater-Velocity-1.0.0.jar
```

## Commands

The required permission is `vigeyserupdater.admin`. Bukkit grants it to operators by default; Velocity delegates it to the configured permission provider.

| Command | Description |
|---|---|
| `/geyserupdates status` | Show the current state of every enabled artifact |
| `/geyserupdates check [artifact\|all]` | Check for updates without installing them |
| `/geyserupdates update [artifact\|all]` | Check and install available updates |
| `/geyserupdates reload` | Reload and validate `config.yml` |

Alias: `/gupdates`

## Configuration

### Automatic mode

The default configuration checks and applies updates every six hours:

```yaml
automatic:
  enabled: true
  initial-delay-seconds: 30
  interval-minutes: 360
  apply: true
```

Check automatically without installing:

```yaml
automatic:
  enabled: true
  apply: false
```

Manual-only mode:

```yaml
automatic:
  enabled: false
```

### Enable or disable individual artifacts

```yaml
artifacts:
  - id: geyser-model-engine-extension
    enabled: true
    # remaining source and destination settings...

  - id: geyser-utils-extension
    enabled: false
    # remaining source and destination settings...

  - id: boar
    enabled: false
    # remaining source and destination settings...
```

Do not remove the remaining fields from an artifact entry. Set only `enabled` to `false`, then run `/gupdates reload`.

### Network resilience

```yaml
network:
  max-download-mib: 128
  connect-timeout-seconds: 10
  request-timeout-seconds: 120
  retry-attempts: 3
  retry-delay-seconds: 2
```

Retries use exponential backoff. If the source remains unavailable, the artifact is marked `failed`, its installed JAR and state remain untouched, and the automatic scheduler tries again during the next cycle.

Set `GITHUB_TOKEN` in the process environment to raise the GitHub API rate limit without storing credentials in YAML.

### Path variables

| Variable | Resolution |
|---|---|
| `${plugins}` | `<server-root>/plugins` |
| `${geyser}` on Bukkit | `plugins/Geyser-Spigot` |
| `${geyser}` on Velocity | `plugins/Geyser-Velocity` |

`existing-regex` discovers an already-installed versioned JAR. If more than one file matches, the artifact fails safely instead of guessing which file to replace.

## Crash-safe installation

```text
Resolve release
      ↓
Validate HTTPS host and redirects
      ↓
Download to a bounded temporary file
      ↓
Verify checksum and JAR structure
      ↓
Persist PREPARED transaction journal
      ↓
current JAR → backup → new JAR
      ↓
Persist installed state and clean up
```

If the process stops during the transaction, the next startup examines the target, pending file, backup, and journal. It restores the known-good JAR or completes a safe first installation based on the durable files that remain.

All network and file operations run on one daemon worker. Folia polling does not use or block an entity scheduler; player-facing command responses are dispatched through the appropriate platform scheduler.

### Host failure behavior

| Failure | Behavior |
|---|---|
| DNS, connection, or timeout failure | Retry with exponential backoff |
| HTTP `429` or `5xx` | Retry with exponential backoff |
| HTTP `4xx` | Fail without retrying |
| Redirect to a non-allowlisted host | Reject immediately |
| Retries exhausted | Mark artifact failed and preserve the installed JAR/state |

## Architecture

```text
viGeyserUpdater
├── common
│   ├── config       Safe YAML loading and immutable records
│   ├── source       GitHub and Modrinth provider strategies
│   ├── net          Bounded HTTPS client and retry policy
│   ├── install      Validation, hashing, journal, and recovery
│   ├── state        Atomic installed-artifact state
│   └── engine       Serialized update orchestration
├── bukkit           Paper / Spigot / Folia adapter
└── velocity         Velocity adapter
```

The common module does not depend on Bukkit or Velocity. Platform modules contain only lifecycle, command, permission, scheduler, and logging adapters. New release providers can implement `ArtifactSource` without changing the transaction installer.

## Testing

Run the deterministic test suite:

```powershell
.\gradlew.bat clean test
```

The suite covers:

- recovery after every transaction checkpoint;
- interrupted first installation;
- corrupt and classless JAR rejection;
- destination matching and path traversal rejection;
- unreachable update hosts and retry exhaustion;
- fail-closed host allowlisting;
- preservation of the installed JAR after a network failure;
- bundled configuration and asset-regex validation.

Run live integration tests against the current upstream services:

```powershell
$env:VI_LIVE_UPDATE_TEST='true'
.\gradlew.bat clean test --no-build-cache
```

Live tests resolve GitHub and Modrinth releases, download and validate a real GeyserUtils artifact, install it into a temporary server root, and verify that the next check reports it as current.

> [!NOTE]
> Fault-injection tests simulate process failure at transaction checkpoints. They are not a physical power-loss test; the strongest durability guarantee still depends on the filesystem, atomic-move implementation, and storage controller.

## Platform notes

- The Bukkit build loads during `STARTUP` and before the known Geyser companion plugins to reduce Windows JAR-lock conflicts.
- Velocity does not provide the same classloader ordering guarantee. If Windows refuses to replace an open JAR, viGeyserUpdater reports the failure and preserves the original file; stop the proxy and replace that artifact manually.
- Full runtime boot/restart validation on every supported server implementation remains separate from the automated unit and integration suite.

## License

viGeyserUpdater is available under the [MIT License](LICENSE).

This project is an original implementation. It uses upstream repositories only to discover their published artifacts and documented installation locations; it does not copy source code from GeyserModelEngine, GeyserUtils, or Boar.
