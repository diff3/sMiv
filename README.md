# sMiv

Modal editing (NAV/INSERT) for PhpStorm, ported from the [MIV](https://github.com/diff3/miv) VS Code extension.

## Version 0.1 (MVP)

- `ESC` → NAV, `i` / `Space` → INSERT. `Enter` does nothing in NAV.
- Movement: `w a s d`, `W`/`S` page up/down, `A`/`D` line start/end, `q e Q E` words.
- Editing: `x` delete char, `b` delete line, `y` yank line, `p` paste before, `P` paste after, `u` undo, `o`/`O` open line below/above.
- Counts: `10s`, `5x`, `3b`, `2y`. `Np` / `NP` are reserved for registers (phase 2).
- Status bar: `sMiv NAV` / `sMiv INSERT` plus the command being typed.
- Block cursor in NAV. Only main code editors are affected. NAV commands use the primary caret only.

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

- `core/` – pure Kotlin (parser, state, engine, text helpers); unit tested.
- `SmivService` – app-wide state, cursor shape, status updates.
- `SmivInput` – raw typed handler + Escape/Enter handlers.
- `SmivEffects` – applies engine effects via Document/CaretModel/WriteCommandAction.
- `SmivStatusBar` – status bar widget and IdeaVim warning.
