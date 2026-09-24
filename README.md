# sMiv

Modal editing (NAV/INSERT) for PhpStorm, ported from the [MIV](https://github.com/diff3/miv) VS Code extension.

## Keys

sMiv is a layer on top of PhpStorm: in `NAV` typed characters are commands and are never inserted, everything else
works as usual. `ESC` → `NAV`, `i` / `Space` → `INSERT` after / before the caret, `w a s d` to move, `x b y p` to
edit, `v` to select, `/` to search, `=` to replace, `V` for registers, `Z` / `z` for the anchor. Click the status bar
item for the sMiv menu.
NAV keys can be changed in Settings → Tools → sMiv.

The full list is in [KEYMAP.md](src/main/resources/nu/entropy/smiv/KEYMAP.md), which the sMiv menu also opens.

## Logo

The plugin logo goes in `src/main/resources/META-INF/`, as 40×40 SVG files:

- `src/main/resources/META-INF/pluginIcon.svg` – light theme
- `src/main/resources/META-INF/pluginIcon_dark.svg` – dark theme (optional; the light one is used if it is missing)

## Build

The build uses PhpStorm's bundled JBR and the local PhpStorm installation as the platform:

```bash
export JAVA_HOME=/Applications/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew test          # core unit tests (no IDE needed)
./gradlew runIde        # sandboxed PhpStorm with sMiv installed
./gradlew verifyPlugin  # Plugin Verifier against the local PhpStorm
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

## License

MIT, see [LICENSE](LICENSE).
