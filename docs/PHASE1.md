# Phase 1 — Core Models & Preflop GTO Range Engine

## Delivered Files

```
app/src/main/java/com/pokercoach/core/
├── model/
│   ├── Card.kt           # Suit, Rank, Card
│   ├── Hand.kt           # HandClass (169), HoleCards
│   ├── Position.kt       # UTG, HJ, CO, BTN, SB, BB
│   └── Action.kt         # Sealed: Fold/Check/Call/Raise(amount)
├── range/
│   ├── HandMatrix.kt     # 13x13 mixed-strategy matrix
│   ├── RangeScenario.kt  # Rfi / BbVsRfi scenario keys
│   └── PreflopRangeManager.kt   # BTN RFI + BB vs BTN RFI (built-in)
└── ev/
    └── EvCalculator.kt   # EV per action, recommendation, range share
```

## Built-in Scenarios

| Scenario | Approx. Frequency | Notes |
|---|---|---|
| `Rfi(BTN)` | ~49% RFI | 100bb 6-Max cash baseline |
| `BbVsRfi(BTN, 2.5bb)` | ~68% defend | call-dominant w/ polarized 3-bets (A5s, 54s, JJ-AA) |

## Invariants Enforced

- `MixedStrategy.{fold+check+call+raise} == 1.0` (±1e-6)
- `HandClass.highRank.value >= lowRank.value`; pair ⇒ `!suited`
- Matrix layout: pair on diagonal, suited upper-right, offsuit lower-left
