---
name: code-review
description: Review code for bugs, security and maintainability
---
Check in this order: correctness and edge cases, security (input handling, secrets, injection, permissions), error handling, concurrency, performance hot spots, then readability. Report findings as file:line, what is wrong, why it matters, and a concrete fix. Separate must-fix from nice-to-have.
