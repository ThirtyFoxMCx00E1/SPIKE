# SPIKE

Free coding and knowledge agent for **Android** and **Windows**. The agent profile is **SPK 1.0**.
No accounts, no payments, no usage limits in the app itself: step count, tool use and shell time are all
settings that default to "no limit".

## What SPK 1.0 is

SPK 1.0 is an agent: a system prompt, a set of tools and a set of skills that run on top of **any
OpenAI-compatible model endpoint**. It is not a trained model, so the model's speed, quality and any
limits come from the backend you point it at. For truly free and unlimited use, run a local model:

| Where | Backend |
|-------|---------|
| Windows | [Ollama](https://ollama.com) or LM Studio on the same PC (`http://localhost:11434/v1`) |
| Android | Ollama inside Termux (if your Termux repo has it), or point at your PC / VPS / Oracle free VM |
| Anywhere | OpenRouter, Groq or any provider with a free tier (add the API key in Settings) |

Default settings: base URL `http://localhost:11434/v1`, model `qwen2.5-coder:7b`.

## Tools and skills

Tools: `list_dir`, `read_file`, `write_file`, `edit_file`, `search`, `run_shell`, `http_get`, `load_skill`, plus
`rea_tools` / `rea_describe` / `rea_call` when REA is on.
Files are confined to the workspace folder. `run_shell` runs real commands with the workspace as the
working directory, so turn it off in Settings if you do not want that.

Built-in skills (`core/src/main/resources/skills`): coding, debugging, code-review, android-ndk,
kotlin-compose, git-github-actions, hivemq-mqtt, linux-ops, research, reverse-engineering, native-binaries,
android-reverse, web-electron-reverse. Add your own as
`<data folder>/skills/<name>/SKILL.md` with `name:` and `description:` front matter.
Data folder: `%USERPROFILE%\.spike` on Windows, the app's private storage on Android.

## Reverse-engineering mode (REA)

SPIKE can drive [REA](https://github.com/ThirtyFoxMCx00E1/rea) (Reverse Engineer Anything, an MCP server by
[morluto/rea](https://github.com/morluto/rea)) for native binaries (Ghidra, Hopper, IDA, Kuna), APKs,
JavaScript/Electron apps, .NET assemblies, firmware, network captures and more. Analysis runs locally.

1. Install Node.js 22.23.4+, 24.22.1+ or 26.12.1+. For native analysis, install Ghidra (and a JDK) or another provider
   following REA's [installation guide](https://github.com/ThirtyFoxMCx00E1/rea/blob/main/docs/installation.md).
2. In the Windows app: Settings, turn on **Enable REA tools**. The default command is
   `npx -y rea-agents@latest mcp`; pin a version, or point at your own build with
   `node /path/to/rea/scripts/rea.mjs mcp`. Set the Ghidra folder and `JAVA_HOME` if needed.
3. Ask: "Open `C:\\apps\\app.exe`, find where it validates its config file and explain how, with evidence."

SPK 1.0 sees three tools instead of REA's whole catalog (small local models cannot hold hundreds of schemas):
`rea_tools` (search), `rea_describe` (schema), `rea_call` (run). It loads the `reverse-engineering`,
`native-binaries`, `android-reverse` and `web-electron-reverse` skills for the workflow. You can also copy REA's own
`reverse-engineer-anything` skill into `<data folder>/skills/`.

Scope: REA is for software you own or are authorized to examine (interoperability, preservation, education, permitted
research). SPK 1.0 is instructed to refuse license/DRM bypass and malware, to say what a runtime capture will do before
it runs, and to rebuild features as original code from a behavior spec instead of pasting decompiler output.

**Android:** the phone cannot run Node.js, so REA lives in the Windows app. In the phone's Settings turn on the bus and
**Remote-only**: your prompts go to the desktop agent over HiveMQ and its output appears on the phone.

## HiveMQ bus

Turn on the bus in Settings to publish agent output to `spike/<channel>/events` and accept prompts on
`spike/<channel>/cmd`. That lets the Windows app watch or drive the agent on your phone, and back.
Prompts that arrive over the bus run **read-only** (no shell, no writes, no REA) unless you turn on
**Trust prompts from the bus** and the broker login is set, because anyone who can publish to `cmd` can otherwise run
commands on your PC. The default broker is the public `broker.hivemq.com`, which anyone can read: testing only.
For real use, run your own broker with a login and TLS: see
[ThirtyFoxMCx00E1/HiveMQ](https://github.com/ThirtyFoxMCx00E1/HiveMQ) and the
[wagu](https://thirtyfoxmcx00e1.github.io/wagu/) guide.

## Layout

```
core/         agent loop, tools, skills, OpenAI-compatible client, HiveMQ bus, MCP client for REA (Kotlin/JVM, shared)
shared-ui/    Compose Material3 screens, compiled into both apps
app-android/  Android app (Kotlin, Compose, NDK r30 + CMake native helper)
app-desktop/  Windows app (Compose Desktop on OpenJDK, MSI + portable zip)
.github/      CI: builds APK + Windows MSI/zip, publishes a release on v* tags
```

## Build

Requirements: OpenJDK 21, Gradle 8.10+ (or run `scripts/make-wrapper.sh` once), and for Android the
SDK with platform 35, NDK `30.0.16248370` (r30) and CMake 3.22.1.

```
gradle :app-android:assembleRelease    # app-android/build/outputs/apk/release (one universal APK)
gradle :app-desktop:run                # run on the desktop
gradle :app-desktop:packageMsi         # Windows installer (on Windows, needs WiX 3)
gradle :app-desktop:createDistributable  # portable folder
```

## Releases

Push to `main` to build both apps in GitHub Actions (artifacts on the run page). Push a tag like
`v1.0.0` to publish `SPIKE.apk` (one universal APK for all phones), `SPIKE-windows.msi` and `SPIKE-windows-portable.zip` as a release.
The release APK is signed with the debug key so it installs directly; use your own keystore for stores.

## Known limits

- REA mode is Windows/desktop only; its tool results are cut at 24,000 characters and the first query on a large
  binary can take minutes. Not tested against a live REA server in the environment this was written in.
- The agent stops when Android kills the process in the background (no foreground service yet).
- Replies are not streamed; long generations show up when complete.
- Not yet compiled or run on a device in the environment this scaffold was written in: expect to fix a
  first-build error or two (the CI log will say exactly where).

MIT licensed.
