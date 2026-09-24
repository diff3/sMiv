<p><b>sMiv</b> är modal editering för PhpStorm, baserad på <a href="https://github.com/diff3/miv">MIV</a> för VS Code: i NAV är tecknen du skriver kommandon, i INSERT skriver du som vanligt.</p>

<h3>Kom igång</h3>
<ul>
  <li><code>Esc</code> – NAV: tecken du skriver är kommandon och infogas aldrig</li>
  <li><code>i</code> – INSERT efter tecknet under markören</li>
  <li><code>Space</code> – INSERT före tecknet under markören</li>
  <li>Statusfältet – visar <code>sMiv NAV</code>, <code>sMiv INSERT</code> eller <code>sMiv SELECT</code> och tangenterna du skrivit hittills</li>
  <li>Andra tangenter – Enter, Tab, Backsteg, piltangenter och alla kortkommandon fungerar som vanligt i NAV</li>
</ul>

<h3>Vardagstangenter</h3>
<ul>
  <li><code>w a s d</code> – upp, vänster, ner, höger</li>
  <li><code>W S</code> – sida upp, sida ner</li>
  <li><code>A D</code> – början, slutet av raden</li>
  <li><code>q e</code> – början av föregående ord, slutet av nästa ord</li>
  <li><code>Q E</code> – slutet av föregående ord, början av nästa ord</li>
  <li><code>x</code> – radera tecknet under markören</li>
  <li><code>b</code> – radera raden</li>
  <li><code>y</code> – kopiera (yank) raden</li>
  <li><code>p P</code> – klistra in före, efter markören (en kopierad rad: ovanför, nedanför)</li>
  <li><code>u</code> – ångra; markören och vyn står kvar</li>
  <li><code>o O</code> – ny rad nedanför, ovanför, sedan INSERT</li>
</ul>

<h3>Antal och fler kommandon</h3>
<ul>
  <li><code>5s</code> <code>3x</code> <code>2b</code> <code>4y</code> – ett tal först upprepar tangenten eller tar så många</li>
  <li><code>g</code> <code>12g</code> – rad 1, rad 12, i samma kolumn</li>
  <li><code>G</code> <code>3G</code> – slutet av dokumentet, 3:e raden nerifrån</li>
  <li><code>m</code> <code>&gt;</code> – mitten av dokumentet; <code>3m</code> 30 %, <code>25m</code> 25 %</li>
  <li><code>&lt;</code> – mitten av texten på raden; <code>3&lt;</code> 30 %, <code>25&lt;</code> 25 %</li>
  <li><code>- _</code> – första, sista raden inuti omgivande <code>()</code>, <code>[]</code> eller <code>{}</code>; <code>3-</code> 30 % uppifrån, <code>3_</code> nerifrån</li>
  <li><code>%</code> – matchande parentes eller citattecken, eller den stängande runt markören</li>
  <li><code>I k</code> – början, slutet av raden, sedan INSERT</li>
  <li><code>c</code> – ändra till radens slut; <code>2c</code> även nästa rad</li>
  <li><code>C</code> – ändra från radens början, indraget behålls</li>
  <li><code>r</code> + tecken – ersätt tecknet under markören</li>
  <li><code>R</code> – ändra ordet</li>
  <li><code>X</code> – radera ordet</li>
  <li><code>B</code> – radera till radens slut</li>
  <li><code>Y</code> – kopiera ordet</li>
  <li><code>§ °</code> – växla versaler/gemener för tecknet, ordet</li>
  <li><code>&amp;</code> – slå ihop med nästa rad</li>
  <li><code>J K</code> – flytta raden ner, upp</li>
  <li><code>L H</code> – öka, minska indraget</li>
  <li><code>.</code> – upprepa senaste redigering, sökning eller ersättning</li>
  <li><code>U</code> – återgå till sparad version i ett steg; <code>u</code> tar tillbaka ändringarna</li>
</ul>

<h3>Register</h3>
<ul>
  <li><code>0</code>–<code>9</code> – register: <code>0</code> senaste kopiering, <code>8</code> senaste radering, <code>9</code> systemets urklipp</li>
  <li><code>3p</code> <code>3P</code> – klistra in register 3 före, efter markören</li>
  <li><code>2 y</code> <code>5 2y</code> – kopiera 1, 5 rader till register 2</li>
  <li><code>3 x</code> <code>5 3x</code> – radera 1, 5 tecken till register 3</li>
  <li><code>V</code> – registervisaren: skriv en siffra eller välj en rad för att klistra in, <code>Esc</code> stänger</li>
  <li><code>2V</code> – spara urklippet i register 2</li>
  <li>Namngivet register – rör aldrig urklippet; en vanlig kopiering går till register 0 och urklippet</li>
  <li>Kopiering – den kopierade texten blinkar till</li>
