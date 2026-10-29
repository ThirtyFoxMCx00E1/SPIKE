---
name: kotlin-compose
description: Kotlin, coroutines and Jetpack / Compose Multiplatform UI
---
Hoist state; UI reads `StateFlow` with `collectAsState()`. Keep blocking work off the main thread (`Dispatchers.IO`). Do not hold UI state in objects that outlive the process lifecycle. Use `LazyColumn` with stable keys for lists. Android edge-to-edge: apply `systemBarsPadding()` and `imePadding()`. Compose Desktop shares the same Material3 API as Android.
