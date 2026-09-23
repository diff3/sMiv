# sMiv Keymap

sMiv is a layer on top of PhpStorm. In `NAV` only typed characters (letters, digits, special characters) are
taken over and never inserted. All other keys and shortcuts (Enter, Tab, Backspace, Delete, Cmd+C/X/V/A,
selecting text, ...) work as usual.

## Modes

- `ESC` enters `NAV`. `i` or `Space` enters `INSERT`.
- `I` goes to the line start and `k` to the line end, then `INSERT`.
- `Enter` in `NAV` works as usual, except that it cancels a half-typed command such as `5`, commits the
  search / replace command line, and applies an active replace rule to the current match.
- The status bar shows `sMiv NAV` / `sMiv INSERT` and what you are typing. It is highlighted in `INSERT` and while
  a command is typed. Click it for the sMiv menu.
- Block cursor in `NAV`. Only code editors are affected. `NAV` commands use the primary caret only.

## Motion

- `a` / `d` left / right, `w` / `s` up / down.
- `W` / `S` page up / down, `A` / `D` line start / end.
- `q` start of the previous word, `e` end of the next word, `Q` end of the previous word, `E` start of the next word.
- `%` matching bracket or quote.
- `-` / `_` first / last line inside the surrounding `()`, `[]` or `{}` block.
- `g` line 1, `[n]g` line n, `m` 50 %, `1m`..`9m` 10–90 %, `G` document end, `[n]G` n lines from the bottom.
- `Alt+Q` / `Alt+E` previous / next paragraph.
- `Alt+A` / `Alt+D` navigate back / forward.
- `Alt+Z` sets an anchor, `Alt+X` jumps to it and then toggles between the anchor and where you jumped from.
- `Ctrl+F` opens the find bar.
- Counts work with motions, for example `10s`.

## Editing

- `x` delete a character, `X` delete a word, `b` delete the line, `B` delete to the line end (all into register `8`).
- `r<char>` replace the character under the caret, `R` change the word.
- `§` / `°` toggle the case of the character / word.
- `c` change to the line end (like Vim's `C`, takes a count), `C` change from the line start (keeps indentation).
  Both store the deleted text in register `8`.
- `&` join lines, `o` / `O` open a line below / above.
- `u` undo (caret and view stay put), `U` revert to the last saved version (one step, so `u` brings the changes back).
- `.` repeat the last edit, search or replace.
- Counts: `5x`, `3b`, `3X`, `2Y`, `2R`, `3§`, `2°`, `2c`.

## Yank, paste and registers

- `y` yanks line(s), `Y` word(s), into register `0` and the clipboard.
- `p` pastes before, `P` after the caret, from the clipboard. `3p` / `3P` paste register `3`.
- Registers `0`–`9`. Register `9` is the system clipboard, so Cmd+C and copies from other apps show up there.
- `2 y` yanks the current line into register `2`, `2 4y` two lines into register `4`.
- `3 x` / `5 3x` delete one / five characters into register `3`.
- A yank or delete into a named register only writes that register, not the clipboard.
- `2v` stores the clipboard in register `2`.
- `v` opens the register viewer. Type a digit or pick a row to paste that register. It closes after two seconds.

## Search and replace

- `/text` forward, `\text` backward, `,pattern` regex (shown as `~`), then `Enter`.
- `n` / `N` next / previous match from the caret. Matches are highlighted until `Esc`.
- `Backspace` edits the command line, `Esc` cancels it.
- `=replacement` steps through the matches of the last search (literal or regex): `Enter` or `.` replaces the
  match at the caret and moves to the next, `n` / `N` skip one, `Esc` stops.
- `==replacement` replaces every match of the last search at once.
- `=search replacement` / `==search replacement` do the same for a literal `search` (`'a b'` quotes spaces).
- Regex rules understand `$1`, `$&`, `$<name>`. A replacement starting with `=` is written in quotes: `='=x'`.
- `=` alone replaces the current match, `==` alone every match of the current rule. `u` undoes a full replace in one step.

## Text objects

Inside `!` (automatic), `"`, `'`, `` ` ``, `´`, `(`, `[` or `{`:

- `"y` yanks (register `0` and the clipboard), `"x` deletes (register `8`), `"p` replaces with the clipboard.
- `" 3y`, `( 3x`, `{ 3p` use only register `3`.

## sMiv menu

Click the status bar item: Registers, Command Stats, Toggle Search Highlight, Change Keybindings, Open KEYMAP.

## Custom keys

Settings → Tools → sMiv (or Change Keybindings in the sMiv menu) lists every `NAV` key by MIV's command name, for
example `LEFT` or `DELETE_LINE`. Type a new character to move a command; the new key replaces the default one. Digits
and Space are fixed. The Alt shortcuts are in Settings → Keymap under sMiv.
