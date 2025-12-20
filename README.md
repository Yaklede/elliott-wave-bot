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

## Backtest as Test Code (1 year)
- Place a 1-year CSV at `data/bybit_btcusdt_15m_1y.csv` or set `BACKTEST_DATA_PATH`.
- The CSV should be generated locally (e.g., via Bybit REST) and kept out of git.
- Run:
```bash
BACKTEST_DATA_PATH=/path/to/1y.csv ./gradlew test --tests '*OneYearBacktestTest*'
```
- The test prints a summary (trades, win rate, profit factor, max drawdown, final equity).

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

## Execution Flow (No Scheduler)
- Spring Boot starts `ExecutionEngine` on `ApplicationReadyEvent`.
- `BACKTEST`: runs once using local CSV (or Bybit REST if start/end provided).
- `PAPER` / `LIVE`: starts a coroutine loop that polls candles every ~15s and evaluates signals on candle close.
- WebSocket public kline stream runs in parallel (for confirmed candles), and private WS runs only in LIVE with credentials.

## Production Setup
1) Prepare configuration (env + args):
   - `BYBIT_API_KEY`, `BYBIT_API_SECRET` for LIVE only
   - `BOT_ENABLE_LIVE=YES` and `--bot.mode=LIVE` to enable real orders
2) Mount a persistent volume for `data/` (portfolio + risk state, optional backtest CSV).
3) Run the container with explicit mode and config args.

## Docker
Build:
```bash
docker build -t elliott-wave-bot:latest .
```

Run (paper mode):
```bash
docker run --rm -p 8080:8080 elliott-wave-bot:latest --bot.mode=PAPER
```

Run (live mode, explicitly gated):
```bash
docker run --rm -p 8080:8080 \\
  -e BOT_ENABLE_LIVE=YES \\
  -e BYBIT_API_KEY=*** \\
  -e BYBIT_API_SECRET=*** \\
  -v $(pwd)/data:/app/data \\
  elliott-wave-bot:latest --bot.mode=LIVE
```

## Docker Swarm
1) Build and push your image to a registry:
```bash
docker build -t your-registry/elliott-wave-bot:latest .
docker push your-registry/elliott-wave-bot:latest
```
2) Deploy stack:
```bash
docker stack deploy -c docker-stack.yml elliott
```
3) For LIVE mode, update `docker-stack.yml` to set `--bot.mode=LIVE` and provide env vars.
