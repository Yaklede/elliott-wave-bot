# Elliott Wave Bot (Bybit V5) — MVP Skeleton

This repository provides a minimal Spring Boot 4 + Kotlin MVP for an Elliott Wave–inspired strategy using Bybit V5 market data. The system supports BACKTEST and PAPER by default and keeps LIVE trading gated.

## Setup
- Java 21+
- Gradle wrapper (`./gradlew`)

Optional: provide a CSV for backtests with columns:
`timeOpenMs,open,high,low,close,volume`

## Required Environment Variables
- `BYBIT_API_KEY` — only required for LIVE orders
- `BYBIT_API_SECRET` — only required for LIVE orders
- `BOT_ENABLE_LIVE=YES` — required **in addition to** `bot.mode=LIVE` to allow real orders

Never place API keys/secrets in code or commit history.

## Run Commands
Backtest (CSV):
```bash
./gradlew bootRun --args="--bot.mode=BACKTEST --backtest.csvPath=/path/to/candles.csv"
```

Backtest (Bybit REST download):
```bash
./gradlew bootRun --args="--bot.mode=BACKTEST --backtest.startMs=1700000000000 --backtest.endMs=1700500000000"
```

Paper trading (testnet default):
```bash
./gradlew bootRun --args="--bot.mode=PAPER"
```

Live trading (explicitly gated):
```bash
BOT_ENABLE_LIVE=YES ./gradlew bootRun --args="--bot.mode=LIVE"
```

## Safety Notes
- LIVE trading is disabled by default and requires both `bot.mode=LIVE` and `BOT_ENABLE_LIVE=YES`.
- The strategy is a deterministic approximation for engineering use; it is not investment advice.
- Public market data calls do not require keys; private order calls do.

## Sample Data
- A small CSV sample is included at `data/sample_btcusdt_15m.csv` and is used by default for BACKTEST mode.

## Backtest API
- `POST /api/backtest/run` runs a backtest and returns a summary + trades.
  - Optional body: `{ "csvPath": "...", "startMs": 1700000000000, "endMs": 1700500000000 }`
- `GET /api/backtest/last` returns the most recent backtest report.

## Project Layout (Key Modules)
- `marketdata/` — candle retrieval + in-memory cache
- `exchange/bybit/` — REST + WS skeleton + signing + private stream
- `strategy/elliott/` — ZigZag, impulse rules, scoring
- `risk/` — sizing and guardrails
- `execution/` — mode loops and routing
- `portfolio/` — positions and PnL
- `backtest/` — CSV backtest runner
- `api/` — `/api/health`, `/api/status`

## Configuration
Defaults live in `src/main/resources/application.yml` and target testnet + BACKTEST mode.
Portfolio state (PAPER/LIVE) is stored at `data/portfolio-state.json` by default.
Risk state (kill switch + cooldown) is stored at `data/risk-state.json` by default.

## Docker
Build:
```bash
docker build -t elliott-wave-bot:latest .
```

Run (paper mode):
```bash
docker run --rm -p 8080:8080 elliott-wave-bot:latest --bot.mode=PAPER
```
