# sMiv

Modal editing (NAV/INSERT) for PhpStorm, ported from the [MIV](https://github.com/diff3/miv) VS Code extension.

## Keys (version 0.2)

- `ESC` → NAV, `i` / `Space` → INSERT, `I` line start + INSERT, `k` line end + INSERT. `Enter` in NAV only cancels a half-typed command.
- Movement: `w a s d`, `W`/`S` page up/down, `A`/`D` line start/end, `q e Q E` words, `%` matching bracket.
- Goto: `g` line 1, `[n]g` line n, `m` 50 %, `1m`..`9m` 10–90 %, `G` document end, `[n]G` n lines from the bottom.
- `Alt+Q` / `Alt+E` previous/next paragraph, `Alt+A` / `Alt+D` navigate back/forward.
- Editing: `x` char, `X` word, `b` line, `B` to line end (all into register 8), `r<char>` replace char, `R` change word,
  `§` / `°` toggle case of char/word, `-` change to line end, `_` change line, `&` join lines, `u` undo, `o`/`O` open line.
- Yank: `y` line(s), `Y` word(s) → register 0 and the clipboard.
- Paste: `p` before, `P` after (from the clipboard); `3p` / `3P` from register 3.
- Registers 0–9, register 9 is the system clipboard (so Cmd+C and copies from other apps show up there):
  `5 3x` delete 5 chars into register 3, `2 4y` yank 2 lines into register 4, `2v` store the clipboard in register 2.
- Counts: `10s`, `5x`, `3b`, `2y`, `3X`, `2Y`, `2R`, `3§`, `2°`.
- Status bar: `sMiv NAV` / `sMiv INSERT` plus the command being typed or the last message.
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

- `core/` – pure Kotlin (parser, state, engine, text helpers in `TextOps`); unit tested.
- `SmivService` – app-wide state, cursor shape, status updates.
- `SmivInput` – raw typed handler, Escape/Enter handlers and the Alt shortcut actions.
- `SmivEffects` – applies engine effects via Document/CaretModel/WriteCommandAction.
- `SmivStatusBar` – status bar widget and IdeaVim warning.
