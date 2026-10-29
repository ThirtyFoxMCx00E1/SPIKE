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

Tools: `list_dir`, `read_file`, `write_file`, `edit_file`, `search`, `run_shell`, `http_get`, `load_skill`.
Files are confined to the workspace folder. `run_shell` runs real commands with the workspace as the
working directory, so turn it off in Settings if you do not want that.

Built-in skills (`core/src/main/resources/skills`): coding, debugging, code-review, android-ndk,
kotlin-compose, git-github-actions, hivemq-mqtt, linux-ops, research. Add your own as
`<data folder>/skills/<name>/SKILL.md` with `name:` and `description:` front matter.
Data folder: `%USERPROFILE%\.spike` on Windows, the app's private storage on Android.

## HiveMQ bus

Turn on the bus in Settings to publish agent output to `spike/<channel>/events` and accept prompts on
`spike/<channel>/cmd`. That lets the Windows app watch or drive the agent on your phone, and back.
The default broker is the public `broker.hivemq.com`, which anyone can read: use it for testing only.
For real use, run your own broker with a login and TLS: see
[ThirtyFoxMCx00E1/HiveMQ](https://github.com/ThirtyFoxMCx00E1/HiveMQ) and the
[wagu](https://thirtyfoxmcx00e1.github.io/wagu/) guide.

## Layout

```
core/         agent loop, tools, skills, OpenAI-compatible client, HiveMQ bus (Kotlin/JVM, shared)
shared-ui/    Compose Material3 screens, compiled into both apps
app-android/  Android app (Kotlin, Compose, NDK r30 + CMake native helper)
app-desktop/  Windows app (Compose Desktop on OpenJDK, MSI + portable zip)
.github/      CI: builds APK + Windows MSI/zip, publishes a release on v* tags
```

## Build

Requirements: OpenJDK 21, Gradle 8.10+ (or run `scripts/make-wrapper.sh` once), and for Android the
SDK with platform 35, NDK `30.0.16248370` (r30) and CMake 3.22.1.

```
gradle :app-android:assembleDebug      # app-android/build/outputs/apk/debug
gradle :app-desktop:run                # run on the desktop
gradle :app-desktop:packageMsi         # Windows installer (on Windows, needs WiX 3)
gradle :app-desktop:createDistributable  # portable folder
```

## Releases

Push to `main` to build both apps in GitHub Actions (artifacts on the run page). Push a tag like
`v1.0.0` to publish `SPIKE-android.apk`, `SPIKE-windows.msi` and `SPIKE-windows-portable.zip` as a release.
The release APK is signed with the debug key so it installs directly; use your own keystore for stores.

## Known limits

- The agent stops when Android kills the process in the background (no foreground service yet).
- Replies are not streamed; long generations show up when complete.
- Not yet compiled or run on a device in the environment this scaffold was written in: expect to fix a
  first-build error or two (the CI log will say exactly where).

MIT licensed.
