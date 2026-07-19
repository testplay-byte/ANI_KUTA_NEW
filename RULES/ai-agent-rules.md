# AI Agent Rules — Complete Ruleset

> **Purpose:** This document defines the rules, boundaries, and expectations for AI agents working on this Android project. Every AI session must start by reading this file (and `ARCHITECTURE.md` at the project root). These rules are non-negotiable.

> **How to use:** Paste this entire document into your AI session at the start, or reference it as a project file. For a condensed version, see the [Master System Prompt](#-master-system-prompt--quick-paste-version) at the bottom.

---

## 1. No Blind Guesses

This is the foundational rule. AI must never silently assume, guess, or fabricate information. Every decision must be grounded in the project's existing code, architecture, or explicit user confirmation.

- **Do not make blind guesses or assumptions about anything** — module placement, architecture patterns, naming conventions, file locations, or user intent.
- **If something is unclear, list your top 2-3 assumptions explicitly**, explain the trade-off of each, and recommend one. Then ask for confirmation.
  - *Bad:* "I'm unsure about where to put this file. Could you clarify?"
  - *Good:* "This could go in `:feature:login/data/` or `:core:auth/data/`. My recommendation is `:feature:login/data/` because only the login feature uses it. If you later need it in signup too, we can extract it to `:core`. Should I proceed?"
- **The user may not know everything** — guide them properly and directly. Don't defer every small decision to the user. Make a recommendation and let them approve or correct it.
- **Do not sugarcoat issues.** Be honest about concerns, trade-offs, and risks. If an approach has downsides, say so upfront.
- **When in doubt, show your reasoning first**, then ask before proceeding. The user should always understand *why* you're proposing something.

---

## 2. Architecture Document (MUST READ FIRST)

`ARCHITECTURE.md` is the single source of truth for this project. It defines everything: module structure, folder conventions, naming rules, architecture patterns, dependencies, and AI-specific rules. This document complements it with behavioral rules.

- **Before doing ANY work, read `ARCHITECTURE.md` at the project root.** This is non-negotiable. Do not write a single line of code before reading it.
- **Follow every convention, module boundary, naming rule, and pattern** defined in `ARCHITECTURE.md`.
- **If something isn't covered in `ARCHITECTURE.md`, ask the user** — do not invent your own approach, pattern, or convention.
- **Keep `ARCHITECTURE.md` updated after every structural change.** An outdated architecture document is worse than no document at all. After adding a module, changing a convention, or making an architectural decision, update the file.
- **When making decisions, reference `ARCHITECTURE.md`** as the source of truth. If there's a conflict between a user's request and the architecture doc, flag it immediately.

---

## 3. Data Flow Rules (CRITICAL)

These rules define how data moves through the app. Violating these breaks the entire architecture.

- **Data flows strictly through layers:**
  ```
  UI → ViewModel → Repository → Data Source (API / Database)
  ```
  **Never skip a layer.** The UI never calls a Repository. The ViewModel never calls an API directly. The Repository never touches UI code.

- **UI files contain ONLY display logic and user event forwarding.**
  - Screens/Composables: display data, handle user interactions (button clicks, text input), and forward actions to the ViewModel.
  - Zero business logic. Zero data access. Zero calculations beyond simple display formatting.

- **ViewModels never call APIs, databases, or shared preferences directly.**
  - ViewModels call Repositories. Period.
  - All data access must go through a Repository class.
  - The ViewModel only knows about the Repository **interface** (defined in `:core`), never the implementation.

- **Repositories define interfaces in `:core`.** Implementations live in `:data` or the feature module. ViewModels depend on the interface only — never on the implementation.

- **Feature modules never import from other feature modules.**
  - If Feature A needs something from Feature B, that "something" belongs in `:core` as a shared interface.
  - Cross-feature communication goes through `:core` interfaces only.

---

## 4. Project Structure & Modularity

Every file has a correct home. Putting a file in the wrong place creates confusion, coupling, and technical debt.

- **Follow the module structure defined in `ARCHITECTURE.md`.** Each feature gets its own `:feature:*` module (e.g., `:feature:login`, `:feature:profile`, `:feature:settings`).

- **Each feature module contains these subfolders:**
  ```
  :feature:login/
  ├── ui/              # Screens, composables, UI components
  ├── viewmodel/       # ViewModels
  ├── data/            # Local data models (if any)
  ├── navigation/      # Navigation route definitions
  └── di/              # Dependency injection setup for this module
  ```

- **Code shared across 2+ features goes in `:core`.** Code used by only one feature stays in that feature's module.
  - `:core` is NOT a dumping ground. Only put things there if they are genuinely shared.
  - If only one feature uses something, keep it in that feature — even if you *think* another feature might use it later.

- **Never place all code in one large file.** Split into logical, focused files.
  - Suggested maximum: **~300 lines per file**, **~3 responsibilities per class**.
  - If a file is growing large, propose a split into smaller, focused files.

- **Document the purpose of every module and major file.** A new AI agent (or the user) should be able to understand the project from the documentation alone.

---

## 5. Dependency Management

AI loves adding new libraries for every small problem. This rule prevents dependency bloat.

- **Before adding any new library, check if the existing dependencies can solve the problem.**
  - Don't add Retrofit if you already have Ktor. Don't add Glide if you already use Coil.
  - Search the existing `build.gradle` files first.

- **If a new dependency is truly needed:**
  1. Explain what it does.
  2. Explain why existing dependencies can't do it.
  3. Add it **only to the module that needs it** in its `build.gradle`.
  4. Never add a dependency to `:app` when it belongs in a specific feature or `:core` module.

- **After adding a dependency, update `ARCHITECTURE.md`** to document what it's for and why it was added.

---

## 6. Design Language Consistency

The app's UI must feel like one cohesive product, not a patchwork of different styles.

- **Use only components from the design system** defined in `:core:designsystem` (or the equivalent path specified in `ARCHITECTURE.md`).
- **Before creating any custom UI component, check if the design system already provides it.** Reuse before creating.
- **If a needed component doesn't exist in the design system, add it there** — don't create one-off components in feature modules. This keeps the design system growing and consistent.
- **No random or inconsistent design changes.** Every UI element must adhere to the established design system. Colors, spacing, typography, border radius — all must match.
- **Never deviate from the design language without being asked.** If the user asks for something that contradicts the design system, flag it and confirm before proceeding.

---

## 7. Task Management

Large tasks fail when AI tries to do everything at once. This rule enforces a methodical, step-by-step approach.

- **Large tasks must be broken into smaller, manageable subtasks.**
  - If a request involves 3+ files or 2+ modules, it should be split into steps.

- **Complete subtasks one by one.** After each subtask, verify ALL of the following before moving on:
  1. The build compiles with zero errors.
  2. No existing tests broke.
  3. The change doesn't violate any module boundaries.
  4. Provide a one-sentence summary of what was done.

- **Verify builds via GitHub Actions (or local build)** to confirm changes work correctly before moving on.

- **Never rush through a huge task** — analyze, plan, split, and execute methodically.

- **Before writing any code, create a brief plan:**
  1. Which module(s) will be affected.
  2. Which files need to be created or modified.
  3. What data flows in and out.
  4. Present this plan and wait for approval before proceeding.

- **Think about side effects.** After planning, list any potential ripple effects:
  - Files that import the changed code.
  - Database migrations needed.
  - API contract changes.
  - Anything else that might break. Address each one.

---

## 8. Future-Proofing

Build the project so it's easy to extend, modify, and scale months from now.

- **Maintain loose coupling with a single entry point per module.**
  - Each module should have ONE public entry point (usually a navigation route or a DI module).
  - Everything else is internal. This means you can completely rewrite a module's internals without anything outside noticing.

- **Define clear data contracts.**
  - Define what data goes in and out of each module using data classes or interfaces.
  - Example: "The login module receives `LoginRequest` and returns `AuthResult`."
  - This documentation prevents surprises and makes refactoring safe.

- **Document all architecture decisions in `ARCHITECTURE.md`.**
  - When you choose Room over DataStore, Retrofit over Ktor, or MVVM over MVI — write down **what** was chosen and **why**.
  - Future-you (and future AI sessions) need this reasoning to avoid undoing good decisions.

- **Plan for feature flags from the start.**
  - New features should be toggleable without code changes where possible.
  - A simple feature flag system in `:core` (boolean in a config file) lets you ship hidden code, test in production, and do gradual rollouts.

---

## 9. Logging

Logging is essential for debugging, but done wrong it's a security hazard. Follow these rules strictly.

- **Include proper logging throughout the project.** Log every meaningful action:
  - Operations performed (e.g., "User login attempted").
  - Results returned (e.g., "Login successful", "Login failed: invalid credentials").
  - Errors encountered (with context, not just stack traces).

- **NEVER log sensitive data:**
  - API keys, auth tokens, passwords.
  - Personal identifiable information: email, phone number, address, full name.
  - Full request/response bodies (log only relevant fields and status codes).

- **Use log levels consistently:**
  - `DEBUG` — Development-only details (e.g., "ViewModel received updated state").
  - `INFO` — Meaningful user-facing actions (e.g., "User logged in", "Profile updated").
  - `WARN` — Potential issues that aren't errors yet (e.g., "API response took longer than expected").
  - `ERROR` — Failures that need attention (e.g., "Network call failed: timeout", "Database write failed").

- **Keep logs clean, filterable, and free of junk data.** Format logs consistently so they can be searched and filtered effectively.

---

## 10. Code Quality

These rules ensure the codebase stays maintainable, readable, and reliable.

- **Read before writing.** Understand existing code and patterns before making changes. Do not create a new file without first searching for similar files, patterns, or implementations that already exist. If something similar exists, extend it — don't duplicate.

- **No blind changes.** Think through the impact of every modification, including side effects on files that import the changed code.

- **Consistency over cleverness.** Follow existing patterns exactly. If the project uses `StateFlow` for state management, don't introduce `LiveData`. If it uses Hilt for DI, don't add Koin. Do not introduce new approaches unless explicitly asked.

- **Name everything descriptively.**
  - No abbreviations unless universally understood (e.g., URL, API, ID, UI).
  - No generic names: `Helper`, `Util`, `Manager`, `Handler`, `Processor`.
  - A name should explain **why** something exists, not just **what** it is.
  - *Bad:* `UserManager`, `processData()`, `helper`
  - *Good:* `UserSessionRepository`, `validateLoginCredentials()`, `EmailFormatter`

- **Handle errors explicitly.**
  - Every network call, database operation, and user input must have error handling.
  - **Never use empty catch blocks.** At minimum, log the error.
  - Define what the user sees when something fails: loading state → error message → retry option.
  - Errors should never crash the app silently.

- **No hardcoded values.**
  - Strings → `strings.xml` / resource files.
  - URLs → `BuildConfig` or a network config file.
  - Numeric constants → named `const val` in a companion object or constants file.
  - API keys → `local.properties` or environment variables (never in source code).

- **Write tests for ViewModels and Repositories** covering:
  1. The main success path.
  2. At least one error scenario (network failure, empty data, invalid input).
  3. Edge cases (null values, empty lists, boundary values).

---

## 11. Communication

How AI communicates with the user is as important as the code it writes.

- **Ask questions when unsure, but show your reasoning first.**
  - *Bad:* "Which module should this go in?"
  - *Good:* "This utility is currently only used by the login feature, so I'd put it in `:feature:login/util/`. If you plan to use it in signup too, I can put it in `:core:common/`. What do you prefer?"

- **Explain trade-offs clearly when multiple approaches are possible.**
  - Briefly present the options with their trade-offs (complexity, performance, maintainability).
  - Recommend one, but let the user make the final decision.

- **Flag architectural concerns proactively** — before they become problems.
  - If the requested change violates the project's architecture, naming conventions, or module boundaries, flag it immediately.
  - Explain why it's a concern and propose an alternative that respects the architecture.
  - Don't silently create technical debt.

- **After completing work, provide a structured summary:**
  1. Files created (with full path).
  2. Files modified (with a one-line description of what changed).
  3. Files deleted (if any).
  4. Any new dependencies added.
  5. Potential risks or things to watch.

- **If the user rejects an approach, don't argue.** Ask what their concern is and propose an alternative. Never push back on a user's decision.

- **If you notice a better way to do something**, suggest it as an optional improvement — not a substitution. "By the way, I noticed X could also be improved by doing Y. Want me to handle that separately?"

---

## 12. Error Handling Workflow

When fixing bugs or errors, follow this systematic 8-step process. Do not skip steps or try to "quick fix" without understanding the root cause.

1. **Understand** — Identify what the issue is. Reproduce it if possible. Read error logs carefully.
2. **Locate** — Determine which file or module is causing it. Trace the error back to its source.
3. **Analyze** — Evaluate fix options and their trade-offs. Don't jump to the first idea.
4. **Plan** — Design a fix strategy that respects the architecture. Consider side effects.
5. **Fix** — Implement the fix. Follow all other rules (module boundaries, naming, etc.).
6. **Verify** — Run tests to confirm the fix works AND nothing else broke (regression check).
7. **Rollback** — If the fix introduces new issues, revert the change and try an alternative approach. Never leave the codebase in a broken state.
8. **Document** — Record what went wrong, why, and how it was fixed. Add to `ARCHITECTURE.md` if it reveals an architecture weakness that should be addressed.

---

## 13. Git & Branch Conventions

These rules keep the project's version control clean and understandable.

- **Always work on a branch.** Never commit directly to `main` or `master`.
- **Branch naming convention:**
  - Features: `feature/short-description` (e.g., `feature/login-screen`)
  - Bug fixes: `fix/short-description` (e.g., `fix/crash-on-empty-input`)
  - Refactoring: `refactor/short-description` (e.g., `refactor/extract-repository`)
  - Architecture: `arch/short-description` (e.g., `arch/add-core-network-module`)

- **Commit messages should be descriptive:**
  - *Bad:* "fixed stuff", "update", "wip"
  - *Good:* "Add login screen with email/password validation to :feature:login"
  - *Good:* "Extract API calls from LoginViewModel into LoginRepository"

- **Each commit should represent one logical change.** Don't bundle unrelated changes into a single commit.

---

## 14. Module Boundary Rules (Reference)

These are the hard boundaries. Violating any of these is a critical error.

| Rule | Description |
|------|-------------|
| **UI has zero logic** | UI files contain ONLY display logic and user event forwarding. No business logic, no data access. |
| **ViewModel → Repository only** | ViewModels never call APIs, databases, or shared preferences directly. All data access goes through a Repository. |
| **Feature isolation** | Feature modules never import from other feature modules. Communication goes through `:core` interfaces. |
| **:core is shared only** | Only put code in `:core` if used by 2+ feature modules. Otherwise keep it in the feature module. |
| **No God classes** | No file over 300 lines. No class with more than 3 responsibilities. |
| **Interface in :core, impl elsewhere** | Repository interfaces live in `:core`. Implementations live in `:data` or the feature module. |

---

## Master System Prompt (Quick-Paste Version)

> Use this condensed version when you want to quickly set up an AI session without pasting the entire document. It covers the most critical rules.

```
You are working on an Android project. Follow these rules strictly:

1. Read ARCHITECTURE.md at the project root and follow all conventions, rules, and patterns defined there.

2. Before writing any code, create a brief plan: affected modules, files to create/modify, data flow. Wait for approval.

3. Respect module boundaries: feature modules never directly import from other feature modules. Use :core for shared interfaces.

4. Data flows strictly: UI → ViewModel → Repository → Data Source. Never skip a layer.

5. UI files contain only display logic and user event forwarding. All business logic and data access goes through ViewModel → Repository.

6. ViewModels never call APIs, databases, or shared preferences directly. Always go through a Repository.

7. Match existing codebase patterns exactly. Do not introduce new approaches unless explicitly asked.

8. No file over 300 lines. No class with more than 3 responsibilities. Propose splits when approaching limits.

9. Handle all errors explicitly. No empty catch blocks. Show users meaningful error states with retry options.

10. No hardcoded strings, URLs, or config values. Use appropriate resource/config files.

11. Before adding a new dependency, check if existing ones can solve the problem. Add only to the module that needs it.

12. NEVER log sensitive data: API keys, tokens, passwords, PII, or full request/response bodies. Use log levels (DEBUG, INFO, WARN, ERROR).

13. After each task, provide a structured summary: files created, files modified, new dependencies, and any risks.

14. If unsure about anything (module placement, naming, patterns), STOP and ask. Show your reasoning first, then ask.

15. Name everything descriptively. No abbreviations, no generic names like "Helper", "Util", or "Manager".

16. Only put code in :core if used by 2+ feature modules. Otherwise keep it in the feature module.

17. Write unit tests for ViewModels and Repositories: success paths, error cases, and edge cases.

18. For errors and bugs, follow this workflow: Understand → Locate → Analyze → Plan → Fix → Verify → Rollback (if needed) → Document.

19. Keep ARCHITECTURE.md updated after every structural change or architectural decision.

20. Use only components from the design system. Before creating a custom component, check if the design system already provides it. If not, add it to the design system.
```

---

*This document is a living file. Update it as the project evolves. Every new AI session starts here.*
