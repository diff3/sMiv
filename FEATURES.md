<p><b>sMiv</b> is modal editing for PhpStorm, based on <a href="https://github.com/diff3/miv">MIV</a> for VS Code: in NAV typed characters are commands, in INSERT you type as usual.</p>

<h3>Getting started</h3>
<ul>
  <li><code>Esc</code> – NAV: typed characters are commands and are never inserted</li>
  <li><code>i</code> – INSERT after the character under the caret</li>
  <li><code>Space</code> – INSERT before the character under the caret</li>
  <li>Status bar – shows <code>sMiv NAV</code>, <code>sMiv INSERT</code> or <code>sMiv SELECT</code> and the keys typed so far</li>
  <li>Other keys – Enter, Tab, Backspace, arrow keys and all shortcuts work as usual in NAV</li>
</ul>

<h3>Everyday keys</h3>
<ul>
  <li><code>w a s d</code> – up, left, down, right</li>
  <li><code>W S</code> – page up, page down</li>
  <li><code>A D</code> – start, end of the line</li>
  <li><code>q e</code> – start of the previous word, end of the next word</li>
  <li><code>Q E</code> – end of the previous word, start of the next word</li>
  <li><code>x</code> – delete the character under the caret</li>
  <li><code>b</code> – delete the line</li>
  <li><code>y</code> – yank (copy) the line</li>
  <li><code>p P</code> – paste before, after the caret (a yanked line: above, below)</li>
  <li><code>u</code> – undo; the caret and the view stay put</li>
  <li><code>o O</code> – new line below, above, then INSERT</li>
</ul>

<h3>Counts and more commands</h3>
<ul>
  <li><code>5s</code> <code>3x</code> <code>2b</code> <code>4y</code> – a number first repeats the key or takes that many</li>
  <li><code>g</code> <code>12g</code> – line 1, line 12, in the same column</li>
  <li><code>G</code> <code>3G</code> – end of the document, the 3rd line from the bottom</li>
  <li><code>m</code> <code>&gt;</code> – middle of the document; <code>3m</code> 30 %, <code>25m</code> 25 %</li>
  <li><code>&lt;</code> – middle of the text on the line; <code>3&lt;</code> 30 %, <code>25&lt;</code> 25 %</li>
  <li><code>- _</code> – first, last line inside the surrounding <code>()</code>, <code>[]</code> or <code>{}</code>; <code>3-</code> 30 % from the top, <code>3_</code> from the bottom</li>
  <li><code>%</code> – matching bracket or quote, or the closing one around the caret</li>
  <li><code>I k</code> – start, end of the line, then INSERT</li>
  <li><code>c</code> – change to the end of the line; <code>2c</code> also the next line</li>
  <li><code>C</code> – change from the start of the line, keeping the indentation</li>
  <li><code>r</code> + character – replace the character under the caret</li>
  <li><code>R</code> – change the word</li>
  <li><code>X</code> – delete the word</li>
  <li><code>B</code> – delete to the end of the line</li>
  <li><code>Y</code> – yank the word</li>
  <li><code>§ °</code> – toggle the case of the character, the word</li>
  <li><code>&amp;</code> – join with the next line</li>
  <li><code>J K</code> – move the line down, up</li>
  <li><code>L H</code> – indent, outdent</li>
  <li><code>.</code> – repeat the last edit, search or replace</li>
  <li><code>U</code> – revert to the saved version in one step; <code>u</code> brings the changes back</li>
</ul>

<h3>Registers</h3>
<ul>
  <li><code>0</code>–<code>9</code> – registers: <code>0</code> last yank, <code>8</code> last delete, <code>9</code> the system clipboard</li>
  <li><code>3p</code> <code>3P</code> – paste register 3 before, after the caret</li>
  <li><code>2 y</code> <code>5 2y</code> – yank 1, 5 lines into register 2</li>
  <li><code>3 x</code> <code>5 3x</code> – delete 1, 5 characters into register 3</li>
  <li><code>V</code> – register viewer: type a digit or pick a row to paste it, <code>Esc</code> closes</li>
  <li><code>2V</code> – store the clipboard in register 2</li>
  <li>Named register – never touches the clipboard; a plain yank goes to register 0 and the clipboard</li>
  <li>Yank – the yanked text flashes briefly</li>
