# KMP Studio

> Android Studio feels heavy.
> VS Code was never built for Kotlin.
>
> So we built a Kotlin-native IDE — using Kotlin Multiplatform itself.

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Compose_for_Web-wasmJs-7C3AED" />
  <img src="https://img.shields.io/badge/Ktor-3.x-E85B2A" />
  <img src="https://img.shields.io/badge/AI-Claude_%7C_GPT_%7C_Gemini_%7C_Ollama-00C2A0" />
  <img src="https://img.shields.io/badge/license-MIT-green" />
</p>

---

<p align="center">
  <video src="https://github.com/user-attachments/assets/ffc44e20-3899-4144-9629-6eb0d5aaf567" width="860" controls autoplay muted loop></video>
</p>

<p align="center"><em>Project wizard → AI diff → Gradle build → Live emulator stream</em></p>

---

## Why this exists

Kotlin Multiplatform is production-ready. The tooling isn't.

Every KMP experience today is trapped inside tools built for other ecosystems — IntelliJ plugins, VS Code extensions, Electron wrappers. All adapted for KMP as an afterthought.

> Build the IDE itself with Kotlin Multiplatform.

No Electron. No plugin architecture. No JavaScript framework.

The UI is Kotlin compiled to WebAssembly — not a JS bundle, not an Electron shell.

---

## What it does

> **Describe a change. The AI edits your entire codebase** — multi-file diffs, applied inline inside Monaco. Not a suggestion, not a chat bubble. Actual code changes across every affected file, in one shot.

