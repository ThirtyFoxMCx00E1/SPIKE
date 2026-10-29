---
name: reverse-engineering
description: Evidence-first workflow for understanding how a binary, app or feature works (uses REA)
---
1. Scope: confirm the target is the user's or authorized for analysis, and what question to answer ("how does X work", "what does this call do").
2. Identify: file type, architecture, size, signing, build id (`rea_call` with the inspection tools you find via `rea_tools`).
3. Triage cheaply: strings, imports, sections, embedded metadata. Form 2-3 hypotheses before opening a decompiler.
4. Trace: start from a string, import or symbol tied to the feature; follow xrefs and calls to the function that matters.
5. Record evidence as you go: tool, address or offset, and the exact fact. Mark findings observed, inferred or unknown.
6. Verify: re-check the key claim a second way (assembly vs pseudocode, static vs captured behavior).
7. Deliver: a short behavior spec, the evidence list, open unknowns. If asked to rebuild the feature, implement original code from the spec and test it against observed behavior; do not copy decompiled code.
Stop and ask if a step needs to run the target.
