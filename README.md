# sMiv

Modal editing (NAV/INSERT) for PhpStorm, ported from the [MIV](https://github.com/diff3/miv) VS Code extension.

## Keys (version 0.5)

sMiv is a layer on top of PhpStorm: in `NAV` typed characters are commands and are never inserted, everything else
works as usual. `ESC` → `NAV`, `i` / `Space` → `INSERT`, `w a s d` to move, `x b y p` to edit, `/` to search,
`=` to replace, `v` for registers, `Alt+Z` / `Alt+X` for the anchor. Click the status bar item for the sMiv menu.
NAV keys can be changed in Settings → Tools → sMiv.

The full list is in [KEYMAP.md](src/main/resources/nu/entropy/smiv/KEYMAP.md), which the sMiv menu also opens.

## Build

The build uses PhpStorm's bundled JBR and the local PhpStorm installation as the platform:

```bash
export JAVA_HOME=/Applications/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test          # core unit tests (no IDE needed)
./gradlew runIde        # sandboxed PhpStorm with sMiv installed
./gradlew buildPlugin   # build/distributions/sMiv-<version>.zip
```

Install: Settings → Plugins → ⚙ → Install Plugin from Disk… → pick the zip → restart.

## Layout

- `core/` – pure Kotlin (parser, state, engine, `TextOps`, `Search`, `Tools`); unit tested.
- `SmivService` – app-wide state, cursor shape, status updates.
- `SmivInput` – raw typed handler, Escape/Enter handlers and the Alt shortcut actions.
- `SmivEffects` – applies engine effects via Document/CaretModel/WriteCommandAction.
- `SmivStatusBar` – status bar widget and IdeaVim warning.
- `SmivPopups` – register viewer, sMiv menu, stats and KEYMAP views.
- `SmivSettings` – custom NAV keys (Settings → Tools → sMiv).
