---
name: debugging
description: Find and fix the root cause of a bug or failing build
---
1. Reproduce first: run the failing command and capture the exact error.
2. Read the first error, not the last; later errors are often fallout.
3. Form one hypothesis, test it with the cheapest check (log, print, minimal repro), then change one thing.
4. Fix the cause, not the symptom. Add a test that fails before the fix and passes after.
5. Re-run the original command to confirm.