</ul>

<h3>Markering</h3>
<ul>
  <li><code>v</code> – markeringsläge på, av; rörelser, piltangenter och sökningar utökar markeringen</li>
  <li><code>x y c C § ° p P</code> – verkar på markeringen, även en gjord med musen eller Shift; <code>p P</code> ersätter den</li>
  <li><code>( [ { " ' ` ´</code> – i markeringsläget: omslut markeringen, till exempel <code>(foo)</code></li>
  <li><code>=ny</code> <code>==ny</code> – i markeringsläget: ersätt bara inuti markeringen</li>
</ul>

<h3>Sök och ersätt</h3>
<ul>
  <li><code>/text</code> <code>Enter</code> – sök framåt</li>
  <li><code>\text</code> <code>Enter</code> – sök bakåt</li>
  <li><code>,regex</code> <code>Enter</code> – regex-sökning, visas som <code>~</code></li>
  <li><code>n N</code> – nästa, föregående träff; <code>3n</code> tre framåt; börjar om vid ändarna</li>
  <li><code>f F</code> – sök tecknet under markören framåt, bakåt</li>
  <li><code>* #</code> – sök ordet under markören framåt, bakåt</li>
  <li>Smart skiftläge – gemener hittar båda, en versal gör sökningen exakt; <code>f F * #</code> är alltid exakta</li>
  <li><code>=ny</code> <code>Enter</code> – stega genom träffarna i senaste sökningen: <code>Enter</code> eller <code>.</code> ersätter, <code>n N</code> hoppar över</li>
  <li><code>==ny</code> <code>Enter</code> – ersätt alla träffar på en gång</li>
  <li><code>=gammal ny</code> <code>==gammal ny</code> – samma sak för den exakta texten <code>gammal</code>; <code>'a b'</code> citerar mellanslag</li>
  <li><code>=</code> <code>==</code> <code>Enter</code> – ersätt aktuell träff, alla träffar, med nuvarande regel</li>
  <li><code>$1</code> <code>$&amp;</code> <code>$&lt;namn&gt;</code> – grupper i ersättningen efter en regex-sökning</li>
  <li><code>Backspace</code> <code>Esc</code> – redigera, avbryt kommandoraden; <code>Esc</code> döljer även träffarna</li>
</ul>

<h3>Textobjekt</h3>
<ul>
  <li><code>! " ' ` ´ ( [ {</code> – närmaste paret runt markören; <code>!</code> tar vilket par som helst, även <code>&lt; &gt;</code></li>
  <li><code>"y</code> – kopiera innehållet till register 0 och urklippet</li>
  <li><code>"x</code> – radera innehållet till register 8</li>
  <li><code>"c</code> – radera innehållet till register 8, sedan INSERT</li>
  <li><code>"p</code> – ersätt innehållet med urklippet</li>
  <li><code>"Y</code> <code>"X</code> – kopiera, radera inklusive avgränsarna</li>
  <li><code>" 3y</code> <code>( 3x</code> <code>{ 3p</code> <code>[ 3c</code> – använd bara register 3; inte med <code>!</code></li>
</ul>

<h3>Ankare, navigering och meny</h3>
<ul>
  <li><code>Z</code> <code>Alt+Z</code> – sätt ankaret</li>
  <li><code>z</code> <code>Alt+X</code> – hoppa till ankaret och tillbaka igen, även mellan filer</li>
  <li><code>3Z</code> <code>3z</code> – numrerade ankare 0–9</li>
  <li><code>Alt+Q</code> <code>Alt+E</code> – föregående, nästa stycke</li>
  <li><code>Alt+A</code> <code>Alt+D</code> – navigera bakåt, framåt</li>
  <li><code>Ctrl+F</code> – sökfältet</li>
  <li>Klick på statusfältet – meny: Registers, Command Stats, Toggle Search Highlight, Change Keybindings, Open KEYMAP</li>
</ul>

<h3>Inställningar</h3>
<ul>
  <li>Settings → Tools → sMiv – ge valfri NAV-tangent ett annat tecken; siffror och Space är fasta; Restore Defaults</li>
  <li>Settings → Keymap → sMiv – ändra Alt-kortkommandona och <code>Ctrl+F</code></li>
</ul>

<h3>Bra att veta</h3>
<ul>
  <li>IdeaVim – använd inte samtidigt; sMiv varnar och kan stänga av sig själv för sessionen</li>
  <li>Aktivera, inaktivera – PhpStorm behöver startas om</li>
  <li>Markörer – NAV-kommandon använder bara den primära markören</li>
  <li>Editorer – sMiv fungerar i kodeditorer, inte i konsoler, commit-meddelanden eller dialoger</li>
  <li>Ett läge – NAV och INSERT gäller alla editorer samtidigt</li>
</ul>
