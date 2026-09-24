# Changelog

## Unreleased

- `"c`, `(c`, `!c` … empty a text object and enter INSERT (`( 3c` stores the old text in register `3`).
- In selection mode `(`, `[`, `{`, `"`, `'`, `` ` `` and `´` wrap the selection.
- `=new` / `==new` with a selection only replace inside it.
- Numbered anchors: `3Z` sets anchor `3`, `3z` jumps to it.
- `f` / `F` / `*` / `#` only highlight the matches, the caret stays; step with `n` / `N`.
- `n` / `N` take a count: `3n` goes three matches on.
- The register viewer stays open until you pick a register or press `Esc`.

## 1.0.0 – 2026-09-24

First Marketplace release. sMiv brings MIV's modal editing to PhpStorm, with the same keys as MIV 2.0.0.

- Swapped `v` and `V`: `v` is selection mode, `V` the register viewer (`2V` stores the clipboard in register 2).
- `f` / `F` search the character under the caret forward / backward (like `*` / `#` for the word); `n` / `N` step.
- `<` goes to the middle of the text on the line, `>` to the middle of the document (same as `m`), both with
  percentages (`25<`, `3>`).
- `Z` sets the anchor and `z` jumps to it and back (`Alt+Z` / `Alt+X` still work).
- `i` enters INSERT after the character under the caret; `Space` enters INSERT before it.
- Two digits before `m`, `-` or `_` give the percentage itself (`15-` is 15 %).
- The arrow keys extend the selection in selection mode.

## 0.6.0 – 2026-09-23

- Selection mode: motions extend the selection; `x y c C § ° p P` act on any selection.
- `*` / `#` search the whole word under the caret.
- `J` / `K` move lines down / up, `L` / `H` indent / outdent, with counts.
- `"X` / `(Y` (and the other text objects) delete / yank including the delimiters.
- `5-` / `5_` jump a percentage into the surrounding block from the top / bottom.
- Searches use smart case (no capitals ignores case) and wrap around at the end of the file.
- `=new` steps through the matches of the last search (`Enter` replaces one, `n` / `N` skip one); `==new` replaces all.

## 0.5.0 – 2026-09-23

- Custom NAV keys in Settings → Tools → sMiv, listed by MIV's command names.
- `Ctrl+F` opens the find bar in NAV.

## 0.4.0 – 2026-09-23

- Register viewer, opened from the status bar menu or with a key; type a digit to paste that register.
- Anchors: set one and jump to it and back, also across files; the anchor follows edits.
- sMiv menu on the status bar: registers, command stats, search highlight on / off, keybindings, KEYMAP.
- Yanked text flashes briefly. The status bar is highlighted in INSERT, selection mode and while a command is typed.

## 0.3.0 – 2026-09-23

- Search: `/text`, `\text` (backward), `,regex`, then `n` / `N`; matches are highlighted until `Esc`.
- Replace: `=replacement` for the last search, `=search replacement` for a literal text; regex groups (`$1`).
- `.` repeats the last edit, search or replace.
- Text objects inside `!` (automatic), `"`, `'`, `(`, `[`, `{`, backtick and acute accent, with `y`, `x` and `p`,
  optionally with a register (`" 3y`).
- `c` / `C` change to the line end / from the line start, `-` / `_` jump to the first / last line in the block.
- `u` undoes without moving the caret or the view; `U` reverts to the last saved version in one undoable step.
- NAV only takes over typed characters; Enter, Tab, Backspace, Delete and all shortcuts work as usual.
- A yank or delete into a named register no longer writes the clipboard; `2 y` yanks the current line into register 2.

## 0.2.0 – 2026-09-23

- Editing: `r<char>`, `R`, `X`, `B`, `Y`, `§` / `°` (toggle case), `&` (join lines), `%` (matching bracket),
  `I` / `k` (INSERT at line start / end).
- Goto: `g`, `[n]g`, `m`, `[1-9]m`, `G`, `[n]G`. Paragraphs with `Alt+Q` / `Alt+E`, navigate back / forward with
  `Alt+A` / `Alt+D`.
- Registers `0`–`9`; register `9` is the system clipboard. `[n] [r]x`, `[n] [r]y`, `[r]p`, `[r]P`, and storing the
  clipboard in a register.

## 0.1.0 – 2026-09-23

- NAV and INSERT modes: `Esc` to NAV, `i` / `Space` to INSERT. Block cursor in NAV, mode in the status bar.
- Movement: `w a s d`, `W` / `S` page up / down, `A` / `D` line start / end, `q e Q E` words, with counts (`10s`).
- Editing: `x` delete character, `b` delete line, `y` yank line, `p` / `P` paste before / after, `u` undo,
  `o` / `O` open a line below / above.
- A warning when IdeaVim is enabled too.
