package dev.proofjava.analysis.report;

/**
 * Human-readable, standalone HTML rendering of a {@link VerdictDocument}
 * (hard rule 7: same document, now three readers - {@link VerdictJsonWriter}
 * for machines, {@link TextRenderer} for a terminal, this for a browser).
 *
 * <p>D-80 replaced the earlier "every row printed as HTML server-side"
 * renderer with a data-embedded one: {@link ReportDataWriter} turns {@code
 * doc} into one presentation-shaped JSON object (Turkish-formatted numbers,
 * a friendly-name lookup for only the codes this exact report uses - hard
 * rule 5 keeps every raw code alongside its name, never in place of it),
 * and this class embeds that JSON in a {@code <script
 * type="application/json">} for {@link #SCRIPT} - static,
 * interpolation-free client-side JavaScript shipped alongside it - to read
 * and render into the DOM. D-81 replaced the earlier collapsible-sections
 * page with a fixed sidebar + scroll-spy + dashboard card grid (opens in
 * light mode by default - no {@code prefers-color-scheme} auto-dark on
 * first load); every card is built strictly from computed numbers, never a
 * generated judgment sentence (see D-81 for why). D-82 fixed the layout
 * bugs that survived that rewrite: no text is {@code white-space: nowrap}
 * without a way to break any more (findings and mutant ids used to paint
 * over each other and drag a horizontal scrollbar onto the whole page),
 * the sidebar's links carry group captions instead of a per-row dot that
 * read as an unchecked checkbox, and below 900px the sidebar becomes a
 * scrolling top strip rather than disappearing.
 *
 * <p><b>Security.</b> Every byte of {@code doc}-derived text reaches the
 * browser through exactly one path: JSON-encoded (Jackson escapes {@code "}
 * and control characters the way any correct JSON writer must), then passed
 * through {@link #jsonEscapeForScript(String)}, which additionally turns
 * {@code <}, {@code >}, {@code &}, U+2028 and U+2029 into {@code \\uXXXX}
 * escapes. Since a literal {@code <} can only ever occur inside an
 * already-quoted JSON string value (never as JSON's own structural syntax),
 * replacing it with an escape sequence is a no-op under {@code JSON.parse}
 * and removes every literal {@code <} from the page source - so the
 * sequence {@code </script} (case-insensitive, in any form) cannot occur
 * inside the embedded block, which is what actually terminates a
 * {@code <script>} element per the HTML parsing spec (HTML does not decode
 * entities inside script/style raw-text content, so entity-encoding would
 * not have worked here). {@code >}/{@code &}/U+2028/U+2029 are escaped too
 * as defense in depth. {@link #SCRIPT} itself never uses {@code innerHTML}
 * or {@code eval} on anything - only {@code textContent},
 * {@code setAttribute}, and {@code classList} - so nothing parsed back out
 * of the embedded JSON can re-enter the page as markup.
 *
 * <p>Self-contained by design: no external stylesheet, font, or script
 * reference - the CLI makes zero network calls (SECURITY-POLICY.md #5) and
 * a report opened offline must render identically to one opened online. The
 * declared font stacks fall back to system fonts when IBM Plex isn't
 * installed locally. {@link #SCRIPT} requires JavaScript to render anything
 * at all; a {@code <noscript>} block says so plainly rather than showing a
 * blank page.
 */
public final class HtmlRenderer {

    private HtmlRenderer() {
    }