- **Agentic build loop** — AI applies the diff → Gradle builds → if it fails, AI reads the errors and tries again. Automatically. You review the result, not the process
- **Full native dev loop in the browser** — Gradle build with live streaming output, run on device, Logcat, live emulator screen decoded via WebCodecs — without leaving the tab
- **Works on any device** — phone, tablet, desktop — no app to install, no cable, no pairing
- **Runs fully offline** — point it at a local [Ollama](https://ollama.com) instance and every AI feature works with zero cloud dependency, zero cost, and zero data leaving your machine
- **AI scaffold** — describe your app idea in plain English, the AI picks the right architecture, libraries and targets, then generates a complete ready-to-build KMP project
- **Semantic search** — ask "where is X implemented?" in natural language, get the top matches ranked by an LLM with a one-line explanation per result

<p align="center">
  <img width="832" height="472" alt="How it works — main flow" src="https://github.com/user-attachments/assets/a8481d06-7072-4eba-946c-f961dcaf1089" />
</p>

---

## How KMP Studio compares

| Feature | KMP Studio | Android Studio | VS Code |
|---|---|---|---|
| RAM usage | **< 500 MB** | 2–4 GB | ~300 MB + plugins |
| New Android CLI (2025+) | **Native** | Legacy wrapper | ❌ |
| Runs in any browser | **✅** | ❌ | ❌ |
| Phone / tablet support | **✅ full IDE** | ❌ | ⚠️ limited |
| Frontend tech | **Kotlin/WASM** | Swing | Electron |
| AI multi-file edits | **✅ built-in** | ❌ | via extension |
| Offline AI (Ollama) | **✅** | ❌ | via extension |
| Bring your own model | **✅** | ❌ | via extension |
| Customizable templates | **✅** | ❌ | ❌ |
| Built with KMP | **✅** | ❌ | ❌ |

---

## Code from anywhere

KMP Studio is a web app. The agent is the only thing that needs to run locally.

Find your machine's local IP and open the IDE on any device on your network — no app, no cable, no pairing.

```bash
ipconfig getifaddr en0   # macOS — e.g. 192.168.1.42
# then on your phone: http://192.168.1.42:8080
```

Monaco editor, file tree, AI assistant, emulator panel — the full IDE on a phone screen.

**Full Kotlin Multiplatform dev loop from your phone.**

---

## Project scaffolding

Describe your app in plain English. KMP Studio picks the right architecture, targets, and libraries, then generates a complete, ready-to-build Gradle project — Android, iOS, Desktop, Web, or any combination.

Choose MVVM, MVI, or Clean Architecture. Add Ktor, SQLDelight, Koin, Coil, Voyager — all versions pinned and verified to work together. Generation streams live to the log, file by file.

Every generated file comes from a `.mustache` template. Drop your own into `~/.kmpstudio/templates/` to override any built-in — enforce your folder structure, pre-wire your design system, ship team-wide starters.

---

## AI code assistant

Describe the change. Get a diff. Apply it with one click.

- The AI reads your open files as context before answering
- Suggestions come back as structured diffs applied directly inside Monaco
- Streaming responses — tokens appear as they arrive, not all at once
- **Multi-file edits** — ask the AI to refactor a module and it edits every affected file in one shot

### Multi-file edits

The AI can modify as many files as a change requires — not just the file you have open.

When you confirm a plan, KMP Studio:

1. Feeds the **full content of every affected file** to the AI as context
2. Applies each diff directly inside Monaco (undo history intact) for the active file
3. Patches already-open files in their editor tabs in the background
4. Background-loads any file that wasn't open yet, patches it, and opens its tab — without switching away from your current file
5. Writes all patched files to disk atomically

A single prompt like _"add prompt caching to AiProxy and update the shared message type"_ touches `AiProxy.kt`, `WsMessage.kt`, and `IdeScreen.kt` — all applied in one round trip.

### Supported providers — bring your own key

| Provider | How to use |
|---|---|
| **Claude** (Anthropic) | `ANTHROPIC_API_KEY` env var or Settings — prompt caching enabled |
| **GPT-4o** (OpenAI) | API key in Settings |
| **Gemini** | API key in Settings — has a generous free tier |
| **Any OpenAI-compatible** | Custom base URL — works with Ollama, OpenRouter, Groq… |

No mandatory subscription. Point the Custom endpoint at a local [Ollama](https://ollama.com) instance and the AI assistant is completely free and offline.

---

## Build, run & emulator

Android is the first fully-integrated target. Build, deploy, and debug without leaving the browser. iOS simulator streaming is on the roadmap.

- **Build** — streams `assembleDebug` output line by line, colour-coded by severity
- **Run** — installs and launches on a connected device or running emulator
- **Logcat** — live filtered log stream
- **AVD Manager** — list, create, start and stop virtual devices
- **Live emulator screen** — H.264 video piped from the `android` CLI, decoded with the WebCodecs API

---

## Getting started

### Prerequisites

- JDK 17+
- [`android` CLI](https://developer.android.com/tools) on `$PATH` (Android CLI tools 2025+)
- An AI key — Claude, OpenAI, or Gemini. Or run [Ollama](https://ollama.com) locally for free.

### Run

```bash
git clone https://github.com/peterrichardc/kmpstudio
cd kmpstudio

# Terminal 1 — local agent (port 8765)
./gradlew :agent:run

# Terminal 2 — frontend dev server (port 8080)
./gradlew :frontend:wasmJsBrowserDevelopmentRun
```

Open **http://localhost:8080**.

> For Claude AI, set `ANTHROPIC_API_KEY` in your environment.
> For a zero-cost setup, point the Custom provider at a local Ollama instance.

---

## Architecture

```
┌────────────────────────────────┐
│  Browser  (Compose / wasmJs)   │
│  Monaco · AI Chat · Emulator   │
└───────────────┬────────────────┘
                │  WebSocket
┌───────────────▼────────────────┐
│  Local Ktor Agent  (JVM)       │
│  Gradle · Android CLI · AI     │
└───────────┬────────────────────┘
            │
   ┌────────┴────────┐
   │                 │
┌──▼──────┐   ┌──────▼──────┐
│ Android │   │ AI Providers│
│Emulator │   │Claude · GPT │
└─────────┘   └─────────────┘
```

The `shared` module defines the WebSocket protocol as a sealed class.
Both sides use the exact same Kotlin types — no schema drift, no serialization surprises.

---

## Project structure

```
kmpstudio/
├── shared/        # WsMessage sealed class — KMP wire protocol (JVM + wasmJs)
├── agent/         # Ktor server: CLI runner, AI proxy, FS, scaffolder
│   └── resources/templates/
│       ├── common/    # Gradle files, manifests, platform entry points
│       ├── clean/     # Clean Architecture templates
│       ├── mvvm/      # ViewModel + AppState templates
│       └── mvi/       # Store + Contract templates
└── frontend/      # Compose for Web: IDE UI, Monaco interop, emulator canvas
```

---

## Stack

| Layer | Technology |
|---|---|
| Frontend | Kotlin/wasmJs · Compose Multiplatform · Monaco Editor |
| Agent | Kotlin/JVM · Ktor · Coroutines · Mustache |
| Shared | KMP module — sealed `WsMessage` protocol, both sides |
| AI | Anthropic SSE · OpenAI · Gemini · OpenAI-compatible |

---

## What's coming

- iOS simulator streaming
- One-click web deploy to GitHub Pages / Netlify
- Diff history — browse and revert AI-applied changes
- GitHub integration
- Template marketplace — share and install community packs
- Collaborative sessions
- Desktop app via Compose Desktop

Star the repo to follow along.

---

## Contributing

Three Gradle modules. Straightforward Kotlin throughout. Intentionally modular and hackable.

If you want to add a template, fix a bug or build a new feature — open an issue first, then PR.

If you want to help shape the future of Kotlin tooling, jump in.

---

<p align="center">
  <strong>If this project sparks an idea or saves you time, a ⭐ goes a long way.</strong><br/>
  <em>It helps other Kotlin developers find this project.</em>
</p>

---

## License

MIT
