# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/io/github/yaklede/elliott/wave/principle/coin` holds the application code.
- `marketdata/` fetches and stores candles (public market data + in-memory cache).
- `exchange/bybit/` integrates with Bybit V5 (REST + WS + instrument filters); keep it isolated from strategy logic.
- `strategy/elliott/` contains ZigZag + Elliott detection and signal generation.
- `risk/` handles sizing and guardrails (kill switch, cooldowns).
- `execution/` orchestrates mode loops and routes orders.
- `portfolio/` tracks positions, PnL, and trade history.
- `backtest/` runs candle-by-candle simulation from local data.
- `api/` exposes `/api/health` and `/api/status`.
- `data/` holds local backtest fixtures and persisted state (do not store secrets).
- `external/` is reserved for outward integrations; it must not depend on any package above it and should remain usable as a standalone library.
- `src/main/resources/application.yml` holds Spring configuration.
- `src/test/kotlin/...` mirrors main packages for tests.

## Build, Test, and Development Commands
- `./gradlew bootRun` runs the Spring Boot app locally.
- `./gradlew test` runs the JUnit 5 test suite.
- `./gradlew build` compiles and runs tests, producing artifacts in `build/`.
- `./gradlew clean` removes build outputs.

## Coding Style & Naming Conventions
- Kotlin + Spring Boot; follow IntelliJ Kotlin defaults unless the file already uses a different style.
- Indentation uses 4 spaces in new code; keep indentation consistent within each file.
- Packages are all lowercase and mirror directory structure.
- Classes use `PascalCase`; functions/variables use `camelCase`.

## Testing Guidelines
- Tests use JUnit 5; keep them deterministic and offline-friendly.
- Place tests in `src/test/kotlin` under the matching package.
- Name test classes `*Test` and keep test methods descriptive.
- Run tests via `./gradlew test`.

## Commit & Pull Request Guidelines
- Commit history uses `type: summary` format (e.g., `feature: spring project init`); keep types lowercase and summaries short.
- PRs should include a short description, testing performed, and any configuration or schema changes.

## Safety & Guardrails
- LIVE trading is disabled by default and must require both `bot.mode=LIVE` and `BOT_ENABLE_LIVE=YES`.
- Never log or print API keys/secrets; keep examples secret-free.
- Default configuration targets testnet and BACKTEST mode.
- Risk state is persisted to `data/risk-state.json`; keep it out of version control if it contains sensitive operational data.
