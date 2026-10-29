---
name: native-binaries
description: Native ELF, PE and Mach-O analysis with Ghidra, Hopper, IDA or Kuna through REA
---
- Use `binary_session` first: it lists which providers (Ghidra, Hopper, IDA, Kuna, objdump, Rizin) are ready and why others are not.
- The first query on a target imports and auto-analyzes it, which can take minutes on large files; raise REA_GHIDRA_STARTUP_TIMEOUT_MS or REA_HOPPER_STARTUP_TIMEOUT_MS if it times out.
- Work outward from strings, imports and exported symbols; use xrefs to reach callers; decompile only the functions you need.
- Rename and comment functions as you understand them so later queries read better.
- ARM: check Thumb vs ARM state and calling convention before trusting pseudocode. Stripped binaries: expect missing names; recover from strings, RTTI and relocation data.
- Pseudocode is a model of the code, not the source. Confirm important details in the assembly.
- Close the binary (`close_binary`) when finished.