</ul>

<h3>Selection</h3>
<ul>
  <li><code>v</code> – selection mode on, off; moves, arrow keys and searches extend the selection</li>
  <li><code>x y c C § ° p P</code> – act on the selection, also one made with the mouse or Shift; <code>p P</code> replace it</li>
  <li><code>( [ { " ' ` ´</code> – in selection mode: wrap the selection, for example <code>(foo)</code></li>
  <li><code>=new</code> <code>==new</code> – in selection mode: replace only inside the selection</li>
</ul>

<h3>Search and replace</h3>
<ul>
  <li><code>/text</code> <code>Enter</code> – search forward</li>
  <li><code>\text</code> <code>Enter</code> – search backward</li>
  <li><code>,regex</code> <code>Enter</code> – regex search, shown as <code>~</code></li>
  <li><code>n N</code> – next, previous match; <code>3n</code> three on; wraps around at the ends</li>
  <li><code>f F</code> – search the character under the caret forward, backward</li>
  <li><code>* #</code> – search the word under the caret forward, backward</li>
  <li>Smart case – lower case finds both cases, a capital makes the search exact; <code>f F * #</code> are always exact</li>
  <li><code>=new</code> <code>Enter</code> – step through the matches of the last search: <code>Enter</code> or <code>.</code> replaces, <code>n N</code> skip</li>
  <li><code>==new</code> <code>Enter</code> – replace all matches at once</li>
  <li><code>=old new</code> <code>==old new</code> – the same for the literal text <code>old</code>; <code>'a b'</code> quotes spaces</li>
  <li><code>=</code> <code>==</code> <code>Enter</code> – replace the current match, all matches, with the current rule</li>
  <li><code>$1</code> <code>$&amp;</code> <code>$&lt;name&gt;</code> – groups in the replacement after a regex search</li>
  <li><code>Backspace</code> <code>Esc</code> – edit, cancel the command line; <code>Esc</code> also hides the matches</li>
</ul>

<h3>Text objects</h3>
<ul>
  <li><code>! " ' ` ´ ( [ {</code> – the nearest pair around the caret; <code>!</code> takes any pair, also <code>&lt; &gt;</code></li>
  <li><code>"y</code> – yank the contents into register 0 and the clipboard</li>
  <li><code>"x</code> – delete the contents into register 8</li>
  <li><code>"c</code> – delete the contents into register 8, then INSERT</li>
  <li><code>"p</code> – replace the contents with the clipboard</li>
  <li><code>"Y</code> <code>"X</code> – yank, delete including the delimiters</li>
  <li><code>" 3y</code> <code>( 3x</code> <code>{ 3p</code> <code>[ 3c</code> – use only register 3; not with <code>!</code></li>
</ul>

<h3>Anchors, navigation and menu</h3>
<ul>
  <li><code>Z</code> <code>Alt+Z</code> – set the anchor</li>
  <li><code>z</code> <code>Alt+X</code> – jump to the anchor and back again, also across files</li>
  <li><code>3Z</code> <code>3z</code> – numbered anchors 0–9</li>
  <li><code>Alt+Q</code> <code>Alt+E</code> – previous, next paragraph</li>
  <li><code>Alt+A</code> <code>Alt+D</code> – navigate back, forward</li>
  <li><code>Ctrl+F</code> – the find bar</li>
  <li>Status bar click – menu: Registers, Command Stats, Toggle Search Highlight, Change Keybindings, Open KEYMAP</li>
</ul>

<h3>Settings</h3>
<ul>
  <li>Settings → Tools → sMiv – give any NAV key another character; digits and Space are fixed; Restore Defaults</li>
  <li>Settings → Keymap → sMiv – change the Alt shortcuts and <code>Ctrl+F</code></li>
</ul>

<h3>Good to know</h3>
<ul>
  <li>IdeaVim – do not use it at the same time; sMiv warns and can turn itself off for the session</li>
  <li>Enable, disable – PhpStorm needs a restart</li>
  <li>Carets – NAV commands only use the primary caret</li>
  <li>Editors – sMiv works in code editors, not in consoles, commit messages or dialogs</li>
  <li>One mode – NAV and INSERT apply to all editors at once</li>
</ul>
