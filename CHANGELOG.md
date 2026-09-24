# Changelog

## 1.0.0 – 2026-09-24

First release. sMiv brings MIV's modal editing to PhpStorm, with the same keys as MIV 2.0.0.

- `NAV` / `INSERT` modes as a layer on top of PhpStorm: in `NAV` only typed characters are taken over, all other
  keys and shortcuts work as usual. Block cursor in `NAV`, mode and command line in the status bar.
- Movement: `w a s d`, `W A S D`, `q e Q E`, `g m G` with counts and percentages, `-` / `_` (and `5-` / `15_`) inside
  the surrounding block, `<` / `>` middle of the line / document, `%`, paragraphs (`Alt+Q` / `Alt+E`), navigate
  back / forward (`Alt+A` / `Alt+D`).
- Editing: `x X b B c C r R § ° & J K L H o O i I k`, `u` without moving the view, `U` revert to the saved version,
  `.` repeat.
- Registers `0`–`9` (register `9` is the clipboard), `[r] y`, `[n] [r]x`, `[r]p`, `[r]P`, `[r]V`, register viewer `V`.
- Selection mode `v` (also with the arrow keys); `x y c C § ° p P` act on any selection.
- Search `/ \ ,`, `f` / `F` (character) and `*` / `#` (word) under the caret, `n` / `N` with wrap-around, smart case,
  highlighted matches.
- Replace: `=new` steps through matches (`Enter` replaces one, `n` skips one), `==new` replaces all; regex groups.
- Text objects `! " ' ( [ {` with `y x p` inside and `Y X` including the delimiters.
- Anchors `Z` / `z`, sMiv menu from the status bar, command stats, custom `NAV` keys in Settings → Tools → sMiv.