    public static String render(VerdictDocument doc) {
        String dataJson = ReportDataWriter.write(doc);
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>proof-java report</title>\n");
        sb.append("<style>\n").append(CSS).append("\n</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<noscript><div class=\"noscript-warning\">This report is rendered with JavaScript - ")
            .append("it appears to be disabled in your browser, so the content stays empty.</div></noscript>\n");
        sb.append(BRAND_ICON_SVG).append('\n');
        sb.append("<div id=\"app\"></div>\n");
        sb.append("<script id=\"proof-data\" type=\"application/json\">")
            .append(jsonEscapeForScript(dataJson)).append("</script>\n");
        sb.append("<script>\n").append(SCRIPT).append("\n</script>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /** See the class javadoc's Security section - this is the report's one and only escaping boundary. */
    static String jsonEscapeForScript(String json) {
        StringBuilder out = new StringBuilder(json.length() + 16);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                case '\u2028' -> out.append("\\u2028");
                case '\u2029' -> out.append("\\u2029");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * The sidebar brand mark. "Inkbrush person reviewing proof" by Koboyo
     * (koboyo.com/icons), free for commercial use, no attribution required
     * (koboyo.com/icons/license) - kept here as a literal, since the whole
     * shell this belongs to (title, CSS, this markup) is static and authored
     * by the tool itself, never built from a document-derived value; the
     * report's one escaping boundary ({@link #jsonEscapeForScript}) is about
     * the embedded verdict data, not this. Held in a {@code <template>} so the
     * static shell carries it without the script needing an SVG string
     * literal of its own; the script only clones it.
     */
    private static final String BRAND_ICON_SVG = """
        <template id="brand-icon"><svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" \
        aria-label="A person reviewing a proof" viewBox="-0.0 -18.5 297.0 297.0">\
        <g transform="translate(0.000000,260.000000) scale(0.100000,-0.100000)"><path d="M1105 2410 c-166 -26 -290 -116 -356 -255 -58 -122 -61 -244 -9 -353
        26 -56 58 -96 43 -55 -3 10 10 0 30 -22 39 -43 46 -38 22 18 -8 20 -15 39 -15
        43 0 4 7 1 15 -6 13 -10 15 -9 15 11 0 13 -7 60 -14 103 -28 156 24 289 142
        365 158 101 377 75 500 -59 25 -27 82 -137 82 -157 0 -7 4 -13 9 -13 15 0 18
        -83 4 -131 -7 -26 -29 -73 -48 -105 -33 -54 -35 -64 -35 -141 l0 -83 -27 0
        c-34 -1 -58 -17 -67 -47 -4 -12 -15 -25 -25 -28 -10 -3 -31 -24 -46 -45 -34
        -50 -62 -58 -122 -35 -45 17 -113 28 -113 19 0 -3 12 -16 28 -31 44 -41 103
        -66 155 -67 47 0 50 2 122 78 70 72 77 77 106 71 19 -4 41 -2 54 5 22 12 23
        18 22 116 l-1 103 37 53 c42 61 67 126 67 173 0 37 -24 111 -43 135 -7 8 -19
        37 -25 64 -45 177 -289 311 -507 276z M1768 1718 c-8 -7 -44 -83 -78 -168 -34
        -85 -82 -204 -106 -265 -46 -112 -54 -163 -15 -87 12 24 28 45 35 48 7 3 57
        97 112 209 l99 205 53 0 c94 0 719 -82 768 -100 l22 -9 -23 -73 c-13 -40 -54
        -156 -91 -258 -37 -102 -84 -234 -106 -295 -21 -60 -42 -119 -47 -130 -5 -11
        -33 -73 -61 -137 -50 -112 -69 -146 -55 -95 11 37 -11 2 -72 -118 -30 -60 -59
        -115 -65 -122 -7 -8 -23 -8 -66 3 -31 7 -142 34 -247 59 -104 25 -238 52 -297
        61 -96 14 -110 14 -129 0 -21 -14 -21 -15 17 -31 143 -62 735 -240 769 -231
        13 3 37 23 54 44 30 38 44 72 146 352 29 80 62 168 73 196 12 29 19 54 16 57
        -6 6 76 189 88 198 10 7 75 158 144 334 30 77 61 152 69 167 18 32 19 62 3 76
        -25 20 -97 32 -389 67 -507 61 -590 67 -621 43z m693 -864 c-94 -207 -113
        -246 -117 -241 -2 2 35 87 83 190 48 103 88 185 90 183 2 -1 -23 -61 -56 -132z
        m-140 -301 c-12 -20 -14 -14 -5 12 4 9 9 14 11 11 3 -2 0 -13 -6 -23z M597
        1696 c-169 -70 -294 -215 -387 -450 -17 -44 -30 -91 -28 -105 3 -25 4 -25 27
        14 26 43 65 87 200 227 79 82 148 134 294 219 48 28 55 35 37 37 -21 3 -20 5
        11 24 l34 21 -25 9 c-14 5 -35 7 -48 5 -18 -3 -20 -2 -11 9 19 23 -38 17 -104
        -10z M916 1508 c17 -19 17 -21 2 -15 -12 4 -7 -9 17 -48 80 -124 95 -213 92
        -526 -2 -173 1 -219 12 -233 10 -12 17 -14 25 -6 5 5 17 10 27 10 30 0 239
        201 239 231 0 21 59 69 127 104 28 14 54 25 57 25 19 0 1 -65 -34 -118 -29
        -45 -35 -62 -24 -62 8 0 12 -4 9 -10 -10 -16 5 -12 41 11 18 11 47 39 64 61
        49 68 37 155 -27 179 -56 21 -198 -55 -248 -133 -16 -26 -33 -47 -38 -47 -4
        -1 -31 -17 -59 -36 -28 -20 -53 -33 -56 -30 -3 3 -6 85 -7 183 -2 148 -5 187
        -22 240 -25 74 -79 149 -149 203 -56 43 -78 51 -48 17z M2294 1319 c-50 -32
        -217 -171 -297 -248 -32 -31 -62 -57 -67 -59 -5 -2 -20 19 -34 45 -28 56 -40
        69 -78 78 -24 6 -28 3 -34 -20 -8 -34 53 -239 78 -262 35 -32 69 -6 298 221
        121 121 218 222 215 224 -2 3 -13 -1 -22 -9 -13 -9 -12 -6 3 11 33 39 34 45 6
        45 -14 0 -45 -12 -68 -26z M1436 747 c-3 -16 -11 -25 -16 -22 -5 3 -20 -16
        -34 -43 -37 -73 -136 -202 -197 -255 -80 -71 -188 -122 -258 -121 -120 1 -223
        90 -330 285 -22 42 -50 84 -62 94 -20 19 -20 19 -14 -26 18 -121 86 -299 116
        -299 5 0 15 -15 23 -34 15 -37 102 -96 142 -96 11 0 29 -7 40 -15 41 -31 164
        -10 277 46 88 45 231 190 281 287 39 75 60 169 46 206 -6 18 -8 17 -14 -7z"/>\
        </g></svg></template>
        """;

    private static final String CSS = """
        :root {
          --bg: #f4f4f2; --surf: #ffffff; --surf2: #faf9f7; --surf3: #eeece8;
          --ink: #191b1e; --ink2: #585d64; --ink3: #84888f;
          --line: #e0ded8; --line2: #cbc8c1;
          --good: #12855a; --good-dim: #dcf0e6;
          --bad: #bb4028; --bad-dim: #f8e3dd;
          --warn: #8e6410; --warn-dim: #f7ecd5;
          --info: #2f5f9e; --info-dim: #e4ecf7;
          --na: #8b8f96;
          --mono: "IBM Plex Mono", ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
          --sans: "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
          --serif: "IBM Plex Serif", Georgia, serif;
          color-scheme: light;
        }
        :root[data-theme="dark"] {
          --bg: #111214; --surf: #191a1e; --surf2: #202227; --surf3: #292c33;
          --ink: #e6e5e3; --ink2: #9ea3ab; --ink3: #6e737b;
          --line: #282b32; --line2: #363a43;
          --good: #35d98a; --good-dim: #1c3a2b;
          --bad: #e8614a; --bad-dim: #3a1e18;
          --warn: #dfae3d; --warn-dim: #332811;
          --info: #7fa9e6; --info-dim: #1a2635;
          --na: #7c8189;
          color-scheme: dark;
        }
        * { box-sizing: border-box; }
        body { margin: 0; background: var(--bg); color: var(--ink); font-family: var(--sans);
          line-height: 1.55; -webkit-font-smoothing: antialiased; }
        h1, h2, h3 { font-family: var(--serif); }
        code { font-family: var(--mono); font-size: 0.92em; }
        a { color: var(--good); text-decoration: none; }
        a:hover { text-decoration: underline; }
        input, button { font-family: inherit; }
        ::selection { background: var(--good-dim); }
        ::-webkit-scrollbar { width: 9px; height: 9px; }
        ::-webkit-scrollbar-track { background: var(--bg); }
        ::-webkit-scrollbar-thumb { background: var(--surf3); border-radius: 5px; }

        .noscript-warning { background: var(--warn-dim); color: var(--warn); padding: 1rem 1.25rem;
          text-align: center; font-weight: 600; }

        .side { position: fixed; left: 0; top: 0; bottom: 0; width: 236px; z-index: 40; background: var(--surf);
          border-right: 1px solid var(--line); display: flex; flex-direction: column; }
        .side-head { padding: 20px 20px 18px; border-bottom: 1px solid var(--line); display: flex;
          align-items: center; gap: 10px; }
        .side-mark { width: 30px; height: 30px; flex: none; color: var(--good); }
        .side-brand { display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
        .side-brand-name { font-size: 15px; font-weight: 680; letter-spacing: -0.01em; }
        .side-brand-sub { font-size: 11px; color: var(--ink3); font-family: var(--mono); white-space: nowrap;
          overflow: hidden; text-overflow: ellipsis; }
        .side-nav { flex: 1; padding: 6px 0 14px; overflow-y: auto; display: flex; flex-direction: column; gap: 1px; }
        .side-nav-group { padding: 15px 21px 5px 24px; font-family: var(--mono); font-size: 9.5px; font-weight: 700;
          letter-spacing: 0.16em; text-transform: uppercase; color: var(--ink3); }
        .side-nav-group:first-child { padding-top: 6px; }
        .side-nav a { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: center;
          padding: 7px 18px 7px 21px; border-left: 3px solid transparent; color: var(--ink2); font-size: 13px; }
        .side-nav a:hover { background: var(--surf2); color: var(--ink); text-decoration: none; }
        .side-nav a.active { border-left-color: var(--good); background: var(--surf2); color: var(--ink); font-weight: 600; }
        .side-nav-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .side-nav-count { font-family: var(--mono); font-size: 10.5px; color: var(--ink3); background: var(--surf2);
          padding: 1px 6px; border-radius: 99px; white-space: nowrap; }
        .side-nav a.active .side-nav-count { color: var(--good); background: var(--good-dim); }
        .side-foot { padding: 14px 18px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 9px; }
        .side-foot-btn { width: 100%; padding: 8px 12px; border-radius: 7px; border: 1px solid var(--line2);
          background: var(--surf2); color: var(--ink2); font-size: 12px; cursor: pointer; text-align: left; }
        .side-foot-btn:hover { border-color: var(--good); color: var(--ink); }
        .side-foot-meta { font-family: var(--mono); font-size: 10.5px; color: var(--ink3); line-height: 1.6; }

        .main { margin-left: 236px; min-height: 100vh; }
        .wrap { max-width: 1420px; width: 100%; margin: 0 auto; padding: 34px 40px 40px; }

        #summary .eyebrow { font-family: var(--mono); font-size: 10.5px; letter-spacing: 0.14em; font-weight: 700;
          color: var(--ink3); }
        #summary .summary-time { font-family: var(--mono); font-size: 11.5px; color: var(--ink3); margin-left: 14px; }
        .stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; margin-top: 18px; }
        .stat-tile { background: var(--surf); border: 1px solid var(--line); border-radius: 10px; padding: 14px 18px; }
        .stat-tile-label { font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink3); font-weight: 600; }
        .stat-tile-value { font-family: var(--mono); font-size: 22px; letter-spacing: -0.02em; margin-top: 6px; }
        .stat-tile-sub { font-size: 11.5px; color: var(--ink2); margin-top: 2px; }

        .grid { display: grid; grid-template-columns: repeat(12, 1fr); gap: 18px; margin-top: 26px; }
        .card { min-width: 0; background: var(--surf); border: 1px solid var(--line); border-radius: 12px;
          padding: 22px 26px; scroll-margin-top: 24px; }
        .card-accent-good { border-top: 2px solid var(--good); }
        .card-accent-bad { border-top: 2px solid var(--bad); }
        .card-accent-warn { border-top: 2px solid var(--warn); }
        .card-head { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; flex-wrap: wrap; }
        .card-title { font-size: 17px; font-weight: 640; letter-spacing: -0.01em; margin: 0; }
        .card-count { font-family: var(--mono); font-size: 13px; color: var(--ink3); }
        .card-sub { margin: 5px 0 0; font-size: 13px; color: var(--ink2); max-width: 74ch; }

        .col-4 { grid-column: span 4; } .col-5 { grid-column: span 5; } .col-7 { grid-column: span 7; }
        .col-8 { grid-column: span 8; } .col-12 { grid-column: span 12; }

        .coverage-body { display: flex; gap: 30px; align-items: center; flex-wrap: wrap; }
        .donut-wrap { position: relative; width: 154px; height: 154px; flex: none; }
        .donut { width: 100%; height: 100%; transform: rotate(-90deg); }
        .donut-track { fill: none; stroke: var(--surf3); stroke-width: 7; }
        .donut-fill { fill: none; stroke: var(--good); stroke-width: 7; stroke-linecap: round; }
        .donut-label { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center;
          justify-content: center; gap: 1px; }
        .donut-pct { font-family: var(--mono); font-size: 29px; letter-spacing: -0.03em; line-height: 1; }
        .donut-mode { font-family: var(--mono); font-size: 10px; letter-spacing: 0.08em; color: var(--ink3); }

        .metric-list { margin-top: 16px; display: flex; flex-direction: column; gap: 1px; }
        .metric-row { display: grid; grid-template-columns: 132px 56px minmax(60px, 1fr) auto; gap: 14px;
          align-items: center; padding: 9px 0; border-bottom: 1px solid var(--line); }
        .metric-row code { font-size: 12px; color: var(--ink2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .metric-row-pct { font-family: var(--mono); font-size: 14px; text-align: right; font-variant-numeric: tabular-nums; }
        .metric-row-bar { height: 5px; border-radius: 3px; background: var(--surf3); overflow: hidden; display: block; }
        .metric-row-bar-fill { display: block; height: 5px; background: var(--good); opacity: 0.8; }
        .metric-row-ratio { font-family: var(--mono); font-size: 11px; color: var(--ink3); white-space: nowrap; text-align: right; }
        .newcode-row { display: flex; gap: 12px; align-items: baseline; padding: 10px 0 0; }
        .newcode-row code { font-family: var(--mono); font-size: 12px; color: var(--ink2); }
        .unavailable { color: var(--na); font-style: italic; }

        .mut-summary { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
        .mut-badge { width: 38px; height: 38px; border-radius: 99px; display: flex; align-items: center;
          justify-content: center; font-family: var(--mono); font-size: 17px; flex: none; }
        .mut-badge-bad { background: var(--bad-dim); border: 1px solid var(--bad); color: var(--bad); }
        .mut-badge-good { background: var(--good-dim); border: 1px solid var(--good); color: var(--good); }
        .mut-badge-none { background: var(--surf3); border: 1px solid var(--line2); color: var(--na); }
        .mut-flag { font-family: var(--mono); font-size: 10px; font-weight: 700; letter-spacing: 0.07em;
          padding: 4px 8px; border-radius: 5px; white-space: nowrap; }
        .mut-flag-bad { background: var(--bad-dim); border: 1px solid var(--bad); color: var(--bad); }
        .mut-flag-good { background: var(--good-dim); border: 1px solid var(--good); color: var(--good); }
        .mut-flag-none { background: var(--surf3); border: 1px solid var(--line2); color: var(--na); }
        .mut-score { font-family: var(--mono); font-size: 34px; letter-spacing: -0.03em; line-height: 1; margin-top: 22px; }
        .mut-score-of { font-size: 19px; color: var(--ink3); }
        .mut-headline { font-size: 16px; font-weight: 640; margin: 8px 0 0; }
        .mut-headline-bad { color: var(--bad); } .mut-headline-good { color: var(--good); }
        .mut-note { margin: 6px 0 0; font-size: 13px; color: var(--ink2); }
        .mut-callout { margin-top: 18px; padding: 11px 13px; border-radius: 8px; background: var(--surf2);
          border: 1px solid var(--line); font-family: var(--mono); font-size: 11.5px; display: flex;
          flex-direction: column; gap: 5px; }
        .mut-callout-code { color: var(--ink); overflow-wrap: anywhere; }
        .mut-callout-meta { color: var(--ink3); overflow-wrap: anywhere; }
        .mut-more-link { margin-top: 14px; font-size: 12.5px; color: var(--ink2); display: inline-block; }
        .mut-toolbar { display: flex; gap: 10px; align-items: center; margin: 14px 0 0; flex-wrap: wrap; }
        .mut-toolbar input[type=text] { flex: 1 1 220px; padding: 7px 11px; border: 1px solid var(--line2);
          border-radius: 8px; background: var(--surf2); color: var(--ink); font-size: 12.5px; }
        .mut-toolbar label { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--ink2); }
        details.mutation-class { background: var(--surf2); border: 1px solid var(--line); border-radius: 8px;
          padding: 0.5rem 0.8rem; margin: 10px 0 0; }
        details.mutation-class > summary { cursor: pointer; font-weight: 600; font-size: 13px; overflow-wrap: anywhere; }
        details.mutation-class h4 { overflow-wrap: anywhere; }
        table.mutants { width: 100%; }
        table.mutants td { overflow-wrap: anywhere; vertical-align: top; }
        table.mutants td:nth-child(3) { font-weight: 600; white-space: nowrap; }
        table.mutants td:nth-child(4) { color: var(--ink2); }
        tr.status-KILLED td:nth-child(3) { color: var(--good); }
        tr.status-SURVIVED td:nth-child(3) { color: var(--bad); }

        table { width: max-content; min-width: 100%; border-collapse: collapse; margin: 0; background: var(--surf); }
        th, td { text-align: left; padding: 0.45rem 0.65rem; border-bottom: 1px solid var(--line); font-size: 0.86em; }
        th { color: var(--ink3); font-weight: 600; font-size: 0.75em; text-transform: uppercase; white-space: nowrap; }
        td code, th code { white-space: nowrap; }
        .table-wrap { overflow-x: auto; margin: 10px 0 0; border: 1px solid var(--line); border-radius: 8px; padding: 0.4rem; }
        .table-wrap table { margin: 0; }

        .pill-row { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
        .pill { display: inline-flex; align-items: center; gap: 9px; padding: 7px 13px; border-radius: 99px;
          border: 1px dashed var(--line2); background: var(--surf2); font-size: 12px; color: var(--ink2); cursor: default; }
        .pill code { font-family: var(--mono); font-size: 10.5px; color: var(--ink3); }
        .pill-n { font-family: var(--mono); font-size: 11.5px; color: var(--good); font-weight: 600; }
        .pill-n-nonzero { color: var(--warn); }
        button.pill { cursor: pointer; border-style: solid; font: inherit; }
        button.pill:hover { border-color: var(--ink3); }
        button.pill[data-on="1"] { border-color: var(--good); background: var(--good-dim); color: var(--good); }
        .pill-clear { display: inline-flex; align-items: center; padding: 7px 13px; border-radius: 99px;
          border: 1px dashed var(--line2); background: none; font: inherit; font-size: 12px; color: var(--ink3);
          cursor: pointer; }
        .pill-clear:hover { color: var(--ink); border-color: var(--ink3); }

        .finding-group { border: 1px solid var(--line); border-radius: 8px; background: var(--surf2);
          padding: 0.6rem 0.8rem; margin: 12px 0 0; }
        .finding-group > summary { cursor: pointer; display: flex; align-items: baseline; gap: 0.5rem; flex-wrap: wrap;
          font-weight: 600; font-size: 13.5px; }
        .finding-group-count { color: var(--ink3); font-weight: 400; font-size: 0.85em; margin-left: auto; }
        .finding-group-desc { color: var(--ink2); font-size: 0.88em; margin: 0.5rem 0 0.25rem; }
        .finding-group-action { font-size: 0.88em; margin: 0 0 0.5rem; }
        .finding-rows { border-top: 1px solid var(--line); margin-top: 0.4rem; }
        .finding-row { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.7rem;
          align-items: baseline; padding: 0.5rem 0; border-bottom: 1px dotted var(--line); font-size: 0.85em; }
        .finding-row:last-child { border-bottom: none; }
        .finding-body { min-width: 0; }
        .finding-ident { display: flex; flex-wrap: wrap; align-items: baseline; gap: 2px 14px; min-width: 0; }
        .finding-loc { color: var(--ink); overflow-wrap: anywhere; }
        .finding-method { color: var(--ink3); overflow-wrap: anywhere; }
        .finding-message { color: var(--ink2); margin: 3px 0 0; overflow-wrap: anywhere; }
        .sev-badge, .conf-badge { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.72em; font-weight: 600; }
        .sev-WARNING { background: var(--warn-dim); color: var(--warn); }
        .sev-INFO { background: var(--info-dim); color: var(--info); }
        .conf-HIGH { background: var(--bad-dim); color: var(--bad); }
        .conf-MEDIUM { background: var(--warn-dim); color: var(--warn); }
        .conf-LOW, .conf-INCONCLUSIVE { background: var(--surf3); color: var(--ink3); }

        .file-toolbar { display: flex; gap: 9px; align-items: center; flex-wrap: wrap; margin-top: 16px; }
        .file-toggle { display: inline-flex; border: 1px solid var(--line2); border-radius: 8px; overflow: hidden; }
        .file-toggle button { padding: 7px 13px; font-size: 12px; border: 0; cursor: pointer; background: var(--surf2);
          color: var(--ink2); }
        .file-toggle button + button { border-left: 1px solid var(--line2); }
        .file-toggle button[data-on="1"] { background: var(--good-dim); color: var(--good); }
        .file-toolbar input[type=text] { width: 180px; padding: 7px 11px; border: 1px solid var(--line2); border-radius: 8px;
          background: var(--surf2); color: var(--ink); font-size: 12px; }
        .file-band-row { padding: 12px 0; margin-top: 8px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line);
          display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }
        .file-band-eyebrow { font-family: var(--mono); font-size: 10.5px; letter-spacing: 0.1em; color: var(--ink3); font-weight: 700; }
        .file-band-actions { display: flex; gap: 10px; align-items: center; margin-left: auto; flex-wrap: wrap; }
        .file-sort-btn, .file-reset-btn { padding: 5px 11px; border-radius: 7px; border: 1px solid var(--line2);
          background: var(--surf); color: var(--ink2); font-size: 12px; cursor: pointer; white-space: nowrap; }
        .file-sort-btn:hover { border-color: var(--good); color: var(--ink); }
        .file-reset-btn { border-color: transparent; background: transparent; }
        .file-reset-btn:hover { color: var(--ink); }
        .file-count { font-family: var(--mono); font-size: 11px; color: var(--ink3); white-space: nowrap; }
        .file-rows { margin-top: 4px; }
        .file-row { display: grid; grid-template-columns: minmax(0, 1fr) 150px 58px; gap: 16px; align-items: center;
          padding: 8px 0; border-bottom: 1px solid var(--line); }
        .file-row-zero { background: var(--bad-dim); margin: 0 -10px; padding: 8px 10px; border-radius: 6px; }
        .file-row-name-wrap { min-width: 0; display: flex; gap: 10px; align-items: baseline; overflow: hidden; }
        .file-row-name { font-family: var(--mono); font-size: 12.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .file-row-sub { font-family: var(--mono); font-size: 10.5px; color: var(--ink3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .file-row-bar { height: 5px; border-radius: 3px; background: var(--surf3); overflow: hidden; display: block; }
        .file-row-bar-fill { display: block; height: 5px; }
        .file-row-pct { font-family: var(--mono); font-size: 12.5px; text-align: right; font-variant-numeric: tabular-nums; }
        .file-legend { padding: 12px 0 0; display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap;
          font-size: 11.5px; color: var(--ink3); }
        .file-legend code { font-family: var(--mono); }

        .kv-grid { margin-top: 14px; display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 7px 18px; font-size: 12.5px; }
        .kv-grid dt { color: var(--ink3); grid-column: 1; }
        .kv-grid dd { margin: 0; grid-column: 2; }
        .kv-grid code { font-family: var(--mono); }
        .empty-block { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--line); }
        .empty-block-eyebrow { font-family: var(--mono); font-size: 10.5px; letter-spacing: 0.1em; color: var(--ink3); font-weight: 700; }
        .empty-row { display: flex; gap: 12px; align-items: baseline; padding: 6px 0; border-bottom: 1px dotted var(--line); font-size: 12.5px; }
        .empty-row-name { color: var(--ink2); }
        .empty-row-why { margin-left: auto; font-family: var(--mono); font-size: 11px; color: var(--ink3); white-space: nowrap; }
        .empty-block-note { margin: 10px 0 0; font-size: 11.5px; color: var(--ink3); }

        .reason-list { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
        .reason-list li { border-left: 2px solid var(--line2); padding-left: 11px; font-size: 12.5px; color: var(--ink2); }
        .reason-list code { color: var(--ink); }

        .search-hidden { display: none !important; }
        .deep-link-highlight { outline: 2px solid var(--good); outline-offset: 2px; border-radius: 4px; }
        .footer-strip { margin: 30px 0 0; padding: 14px 0 4px; border-top: 1px solid var(--line); display: flex;
          justify-content: space-between; gap: 16px; flex-wrap: wrap; font-family: var(--mono); font-size: 11px; color: var(--ink3); }

        @media print {
          .side, .noscript-warning, .file-toolbar, .file-band-row, .mut-toolbar { display: none !important; }
          .main { margin-left: 0 !important; }
          body { background: #fff; }
        }
        @media (max-width: 900px) {
          .side { position: static; inset: auto; width: auto; border-right: 0; border-bottom: 1px solid var(--line); }
          .side-head { border-bottom: 0; padding: 16px 16px 10px; }
          .side-nav { flex: none; flex-direction: row; align-items: center; gap: 4px;
            overflow-x: auto; overflow-y: hidden; padding: 0 14px 10px; }
          .side-nav-group { display: none; }
          .side-nav a { flex: none; grid-template-columns: auto auto; white-space: nowrap; padding: 6px 10px;
            border-left: 0; border-bottom: 2px solid transparent; border-radius: 6px 6px 0 0; }
          .side-nav a.active { border-left: 0; border-bottom-color: var(--good); }
          .side-foot { flex-direction: row; flex-wrap: wrap; align-items: center; gap: 8px 16px; padding: 10px 14px; }
          .side-foot-btn { width: auto; }
          .side-foot-meta { display: none; }
          .main { margin-left: 0 !important; }
          .wrap { padding: 22px 18px 32px; }
          .grid { grid-template-columns: 1fr; }
          .grid > * { grid-column: auto !important; }
          .metric-row { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 10px; }
          .metric-row code { flex: 1 1 auto; }
          .metric-row-bar { flex: 1 1 100%; order: 9; }
          .file-row { grid-template-columns: minmax(0, 1fr) 90px 52px; gap: 10px; }
        }
        """;

    /**
     * Static source text, no interpolation - see the class javadoc's
     * Security section. Reads only the escaped JSON in {@code
     * #proof-data} (never {@code innerHTML}/{@code eval}) and builds
     * the DOM with {@code createElement}/{@code textContent}/{@code
     * setAttribute} only.
     */
    private static final String SCRIPT = """
        (function () {
          'use strict';
          var DATA = JSON.parse(document.getElementById('proof-data').textContent);
          var PRINT_STATE = [];

          // The first coverage mode is named after the engine that produced
          // the document - jacoco-line here, coverage-line from proof-python
          // (D-99) - and render-html accepts either (D-78). DATA.meta.engineMode
          // says which, so nothing below hardcodes one engine's spelling.
          function engineLineMetric(metrics) {
            var list = metrics || DATA.coverage.overall;
            if (!list || !list.filter) { return undefined; }
            return list.filter(function (m) { return m.mode === DATA.meta.engineMode; })[0];
          }

          function el(tag, attrs, children) {
            var e = document.createElement(tag);
            if (attrs) {
              for (var k in attrs) {
                if (!Object.prototype.hasOwnProperty.call(attrs, k)) { continue; }
                var v = attrs[k];
                if (v === null || v === undefined) { continue; }
                if (k === 'class') { e.className = v; }
                else if (k === 'text') { e.textContent = v; }
                else if (k.indexOf('on') === 0 && typeof v === 'function') { e.addEventListener(k.slice(2), v); }
                else { e.setAttribute(k, v); }
              }
            }
            (children || []).forEach(function (c) {
              if (c === null || c === undefined) { return; }
              e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
            });
            return e;
          }
          function svgEl(tag, attrs) {
            var e = document.createElementNS('http://www.w3.org/2000/svg', tag);
            for (var k in attrs) { e.setAttribute(k, attrs[k]); }
            return e;
          }
          function txt(s) { return document.createTextNode(s === null || s === undefined ? '' : String(s)); }
          function fmtInt(n) { try { return n.toLocaleString('en-US'); } catch (e) { return String(n); } }
          // D-86: killingTests holds indexes into DATA.testIds - the ids are
          // written once for the whole report instead of once per mutant.
          function killers(m) {
            var ids = DATA.testIds || [];
            return (m.killingTests || []).map(function (i) { return ids[i]; }).filter(Boolean);
          }
          // A generated suite can kill one mutant with dozens of tests. The
          // question a reader has is which test killed it, and a few answer it -
          // the full list is in the verdict JSON.
          var KILLERS_SHOWN = 3;
          function killersSummary(m) {
            var all = killers(m);
            if (!all.length) { return '\u2014'; }
            if (all.length <= KILLERS_SHOWN) { return all.join(', '); }
            return all.slice(0, KILLERS_SHOWN).join(', ') + ' +' + (all.length - KILLERS_SHOWN) + ' more';
          }
          function debounce(fn, ms) {
            var t = null;
            return function () {
              var args = arguments;
              clearTimeout(t);
              t = setTimeout(function () { fn.apply(null, args); }, ms);
            };
          }

          function label(code) {
            var l = DATA.labels[code];
            return l ? l : { name: code, description: '' };
          }
          function codeBadge(code) {
            var l = label(code);
            var span = el('span', { title: l.description || null });
            span.appendChild(txt(l.name + ' '));
            span.appendChild(el('code', { text: code }));
            return span;
          }
          function tone(pct) {
            if (pct === null) { return 'var(--na)'; }
            if (pct < 70) { return 'var(--bad)'; }
            if (pct < 90) { return 'var(--warn)'; }
            return 'var(--good)';
          }

          // ---------- Coverage card ----------
          function donut(pct) {
            var r = 42, c = 2 * Math.PI * r;
            var offset = pct === null ? c : c * (1 - pct / 100);
            var wrap = el('div', { class: 'donut-wrap' });
            var svg = svgEl('svg', { viewBox: '0 0 100 100', class: 'donut' });
            svg.appendChild(svgEl('circle', { cx: 50, cy: 50, r: r, class: 'donut-track' }));
            svg.appendChild(svgEl('circle', { cx: 50, cy: 50, r: r, class: 'donut-fill',
              'stroke-dasharray': c.toFixed(1), 'stroke-dashoffset': offset.toFixed(1) }));
            wrap.appendChild(svg);
            var overlay = el('div', { class: 'donut-label' });
            overlay.appendChild(el('span', { class: 'donut-pct', text: pct === null ? 'n/a' : pct + '%' }));
            overlay.appendChild(el('span', { class: 'donut-mode', text: 'JACOCO-LINE' }));
            wrap.appendChild(overlay);
            return wrap;
          }
          function metricRow(m) {
            var row = el('div', { class: 'metric-row' });
            row.appendChild(codeBadge(m.mode));
            row.appendChild(el('span', { class: 'metric-row-pct', text: m.pctText }));
            var bar = el('span', { class: 'metric-row-bar' });
            bar.appendChild(el('span', { class: 'metric-row-bar-fill', style: m.pct === null ? 'width:0' : ('width:' + m.pct + '%') }));
            row.appendChild(bar);
            row.appendChild(el('span', { class: 'metric-row-ratio', text: m.numeratorText + ' / ' + m.denominatorText }));
            return row;
          }
          function buildCoverageCard() {
            var card = el('section', { id: 'coverage', class: 'card col-8 card-accent-good' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: 'Overall coverage' }));
            card.appendChild(head);
            card.appendChild(el('p', { class: 'card-sub', text: 'Three counts of the same run. No percentage is shown anywhere without its numerator and denominator.' }));
            var body = el('div', { class: 'coverage-body' });
            var jacoco = engineLineMetric();
            body.appendChild(donut(jacoco ? jacoco.pct : null));
            var right = el('div', { style: 'flex:1 1 320px;min-width:0' });
            var list = el('div', { class: 'metric-list' });
            DATA.coverage.overall.forEach(function (m) { list.appendChild(metricRow(m)); });
            right.appendChild(list);
            var nc = el('div', { class: 'newcode-row' });
            nc.appendChild(el('code', { text: 'new code' }));
            if (DATA.coverage.newCode.available) {
              var m0 = DATA.coverage.newCode.metrics[0];
              nc.appendChild(el('span', { text: m0.pctText + ' (' + m0.numeratorText + ' / ' + m0.denominatorText + ')' }));
            } else {
              var span = el('span');
              span.appendChild(el('span', { class: 'unavailable', text: 'not available' }));
              span.appendChild(txt(' \\u2014 '));
              span.appendChild(codeBadge(DATA.coverage.newCode.unavailableStatus));
            nc.appendChild(span);
            }
            right.appendChild(nc);
            body.appendChild(right);
            card.appendChild(body);
            return card;
          }

          // ---------- Mutation + mutant detail ----------
          var CONCERN_RANK = { SURVIVED: 0, TIMED_OUT: 1, RUN_ERROR: 1, MEMORY_ERROR: 1, NON_VIABLE: 1, NOT_STARTED: 1, STARTED: 1, NO_COVERAGE: 2 };
          function findConcernMutant() {
            if (!DATA.mutation) { return null; }
            var best = null;
            DATA.mutation.modules.forEach(function (mod) {
              mod.classes.forEach(function (cls) {
                cls.methods.forEach(function (method) {
                  method.mutants.forEach(function (m) {
                    var rank = CONCERN_RANK[m.status];
                    if (rank === undefined) { return; }
                    if (!best || rank < best.rank) {
                      best = { rank: rank, moduleId: mod.moduleId, className: cls.className, methodName: method.methodName,
                        signatureShort: method.signatureShort, firstLine: method.firstLine, lastLine: method.lastLine,
                        mutator: m.mutator, mutatorShort: m.mutatorShort, line: m.line, status: m.status, killingTests: killers(m) };
                    }
                  });
                });
              });
            });
            return best;
          }
          function mutationTotals() {
            var killed = DATA.mutation.totalsByStatus.KILLED || 0;
            var all = 0;
            Object.keys(DATA.mutation.totalsByStatus).forEach(function (k) { all += DATA.mutation.totalsByStatus[k]; });
            return { killed: killed, all: all };
          }
          function buildMutationCard(concern) {
            var t = mutationTotals();
            var state = t.all === 0 ? 'none' : (t.killed === t.all ? 'good' : 'bad');
            var accentClass = state === 'good' ? 'card-accent-good' : (state === 'bad' ? 'card-accent-bad' : '');
            var card = el('section', { id: 'mutation', class: 'card col-4 ' + accentClass });
            var summary = el('div', { class: 'mut-summary' });
            var badgeGlyph = state === 'good' ? '\\u2713' : (state === 'bad' ? '!' : '\\u2014');
            var badgeClass = state === 'good' ? 'mut-badge-good' : (state === 'bad' ? 'mut-badge-bad' : 'mut-badge-none');
            summary.appendChild(el('span', { class: 'mut-badge ' + badgeClass, text: badgeGlyph }));
            var flagText = state === 'good' ? 'CLEAN' : (state === 'bad' ? 'ATTENTION' : 'NO DATA');
            var flagClass = state === 'good' ? 'mut-flag-good' : (state === 'bad' ? 'mut-flag-bad' : 'mut-flag-none');
            summary.appendChild(el('span', { class: 'mut-flag ' + flagClass, text: flagText }));
            card.appendChild(summary);
            var scoreLine = el('div', { class: 'mut-score' });
            scoreLine.appendChild(txt(t.killed));
            scoreLine.appendChild(el('span', { class: 'mut-score-of', text: ' / ' + t.all }));
            card.appendChild(scoreLine);
            var headlineClass = state === 'good' ? 'mut-headline-good' : (state === 'bad' ? 'mut-headline-bad' : '');
            card.appendChild(el('h2', { class: 'mut-headline ' + headlineClass,
              text: state === 'none' ? 'No mutants generated' : (state === 'bad' ? 'Some mutants survived' : 'All mutants killed') }));
            var noteText = t.all === 0
              ? 'No mutants were generated in this run.'
              : (t.killed + ' / ' + t.all + ' mutants killed by the tests.');
            card.appendChild(el('p', { class: 'mut-note', text: noteText }));
            if (concern) {
              var callout = el('div', { class: 'mut-callout' });
              callout.appendChild(el('span', { class: 'mut-callout-code', text: concern.className + '#' + concern.signatureShort }));
              var killLabel = concern.killingTests.length ? (concern.killingTests.length + ' killing tests') : 'no killing test';
              callout.appendChild(el('span', { class: 'mut-callout-meta', title: concern.mutator, text: (concern.mutatorShort || concern.mutator) + ' \\u00b7 line ' + concern.line + ' \\u00b7 ' + killLabel }));
              card.appendChild(callout);
              var more = el('a', { href: '#mutant-detail', class: 'mut-more-link', text: 'Go to mutant detail \\u2192' });
              card.appendChild(more);
            }
            return card;
          }
          function buildMutantDetailCard(concern, otherCount) {
            var card = el('section', { id: 'mutant-detail', class: 'card col-12' });
            var head = el('div', { style: 'margin:-22px -26px 18px;padding:18px 26px;background:var(--bad-dim);border-bottom:1px solid var(--line);border-radius:12px 12px 0 0;display:flex;gap:14px;align-items:baseline;flex-wrap:wrap' });
            head.appendChild(el('span', { class: 'mut-flag mut-flag-bad', text: label(concern.status).name.toUpperCase() }));
            head.appendChild(el('span', { style: 'font-size:14px;font-weight:600', text: label(concern.status).name + ' mutant' }));
            head.appendChild(el('span', { style: 'font-size:12px;color:var(--ink2);margin-left:auto', text: 'L3 \\u00b7 ' + concern.moduleId }));
            card.appendChild(head);
            var top = el('div', { style: 'display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap;align-items:baseline' });
            top.appendChild(el('span', { style: 'font-family:var(--mono);font-size:13px;font-weight:600;overflow-wrap:anywhere',
              text: concern.className + '#' + concern.signatureShort }));
            top.appendChild(el('span', { style: 'font-family:var(--mono);font-size:11.5px;color:var(--ink3);white-space:nowrap',
              text: 'line ' + concern.firstLine + '-' + concern.lastLine }));
            card.appendChild(top);
            var mutRow = el('div', { style: 'margin-top:12px;display:flex;gap:14px;align-items:baseline;flex-wrap:wrap;padding:10px 13px;border-radius:8px;background:var(--surf2);border:1px solid var(--line);font-family:var(--mono);font-size:12px' });
            mutRow.appendChild(el('span', { style: 'color:var(--bad);font-weight:600;min-width:0;overflow-wrap:anywhere', title: concern.mutator, text: concern.mutatorShort || concern.mutator }));
            mutRow.appendChild(el('span', { style: 'color:var(--ink3);margin-left:auto;min-width:0;overflow-wrap:anywhere',
              text: concern.killingTests.length ? concern.killingTests.join(', ') : 'no killing test' }));
            card.appendChild(mutRow);
            if (otherCount > 0) {
              card.appendChild(el('p', { style: 'margin:14px 0 0;font-size:12.5px;color:var(--ink2)',
                text: 'This run has ' + (otherCount + 1) + ' mutants worth reviewing; see the Mutation card for the rest.' }));
            }
            return card;
          }
          function mutantSearchText(className, methodName, m) {
            return (className + ' ' + methodName + ' ' + m.mutator + ' ' + m.status + ' ' + killers(m).join(' ')).toLowerCase();
          }
          function buildMutationDetailCard() {
            var card = el('section', { id: 'all-mutants', class: 'card col-12' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: 'Mutation evidence \\u2014 all mutants' }));
            var t = mutationTotals();
            head.appendChild(el('span', { class: 'card-count', text: t.killed + ' / ' + t.all + ' killed' }));
            card.appendChild(head);
            var toolbar = el('div', { class: 'mut-toolbar' });
            var search = el('input', { type: 'text', id: 'mut-search', placeholder: 'filter by class, method, mutator or killing test' });
            toolbar.appendChild(search);
            var survivedOnly = el('input', { type: 'checkbox', id: 'mut-survived-only' });
            toolbar.appendChild(el('label', {}, [survivedOnly, ' SURVIVED only']));
            card.appendChild(toolbar);

            function applyMutFilter() {
              var q = search.value.trim().toLowerCase();
              var only = survivedOnly.checked;
              card.querySelectorAll('.mutant-row').forEach(function (row) {
                var textMatch = q === '' || row.dataset.search.indexOf(q) !== -1;
                var statusMatch = !only || row.dataset.status === 'SURVIVED';
                row.classList.toggle('search-hidden', !(textMatch && statusMatch));
              });
              card.querySelectorAll('details.mutation-class').forEach(function (grp) {
                var rows = grp.querySelectorAll('.mutant-row');
                var anyVisible = false;
                rows.forEach(function (r) { if (!r.classList.contains('search-hidden')) { anyVisible = true; } });
                grp.style.display = anyVisible ? '' : 'none';
                if (q !== '' || only) { grp.open = anyVisible; }
              });
            }
            search.addEventListener('input', debounce(applyMutFilter, 60));
            survivedOnly.addEventListener('change', applyMutFilter);

            DATA.mutation.modules.forEach(function (mod) {
              mod.classes.forEach(function (cls) {
                var counts = Object.keys(cls.countsByStatus).sort().map(function (s) { return label(s).name + ' ' + cls.countsByStatus[s]; }).join(' \\u00b7 ');
                var details = el('details', { class: 'mutation-class' });
                details.appendChild(el('summary', {}, [el('code', { text: cls.className }), ' \\u00b7 ' + counts]));
                cls.methods.forEach(function (method) {
                  details.appendChild(el('h4', { style: 'margin:10px 0 4px;font-size:12.5px' }, [
                    el('code', { title: method.signatureFull, text: cls.className + '#' + method.signatureShort }),
                    txt(' (line ' + method.firstLine + '-' + method.lastLine + ')')
                  ]));
                  var tableWrap = el('div', { class: 'table-wrap' });
                  var table = el('table', { class: 'mutants' });
                  table.appendChild(el('thead', {}, [el('tr', {}, [
                    el('th', { text: 'mutator' }), el('th', { text: 'line' }), el('th', { text: 'status' }), el('th', { text: 'killing tests' })
                  ])]));
                  var tbody = el('tbody');
                  method.mutants.forEach(function (m) {
                    var tr = el('tr', { class: 'mutant-row status-' + m.status, 'data-status': m.status,
                      'data-search': mutantSearchText(cls.className, method.methodName, m) });
                    tr.appendChild(el('td', { title: m.mutator, text: m.mutatorShort || m.mutator }));
                    tr.appendChild(el('td', { text: String(m.line) }));
                    tr.appendChild(el('td', { text: label(m.status).name }));
                    tr.appendChild(el('td', { title: killers(m).join(', '), text: killersSummary(m) }));
                    tbody.appendChild(tr);
                  });
                  table.appendChild(tbody);
                  tableWrap.appendChild(table);
                  details.appendChild(tableWrap);
                });
                card.appendChild(details);
              });
            });
            return card;
          }

          // ---------- Findings card ----------
          function buildFindingsCard() {
            var f = DATA.findings;
            var groups = {};
            f.items.forEach(function (it) { (groups[it.rule] = groups[it.rule] || []).push(it); });

            var card = el('section', { id: 'findings', class: 'card col-12' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title' }, ['Test findings ', el('span', { style: 'font-family:var(--mono);font-size:13px;color:var(--good)', text: String(f.items.length) })]));
            head.appendChild(el('span', { class: 'card-count', text: 'scope: ' + f.scopeLabel + ' \\u00b7 ' + DATA.ruleIds.length + ' rules' }));
            card.appendChild(head);
            card.appendChild(el('p', { class: 'card-sub',
              text: f.items.length === 0
                ? DATA.ruleIds.length + ' rules ran, none of them triggered. The rule list stays visible at zero too \\u2014 which checks actually ran matters more when the result is empty.'
                : f.items.length + ' findings, ' + Object.keys(groups).length + ' rules.' }));

            // Rule pills double as a filter (multi-select: click adds a rule to
            // the filter, click again removes it; empty selection means "show
            // all", same as no search text). A pill for a rule with zero
            // findings is still clickable - selecting only zero-count rules is
            // a valid, if empty, filter state, not a disabled one.
            var selectedRules = new Set();
            var pillButtons = {};
            var pillRow = el('div', { class: 'pill-row' });
            DATA.ruleIds.forEach(function (rule) {
              var count = (groups[rule] || []).length;
              var l = label(rule);
              var pill = el('button', { type: 'button', class: 'pill', title: l.description, 'data-on': '0' });
              pill.appendChild(txt(l.name + ' '));
              pill.appendChild(el('code', { text: rule }));
              pill.appendChild(el('span', { class: 'pill-n' + (count > 0 ? ' pill-n-nonzero' : ''), text: String(count) }));
              pillButtons[rule] = pill;
              pillRow.appendChild(pill);
            });
            var clearPillsBtn = el('button', { type: 'button', class: 'pill-clear', text: 'Clear filter', hidden: '' });
            pillRow.appendChild(clearPillsBtn);
            card.appendChild(pillRow);

            if (f.items.length > 0) {
              var toolbar = el('div', { class: 'mut-toolbar' });
              var search = el('input', { type: 'text', id: 'finding-search', placeholder: 'filter by rule, file, test or message' });
              toolbar.appendChild(search);
              card.appendChild(toolbar);
              var noMatch = el('p', { class: 'card-sub', text: 'No findings match the selected filters.', hidden: '' });

              var list = el('div', { class: 'finding-groups' });
              Object.keys(groups).sort().forEach(function (rule) {
                var items = groups[rule];
                var l = label(rule);
                var group = el('details', { class: 'finding-group', 'data-rule': rule, open: '' });
                group.open = true;
                var gsum = el('summary');
                gsum.appendChild(txt(l.name + ' '));
                gsum.appendChild(el('code', { text: rule }));
                gsum.appendChild(el('span', { class: 'sev-badge sev-' + items[0].severity, text: label(items[0].severity).name }));
                gsum.appendChild(el('span', { class: 'finding-group-count', text: items.length + ' finding(s)' }));
                group.appendChild(gsum);
                group.appendChild(el('p', { class: 'finding-group-desc', text: l.description }));
                var action = el('p', { class: 'finding-group-action' });
                action.appendChild(el('strong', { text: 'Suggested: ' }));
                action.appendChild(txt(items[0].suggestedAction));
                group.appendChild(action);
                var rows = el('div', { class: 'finding-rows' });
                items.forEach(function (it) {
                  var row = el('div', { class: 'finding-row', 'data-search': it.search, id: it.fingerprint ? ('f-' + it.fingerprint) : null });
                  row.appendChild(el('span', { class: 'conf-badge conf-' + it.confidence, text: label(it.confidence).name }));
                  var ident = el('div', { class: 'finding-ident' });
                  ident.appendChild(el('code', { class: 'finding-loc',
                    text: it.path + ':' + it.startLine + (it.endLine !== it.startLine ? ('-' + it.endLine) : '') }));
                  ident.appendChild(el('code', { class: 'finding-method', text: it.anchorMethod || '\\u2014' }));
                  var body = el('div', { class: 'finding-body' });
                  body.appendChild(ident);
                  body.appendChild(el('p', { class: 'finding-message', text: it.message }));
                  row.appendChild(body);
                  rows.appendChild(row);
                });
                group.appendChild(rows);
                list.appendChild(group);
              });
              card.appendChild(list);
              card.appendChild(noMatch);

              function applyFindingsFilter() {
                var q = search.value.trim().toLowerCase();
                var anyGroupVisible = false;
                card.querySelectorAll('details.finding-group').forEach(function (grp) {
                  var ruleMatch = selectedRules.size === 0 || selectedRules.has(grp.dataset.rule);
                  if (!ruleMatch) {
                    grp.style.display = 'none';
                    return;
                  }
                  var rows2 = grp.querySelectorAll('.finding-row');
                  var anyRowVisible = false;
                  rows2.forEach(function (r) {
                    var visible = q === '' || r.dataset.search.indexOf(q) !== -1;
                    r.classList.toggle('search-hidden', !visible);
                    if (visible) { anyRowVisible = true; }
                  });
                  grp.style.display = anyRowVisible ? '' : 'none';
                  if (anyRowVisible) { anyGroupVisible = true; }
                  if (q !== '' || selectedRules.size > 0) { grp.open = anyRowVisible; }
                });
                noMatch.hidden = anyGroupVisible;
              }

              search.addEventListener('input', debounce(applyFindingsFilter, 60));

              function togglePill(rule) {
                if (selectedRules.has(rule)) { selectedRules.delete(rule); } else { selectedRules.add(rule); }
                Object.keys(pillButtons).forEach(function (r) {
                  pillButtons[r].setAttribute('data-on', selectedRules.has(r) ? '1' : '0');
                });
                clearPillsBtn.hidden = selectedRules.size === 0;
                applyFindingsFilter();
              }
              Object.keys(pillButtons).forEach(function (rule) {
                pillButtons[rule].addEventListener('click', function () { togglePill(rule); });
              });
              clearPillsBtn.addEventListener('click', function () {
                selectedRules.clear();
                Object.keys(pillButtons).forEach(function (r) { pillButtons[r].setAttribute('data-on', '0'); });
                clearPillsBtn.hidden = true;
                applyFindingsFilter();
              });
            }
            return card;
          }

          // ---------- Changed files card ----------
          function buildChangedFilesCard() {
            var card = el('section', { id: 'changed-files', class: 'card col-12' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: 'Changed files' }));
            head.appendChild(el('span', { class: 'card-count', text: DATA.changedFiles.length + ' files' }));
            card.appendChild(head);
            var tableWrap = el('div', { class: 'table-wrap' });
            var table = el('table');
            var headRow = el('tr');
            ['module', 'path', 'classification', 'new lines', 'covered', 'uncovered ranges'].forEach(function (h) {
              headRow.appendChild(el('th', { text: h }));
            });
            table.appendChild(el('thead', {}, [headRow]));
            var tbody = el('tbody');
            DATA.changedFiles.forEach(function (f) {
              var tr = el('tr', { 'data-search': f.search });
              tr.appendChild(el('td', { text: f.module }));
              tr.appendChild(el('td', {}, [el('code', { text: f.path })]));
              tr.appendChild(el('td', {}, [codeBadge(f.classification)]));
              if ('newLines' in f) {
                tr.appendChild(el('td', { text: String(f.newLines) }));
                tr.appendChild(el('td', { text: String(f.coveredNewLines) }));
                tr.appendChild(el('td', { text: f.uncoveredRanges }));
              } else {
                tr.appendChild(el('td', { text: '-' })); tr.appendChild(el('td', { text: '-' })); tr.appendChild(el('td', { text: '-' }));
              }
              tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            tableWrap.appendChild(table);
            card.appendChild(tableWrap);
            return card;
          }

          // ---------- Files card (risk / package, band filters, search) ----------
          var BANDS = [
            ['all', 'All', function (v) { return true; }],
            ['zero', '%0', function (v) { return v !== null && v === 0; }],
            ['low', 'below 70%', function (v) { return v !== null && v < 70; }],
            ['mid', '%70\\u201390', function (v) { return v !== null && v >= 70 && v < 90; }],
            ['high', '90% and above', function (v) { return v !== null && v >= 90; }]
          ];
          function computePackages() {
            var map = {};
            DATA.fileCoverage.files.forEach(function (f) {
              var key = f.packagePath || '(root)';
              if (!map[key]) { map[key] = { name: key, numerator: 0, denominator: 0, count: 0 }; }
              map[key].numerator += f.numerator;
              map[key].denominator += f.denominator;
              map[key].count += 1;
            });
            return Object.keys(map).sort().map(function (k) {
              var p = map[k];
              var pct = p.denominator === 0 ? null : Math.round((p.numerator / p.denominator) * 1000) / 10;
              return { name: p.name, sub: p.count + ' files', pct: pct,
                pctText: pct === null ? 'n/a' : pct + '%' };
            });
          }
          function buildFilesCard() {
            var state = { view: 'risk', q: '', band: 'all', asc: true };
            var card = el('section', { id: 'files', class: 'card col-12' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: 'Per-file coverage' }));
            card.appendChild(head);
            card.appendChild(el('p', { class: 'card-sub',
              text: 'The default order is risk, not alphabet: lowest coverage on top. The package view shows where it concentrates.' }));

            var toolbar = el('div', { class: 'file-toolbar' });
            var toggle = el('div', { class: 'file-toggle' });
            var riskBtn = el('button', { type: 'button', text: 'Risk', 'data-on': '1' });
            var pkgBtn = el('button', { type: 'button', text: 'Package', 'data-on': '0' });
            toggle.appendChild(riskBtn); toggle.appendChild(pkgBtn);
            toolbar.appendChild(toggle);
            var search = el('input', { type: 'text', placeholder: 'search files' });
            toolbar.appendChild(search);
            card.appendChild(toolbar);

            var bandRow = el('div', { class: 'file-band-row' });
            bandRow.appendChild(el('span', { class: 'file-band-eyebrow', text: 'FILTER' }));
            var bandBtns = {};
            var bandChips = el('div', { style: 'display:flex;gap:6px;flex-wrap:wrap' });
            BANDS.forEach(function (b) {
              var btn = el('button', { type: 'button', class: 'pill', 'data-on': b[0] === 'all' ? '1' : '0', text: b[1] });
              bandBtns[b[0]] = btn;
              bandChips.appendChild(btn);
            });
            bandRow.appendChild(bandChips);
            var actions = el('div', { class: 'file-band-actions' });
            var sortBtn = el('button', { type: 'button', class: 'file-sort-btn' });
            var resetBtn = el('button', { type: 'button', class: 'file-reset-btn', text: 'Reset' });
            var countLabel = el('span', { class: 'file-count' });
            actions.appendChild(sortBtn); actions.appendChild(resetBtn); actions.appendChild(countLabel);
            bandRow.appendChild(actions);
            card.appendChild(bandRow);

            var rowsWrap = el('div', { class: 'file-rows' });
            card.appendChild(rowsWrap);
            var legend = el('div', { class: 'file-legend' });
            var legendLeft = el('span');
            legendLeft.appendChild(txt('Thresholds: '));
            legendLeft.appendChild(el('code', { style: 'color:var(--bad)', text: 'below 70%' }));
            legendLeft.appendChild(txt(' \\u00b7 '));
            legendLeft.appendChild(el('code', { style: 'color:var(--warn)', text: '%70\\u201390' }));
            legendLeft.appendChild(txt(' \\u00b7 '));
            legendLeft.appendChild(el('code', { style: 'color:var(--good)', text: '90% and above' }));
            legend.appendChild(legendLeft);
            var legendRight = el('code', { text: fmtInt(DATA.fileCoverage.totalFiles) + ' files \\u00b7 ' + fmtInt(DATA.fileCoverage.excluded.length) + ' excluded' });
            legend.appendChild(legendRight);
            card.appendChild(legend);

            var packages = null;

            function render() {
              var src = state.view === 'risk'
                ? DATA.fileCoverage.files.map(function (f) { return { name: f.displayPath, sub: f.module, pct: f.pct, pctText: f.pctText, fw: 500 }; })
                : (packages || (packages = computePackages())).map(function (p) { return { name: p.name, sub: p.sub, pct: p.pct, pctText: p.pctText, fw: 600 }; });
              var total = src.length;
              var unit = state.view === 'risk' ? 'files' : 'packages';
              var q = state.q.trim().toLowerCase();
              var matchQ = src.filter(function (r) { return q === '' || (r.name + ' ' + r.sub).toLowerCase().indexOf(q) !== -1; });
              var bandFn = (BANDS.filter(function (b) { return b[0] === state.band; })[0] || BANDS[0])[2];
              var kept = matchQ.filter(function (r) { return bandFn(r.pct); })
                .sort(function (a, b) { return band_cmp(a.pct, b.pct, state.asc); });

              while (rowsWrap.firstChild) { rowsWrap.removeChild(rowsWrap.firstChild); }
              kept.forEach(function (r) {
                var row = el('div', { class: 'file-row' + (r.pct === 0 ? ' file-row-zero' : '') });
                var nameWrap = el('span', { class: 'file-row-name-wrap' });
                nameWrap.appendChild(el('span', { class: 'file-row-name', style: 'font-weight:' + r.fw, text: r.name }));
                nameWrap.appendChild(el('span', { class: 'file-row-sub', text: r.sub }));
                row.appendChild(nameWrap);
                var bar = el('span', { class: 'file-row-bar' });
                bar.appendChild(el('span', { class: 'file-row-bar-fill', style: 'width:' + (r.pct === null ? 1.5 : Math.max(r.pct, 1.5)) + '%;background:' + tone(r.pct) }));
                row.appendChild(bar);
                row.appendChild(el('span', { class: 'file-row-pct', style: 'color:' + (r.pct === null ? 'var(--na)' : 'var(--ink)') + (r.pct === null ? ';font-style:italic' : ''), text: r.pctText }));
                rowsWrap.appendChild(row);
              });

              countLabel.textContent = kept.length + ' / ' + total + ' ' + unit;
              sortBtn.textContent = state.asc ? '\\u2191 lowest first' : '\\u2193 highest first';
              Object.keys(bandBtns).forEach(function (k) { bandBtns[k].setAttribute('data-on', k === state.band ? '1' : '0'); });
              riskBtn.setAttribute('data-on', state.view === 'risk' ? '1' : '0');
              pkgBtn.setAttribute('data-on', state.view === 'pkg' ? '1' : '0');
            }
            function band_cmp(a, b, asc) {
              if (a === null && b === null) { return 0; }
              if (a === null) { return 1; }
              if (b === null) { return -1; }
              return asc ? a - b : b - a;
            }

            riskBtn.addEventListener('click', function () { state.view = 'risk'; render(); });
            pkgBtn.addEventListener('click', function () { state.view = 'pkg'; render(); });
            search.addEventListener('input', debounce(function () { state.q = search.value; render(); }, 60));
            Object.keys(bandBtns).forEach(function (k) { bandBtns[k].addEventListener('click', function () { state.band = k; render(); }); });
            sortBtn.addEventListener('click', function () { state.asc = !state.asc; render(); });
            resetBtn.addEventListener('click', function () { state.q = ''; state.band = 'all'; state.asc = true; search.value = ''; render(); });

            render();
            return card;
          }

          // ---------- Warnings / incomplete reasons / per-test evidence ----------
          function buildReasonCard(id, title, items) {
            var card = el('section', { id: id, class: 'card col-12' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: title }));
            head.appendChild(el('span', { class: 'card-count', text: String(items.length) }));
            card.appendChild(head);
            var ul = el('ul', { class: 'reason-list' });
            items.forEach(function (r) {
              var li = el('li');
              li.appendChild(codeBadge(r.code));
              li.appendChild(txt(': ' + r.message));
              if (r.path) { li.appendChild(el('code', { text: ' ' + r.path })); }
              if (r.count !== undefined && r.count !== null) { li.appendChild(txt(' (' + r.count + ')')); }
              ul.appendChild(li);
            });
            card.appendChild(ul);
            return card;
          }
          function buildPerTestCard() {
            var card = el('section', { id: 'per-test-evidence', class: 'card col-7' });
            var head = el('div', { class: 'card-head' });
            head.appendChild(el('h2', { class: 'card-title', text: 'Per-test evidence (L2)' }));
            card.appendChild(head);
            card.appendChild(el('p', { class: 'card-sub',
              text: 'Evidence of which production lines each test executes. \\u201cambient\\u201d: lines that run the same way in every test and belong to none of them.' }));
            var tableWrap = el('div', { class: 'table-wrap' });
            var table = el('table');
            table.appendChild(el('thead', {}, [el('tr', {}, [el('th', { text: 'module' }), el('th', { text: 'method-line binding' }), el('th', { text: 'ambient lines' })])]));
            var tbody = el('tbody');
            DATA.perTest.forEach(function (m) {
              tbody.appendChild(el('tr', {}, [el('td', { text: m.moduleId }), el('td', { text: fmtInt(m.entryLineCount) }), el('td', { text: fmtInt(m.ambientLineCount) })]));
            });
            table.appendChild(tbody);
            tableWrap.appendChild(table);
            card.appendChild(tableWrap);
            return card;
          }

          // ---------- Run card ----------
          function buildRunCard(emptyList) {
            var card = el('section', { id: 'run', class: 'card col-5' });
            card.appendChild(el('h2', { class: 'card-title', text: 'Run and diagnostics' }));
            var m = DATA.meta;
            var dl = el('dl', { class: 'kv-grid' });
            function row(k, vNode) { dl.appendChild(el('dt', { text: k })); var dd = el('dd'); dd.appendChild(vNode); dl.appendChild(dd); }
            row('modules', txt(m.modules));
            row('fark modu', txt(m.diffMode));
            row('encoding', el('code', { text: m.encoding }));
            if (DATA.perTest) {
              var total = DATA.perTest.reduce(function (s, p) { return s + p.entryLineCount; }, 0);
              var ambient = DATA.perTest.reduce(function (s, p) { return s + p.ambientLineCount; }, 0);
              row('per-test evidence', el('code', { text: 'L2 \\u00b7 ' + fmtInt(total) + ' entry lines \\u00b7 ' + fmtInt(ambient) + ' ambient' }));
            }
            var warnNode = DATA.warnings.length
              ? el('span', { style: 'color:var(--warn)', text: DATA.warnings.length + ' warnings' })
              : el('span', { style: 'color:var(--good)', text: 'yok' });
            row('warnings', warnNode);
            card.appendChild(dl);

            if (emptyList.length) {
              var block = el('div', { class: 'empty-block' });
              block.appendChild(el('div', { class: 'empty-block-eyebrow', text: 'SECTIONS LEFT EMPTY IN THIS RUN' }));
              var list = el('div', { style: 'margin-top:9px;display:flex;flex-direction:column' });
              emptyList.forEach(function (e) {
                var r = el('div', { class: 'empty-row' });
                r.appendChild(el('span', { class: 'empty-row-name', text: e.name }));
                r.appendChild(el('span', { class: 'empty-row-why', text: e.why }));
                list.appendChild(r);
              });
              block.appendChild(list);
              block.appendChild(el('p', { class: 'empty-block-note',
                text: 'Empty sections collapse to one line instead of a full card: the report looks as full as it actually is.' }));
              card.appendChild(block);
            }
            return card;
          }

          // ---------- Sidebar / scroll-spy ----------
          function buildSidebar(navItems) {
            var side = el('nav', { class: 'side' });
            var head = el('div', { class: 'side-head' });
            var markTemplate = document.getElementById('brand-icon');
            var mark = markTemplate
              ? markTemplate.content.firstElementChild.cloneNode(true)
              : el('span'); // template missing (e.g. a hand-edited copy) - degrade to an empty mark, never break the page
            mark.classList.add('side-mark');
            head.appendChild(mark);
            var brand = el('div', { class: 'side-brand' });
            brand.appendChild(el('span', { class: 'side-brand-name', text: 'proof-java' }));
            brand.appendChild(el('span', { class: 'side-brand-sub', text: DATA.meta.modules + ' \\u00b7 Java ' + DATA.meta.languageLevel }));
            head.appendChild(brand);
            side.appendChild(head);

            var navEl = el('div', { class: 'side-nav' });
            var lastGroup = null;
            navItems.forEach(function (n) {
              if (n.group && n.group !== lastGroup) {
                navEl.appendChild(el('div', { class: 'side-nav-group', text: n.group }));
                lastGroup = n.group;
              }
              var a = el('a', { href: '#' + n.id, id: 'nav-' + n.id });
              a.appendChild(el('span', { class: 'side-nav-label', text: n.label }));
              if (n.count !== undefined && n.count !== '') { a.appendChild(el('span', { class: 'side-nav-count', text: n.count })); }
              navEl.appendChild(a);
            });
            side.appendChild(navEl);

            var foot = el('div', { class: 'side-foot' });
            var themeBtn = el('button', { type: 'button', id: 'theme-toggle', class: 'side-foot-btn', onclick: toggleTheme });
            foot.appendChild(themeBtn);
            foot.appendChild(el('div', { class: 'side-foot-meta',
              text: DATA.meta.toolVersion }));
            var metaLine2 = el('div', { class: 'side-foot-meta', text: 'schema ' + DATA.meta.schemaVersion });
            var metaLine3 = el('div', { class: 'side-foot-meta', text: DATA.meta.generatedAt });
            foot.appendChild(metaLine2);
            foot.appendChild(metaLine3);
            side.appendChild(foot);
            return side;
          }
          function wireScrollSpy(ids) {
            var links = {};
            ids.forEach(function (id) { links[id] = document.getElementById('nav-' + id); });
            function onScroll() {
              var current = ids[0];
              ids.forEach(function (id) {
                var target = document.getElementById(id);
                if (target && target.getBoundingClientRect().top < 140) { current = id; }
              });
              ids.forEach(function (id) {
                if (links[id]) { links[id].classList.toggle('active', id === current); }
              });
            }
            window.addEventListener('scroll', onScroll, { passive: true });
            onScroll();
          }

          // ---------- Theme (opens light by default - no prefers-color-scheme auto-dark) ----------
          function effectiveTheme() { return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'; }
          function updateThemeButton() {
            var btn = document.getElementById('theme-toggle');
            if (!btn) { return; }
            btn.textContent = '\\u25d0\\u00a0\\u00a0Switch theme';
          }
          function setTheme(theme) {
            if (theme === 'dark') { document.documentElement.setAttribute('data-theme', 'dark'); }
            else { document.documentElement.removeAttribute('data-theme'); }
            try { window.localStorage.setItem('proof-report-theme', theme); } catch (e) { /* may be blocked under file:// or in a private tab */ }
            updateThemeButton();
          }
          function toggleTheme() { setTheme(effectiveTheme() === 'dark' ? 'light' : 'dark'); }
          function restoreTheme() {
            try {
              var saved = window.localStorage.getItem('proof-report-theme');
              if (saved === 'dark') { document.documentElement.setAttribute('data-theme', 'dark'); }
            } catch (e) { /* fine - stay on the default light theme */ }
            updateThemeButton();
          }

          // ---------- Print ----------
          window.addEventListener('beforeprint', function () {
            PRINT_STATE = [];
            document.querySelectorAll('details').forEach(function (d) { PRINT_STATE.push([d, d.open]); d.open = true; });
          });
          window.addEventListener('afterprint', function () {
            PRINT_STATE.forEach(function (pair) { pair[0].open = pair[1]; });
          });

          // ---------- Deep link ----------
          // Runs after init() builds the DOM - the browser's own load-time fragment scroll happens
          // before that (#run/#mutant-detail/... don't exist yet at that point), so a shared/bookmarked URL
          // needs this to still land on the right card or finding row.
          function applyDeepLink() {
            if (!location.hash || location.hash.length < 2) { return; }
            var target = document.getElementById(location.hash.slice(1));
            if (!target) { return; }
            var group = target.closest('.finding-group');
            if (group) { group.open = true; }
            window.requestAnimationFrame(function () {
              target.scrollIntoView({ block: group ? 'center' : 'start' });
              target.classList.add('deep-link-highlight');
              window.setTimeout(function () { target.classList.remove('deep-link-highlight'); }, 2000);
            });
          }

          // ---------- Boot ----------
          function buildSummary() {
            var section = el('section', { id: 'summary', style: 'scroll-margin-top:24px' });
            var top = el('div', { style: 'display:flex;align-items:baseline;gap:14px;flex-wrap:wrap' });
            top.appendChild(el('span', { class: 'eyebrow', text: 'SUMMARY' }));
            top.appendChild(el('span', { class: 'summary-time', text: DATA.meta.diffMode + ' \\u00b7 ' + DATA.meta.generatedAt }));
            section.appendChild(top);

            var jacoco = engineLineMetric();
            var t = DATA.mutation ? mutationTotals() : null;
            var zeroFiles = DATA.fileCoverage ? DATA.fileCoverage.files.filter(function (f) { return f.pct === 0; }).length : 0;

            var tiles = el('div', { class: 'stat-row' });
            function tile(hrefId, label2, value, sub) {
              var a = el('a', { href: '#' + hrefId, class: 'stat-tile' });
              a.appendChild(el('div', { class: 'stat-tile-label', text: label2 }));
              a.appendChild(el('div', { class: 'stat-tile-value', text: value }));
              if (sub) { a.appendChild(el('div', { class: 'stat-tile-sub', text: sub })); }
              return a;
            }
            tiles.appendChild(tile('coverage', 'Line coverage', jacoco ? jacoco.pctText : 'n/a', jacoco ? (jacoco.numeratorText + ' / ' + jacoco.denominatorText) : null));
            if (t) { tiles.appendChild(tile('mutation', 'Mutation', t.killed + ' / ' + t.all, t.all === 0 ? 'no mutants generated' : 'killed / total')); }
            tiles.appendChild(tile('findings', 'Findings', String(DATA.findings.items.length), DATA.ruleIds.length + ' rules scanned'));
            if (DATA.fileCoverage) { tiles.appendChild(tile('files', 'files at 0% coverage', String(zeroFiles), fmtInt(DATA.fileCoverage.totalFiles) + ' files')); }
            section.appendChild(tiles);
            return section;
          }

          function init() {
            var app = document.getElementById('app');
            var emptyList = [];
            var navItems = [{ id: 'summary', label: 'Summary', count: '', group: 'OVERVIEW' }];
            var grid = el('div', { class: 'grid' });

            grid.appendChild(buildCoverageCard());
            var jacocoNav = engineLineMetric();
            navItems.push({ id: 'coverage', label: 'Coverage', count: jacocoNav ? jacocoNav.pctText : '', group: 'OVERVIEW' });

            var concern = findConcernMutant();
            if (DATA.mutation) {
              grid.appendChild(buildMutationCard(concern));
              var t = mutationTotals();
              navItems.push({ id: 'mutation', label: 'Mutation', count: t.killed + '/' + t.all, group: 'OVERVIEW' });
            } else {
              emptyList.push({ name: 'Mutation evidence', why: 'not collected' });
            }

            grid.appendChild(buildFindingsCard());
            navItems.push({ id: 'findings', label: 'Findings', count: String(DATA.findings.items.length), group: 'OVERVIEW' });

            if (DATA.changedFiles.length) {
              grid.appendChild(buildChangedFilesCard());
              navItems.push({ id: 'changed-files', label: 'Changed files', count: String(DATA.changedFiles.length), group: 'COVERAGE DETAIL' });
            } else {
              emptyList.push({ name: 'Changed files', why: DATA.meta.diffMode });
            }

            if (DATA.fileCoverage) {
              grid.appendChild(buildFilesCard());
              navItems.push({ id: 'files', label: 'Files', count: fmtInt(DATA.fileCoverage.totalFiles), group: 'COVERAGE DETAIL' });
            } else {
              emptyList.push({ name: 'Per-file coverage', why: 'not collected' });
            }

            var otherConcern = 0;
            if (concern) {
              var totals = DATA.mutation.totalsByStatus;
              var concerning = 0;
              Object.keys(totals).forEach(function (s) { if (s !== 'KILLED') { concerning += totals[s]; } });
              otherConcern = Math.max(0, concerning - 1);
              grid.appendChild(buildMutantDetailCard(concern, otherConcern));
              navItems.push({ id: 'mutant-detail', label: 'Highlighted mutant', count: String(otherConcern + 1), group: 'MUTATION DETAIL' });
            }

            if (DATA.mutation && DATA.mutation.modules.length) {
              grid.appendChild(buildMutationDetailCard());
              navItems.push({ id: 'all-mutants', label: 'All mutants', count: fmtInt(mutationTotals().all), group: 'MUTATION DETAIL' });
            }

            if (DATA.warnings.length) {
              grid.appendChild(buildReasonCard('warnings', 'Warnings', DATA.warnings));
              navItems.push({ id: 'warnings', label: 'Warnings', count: String(DATA.warnings.length), group: 'DIAGNOSTICS' });
            } else {
              emptyList.push({ name: 'Warnings', why: '0 records' });
            }

            if (DATA.incompleteReasons.length) {
              grid.appendChild(buildReasonCard('incomplete-reasons', 'Incomplete reasons', DATA.incompleteReasons));
              navItems.push({ id: 'incomplete-reasons', label: 'Incomplete reasons', count: String(DATA.incompleteReasons.length), group: 'DIAGNOSTICS' });
            } else {
              emptyList.push({ name: 'Incomplete reasons', why: '0 records' });
            }

            var perTestTotal = DATA.perTest ? DATA.perTest.reduce(function (s, p) { return s + p.entryLineCount; }, 0) : 0;
            if (DATA.perTest && perTestTotal > 0) {
              grid.appendChild(buildPerTestCard());
              navItems.push({ id: 'per-test-evidence', label: 'Per-test evidence', count: fmtInt(perTestTotal), group: 'DIAGNOSTICS' });
            } else {
              emptyList.push({ name: 'Per-test evidence (L2)', why: DATA.perTest ? '0 records' : 'not collected' });
            }

            grid.appendChild(buildRunCard(emptyList));
            navItems.push({ id: 'run', label: 'Run and diagnostics', count: '', group: 'DIAGNOSTICS' });

            app.appendChild(buildSidebar(navItems));
            var main = el('div', { class: 'main' });
            var wrap = el('div', { class: 'wrap' });
            wrap.appendChild(buildSummary());
            wrap.appendChild(grid);
            var footer = el('div', { class: 'footer-strip' });
            footer.appendChild(el('span', { text: 'proof-java ' + DATA.meta.toolVersion + ' \\u00b7 schema ' + DATA.meta.schemaVersion + ' \\u00b7 single file, offline, no external requests' }));
            footer.appendChild(el('span', { text: DATA.meta.generatedAt }));
            wrap.appendChild(footer);
            main.appendChild(wrap);
            app.appendChild(main);

            restoreTheme();
            wireScrollSpy(navItems.map(function (n) { return n.id; }));
            applyDeepLink();
          }

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
          } else {
            init();
          }
        })();
        """;
}
