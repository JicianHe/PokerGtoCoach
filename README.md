# PokerGtoCoach

A single-player Texas Hold'em **strategy teaching** game for Android tablets, optimized for the **Samsung Galaxy Tab S11** (11", 2560x1600, 120Hz).

Education-first, gameplay-second: the player learns GTO (Game Theory Optimal) preflop / postflop strategy and exploitive adjustments through guided play against a single-machine AI, with a real-time **Strategy Coach HUD** on the right side of the table.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (tablet-optimized, landscape)
- **Architecture:** MVVM + State Machine for hand flow
- **Target:** Android 14+ (API 34), 11" tablet landscape
- **Refresh Rate:** 120Hz optimized animations

## Development Phases

| Phase | Scope |
|-------|-------|
| 1 | Core data models + GTO preflop range engine (13x13 matrix, EV calc) |
| 2 | Hand state machine + single-player AI (GTO mixed strategy + exploitive weights) |
| 3 | Tablet UI architecture (6-Max table, left/center layout, 120Hz touch) |
| 4 | Right-side Strategy Coach HUD + pre/post-action GTO review pause |
| 5 | Integration, edge-case tests, mock data fill |

## Module Layout

```
app/src/main/java/com/pokercoach/
  core/
    model/    # Card, Hand, Position, Action, ...
    range/    # PreflopRangeManager, HandMatrix
    ev/       # EV / frequency calculation
```

## Status

- [x] Phase 1: Models + Preflop Range Engine
- [ ] Phase 2
- [ ] Phase 3
- [ ] Phase 4
- [ ] Phase 5
