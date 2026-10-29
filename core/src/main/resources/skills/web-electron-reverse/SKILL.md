---
name: web-electron-reverse
description: Static analysis of JavaScript, Electron apps and websites, and saved network captures
---
- Electron or Node app: analyze the extracted folder or ASAR statically first: modules, imports, preload scripts, IPC channels, source maps.
- Trace a renderer feature through preload and IPC to the main process; note which side owns the privileged call.
- Websites: use the browser tools for page structure and requested screenshots; prefer saved HAR or SAZ captures over live probing.
- Treat any credentials or tokens that appear in captures as secrets: do not repeat them in answers.
- Only exercise third-party services you are permitted to test.
