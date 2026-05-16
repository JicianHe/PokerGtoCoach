# Build & Run

## Prerequisites

| Tool | Required Version |
|---|---|
| Android Studio | Koala Feature Drop (2024.1.2) or newer |
| Android SDK Platform | **34** (Android 14) |
| JDK | 17 (bundled with Studio) |
| Device | Samsung Galaxy Tab S11 (or any 10–13" Android 8.0+ tablet) |

## First Run

1. Open Android Studio → **File ▸ Open** → select `G:\PokerGtoCoach`.
2. Studio will prompt to install the Gradle wrapper jar on first sync.
   If you see *"gradle-wrapper.jar missing"*, run:
   ```
   gradle wrapper --gradle-version 8.9
   ```
   from a terminal where any Gradle is installed, then re-sync.
   (Or accept Studio's prompt to download it.)
3. Wait for Gradle sync to finish (~3–5 min first time).
4. Connect Tab S11 via USB (enable Developer Options → USB Debugging).
5. Click **Run ▶** (or `Shift+F10`).

## Running unit tests

```
./gradlew test                # all unit tests
./gradlew :app:testDebugUnitTest
```

## What lives where

```
app/src/main/java/com/pokercoach/
├── MainActivity.kt           # entry point, locks landscape, requests 120Hz
├── core/
│   ├── model/                # Card, Hand, Position, Action
│   ├── range/                # 13x13 matrix, scenario keys, range manager
│   ├── ev/                   # EvCalculator
│   ├── eval/                 # 5-7 card HandEvaluator
│   ├── ai/                   # PsychProfile, PokerAi
│   └── game/                 # Deck, models, HandStateMachine
├── ui/
│   ├── theme/                # Colors, Typography, PokerCoachTheme
│   ├── common/               # CardView, ChipStack
│   ├── table/                # PlayerSeat, PokerTableLayout, ActionBar
│   ├── hud/                  # StrategyHudPanel
│   └── screen/               # GameScreen (3-column landscape)
└── viewmodel/
    └── GameViewModel.kt
```

## Troubleshooting

- **"Plugin [id 'org.jetbrains.kotlin.plugin.compose'] was not found"**:
  Kotlin 2.0.20+ is required. Update Studio if older.
- **120Hz not engaging**: Tab S11 must have "Motion smoothness = Adaptive"
  in system Display settings.
- **Hero seat shows wrong cards**: Hero is hard-coded to `seatIndex = 0`
  and visually pinned to the bottom of the table.
