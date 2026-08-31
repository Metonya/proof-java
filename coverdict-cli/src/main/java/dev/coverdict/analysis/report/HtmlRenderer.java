package dev.coverdict.analysis.report;

/**
 * Human-readable, standalone HTML rendering of a {@link VerdictDocument}
 * (hard rule 7: same document, now three readers - {@link VerdictJsonWriter}
 * for machines, {@link TextRenderer} for a terminal, this for a browser).
 *
 * <p>D-80 replaced the earlier "every row printed as HTML server-side"
 * renderer with a data-embedded one: {@link ReportDataWriter} turns {@code
 * doc} into one presentation-shaped JSON object (Turkish-formatted numbers,
 * a path-compressed file tree, a friendly-name lookup for only the codes
 * this exact report uses - hard rule 5 keeps every raw code alongside its
 * name, never in place of it), and this class embeds that JSON in a
 * {@code <script type="application/json">} for {@link #SCRIPT} - static,
 * interpolation-free client-side JavaScript shipped alongside it - to read
 * and render into the DOM.
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
        sb.append("<!doctype html>\n<html lang=\"tr\">\n<head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<title>coverdict raporu</title>\n");
        sb.append("<style>\n").append(CSS).append("\n</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<noscript><div class=\"noscript-warning\">Bu rapor JavaScript ile render edilir - ")
            .append("tarayıcınızda JavaScript devre dışı görünüyor, bu yüzden içerik boş kalıyor.</div></noscript>\n");
        sb.append("<div id=\"app\"></div>\n");
        sb.append("<script id=\"coverdict-data\" type=\"application/json\">")
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
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final String CSS = """
        :root {
          --paper: #f7f4ee; --paper-raised: #fffdf9; --ink: #1c1f26; --ink-soft: #565b64; --ink-faint: #8b8f78;
          --line: #ddd7c9; --accent: #1f3a5f; --accent-soft: #e4ecf3;
          --warn: #93400f; --warn-soft: #f4e3d1; --info: #2f5f45; --info-soft: #dcece2;
          --kill: #2f5f45; --kill-soft: #dcece2; --survive: #93301c; --survive-soft: #f4e0da;
          --other: #7a7264; --other-soft: #ece7d8;
          --shadow: 0 1px 2px rgba(28, 31, 38, 0.08);
          --row-pad: 0.5rem 0.7rem; --row-fs: 0.92em;
          color-scheme: light;
        }
        @media (prefers-color-scheme: dark) {
          :root:not([data-theme="light"]) {
            --paper: #14161b; --paper-raised: #1b1e25; --ink: #eae6db; --ink-soft: #a19c8f; --ink-faint: #6f7566;
            --line: #2c2f37; --accent: #86aed6; --accent-soft: #1d2a3a;
            --warn: #e0a06a; --warn-soft: #3a2a1a; --info: #8fc4a6; --info-soft: #1c2f24;
            --kill: #8fc4a6; --kill-soft: #1c2f24; --survive: #e08a76; --survive-soft: #3a2018;
            --other: #8c8577; --other-soft: #2a271c;
            --shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
            color-scheme: dark;
          }
        }
        :root[data-theme="dark"] {
          --paper: #14161b; --paper-raised: #1b1e25; --ink: #eae6db; --ink-soft: #a19c8f; --ink-faint: #6f7566;
          --line: #2c2f37; --accent: #86aed6; --accent-soft: #1d2a3a;
          --warn: #e0a06a; --warn-soft: #3a2a1a; --info: #8fc4a6; --info-soft: #1c2f24;
          --kill: #8fc4a6; --kill-soft: #1c2f24; --survive: #e08a76; --survive-soft: #3a2018;
          --other: #8c8577; --other-soft: #2a271c;
          --shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
          color-scheme: dark;
        }
        :root[data-theme="light"] { color-scheme: light; }
        :root[data-density="compact"] { --row-pad: 0.25rem 0.6rem; --row-fs: 0.82em; }
        * { box-sizing: border-box; }
        body { margin: 0; background: var(--paper); color: var(--ink);
          font-family: "IBM Plex Sans", system-ui, sans-serif; line-height: 1.5; }
        code { font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 0.9em; }
        h1, h2, h3 { font-family: "IBM Plex Serif", Georgia, serif; }

        .noscript-warning { background: var(--warn-soft); color: var(--warn); padding: 1rem 1.25rem;
          text-align: center; font-weight: 600; }

        .topbar { position: sticky; top: 0; z-index: 30; background: var(--paper); border-bottom: 1px solid var(--line); }
        .topbar-inner { max-width: 1200px; margin: 0 auto; padding: 0.6rem 1.25rem; display: flex;
          align-items: center; gap: 1rem; flex-wrap: wrap; }
        .brand { display: flex; flex-direction: column; line-height: 1.2; margin-right: 0.5rem; }
        .brand-title { font-weight: 700; font-size: 0.95em; }
        .brand-sub { color: var(--ink-soft); font-size: 0.78em; font-family: "IBM Plex Mono", ui-monospace, monospace;
          max-width: 22rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .global-search { flex: 1 1 16rem; min-width: 10rem; padding: 0.4rem 0.7rem; border: 1px solid var(--line);
          border-radius: 6px; background: var(--paper-raised); color: var(--ink); font-family: inherit; }
        .section-nav { display: flex; gap: 0.15rem; flex-wrap: wrap; }
        .section-nav a { padding: 0.3rem 0.55rem; border-radius: 6px; font-size: 0.8em; color: var(--ink-soft); }
        .section-nav a:hover { background: var(--accent-soft); color: var(--ink); }
        .topbar-actions { display: flex; gap: 0.35rem; align-items: center; }
        .ghost-btn { padding: 0.3rem 0.6rem; border-radius: 6px; border: 1px solid var(--line);
          background: var(--paper-raised); color: var(--ink-soft); font-size: 0.8em; cursor: pointer;
          font-family: inherit; }
        .ghost-btn:hover { border-color: var(--accent); color: var(--ink); }
        .theme-toggle { padding: 0.3rem 0.6rem; border-radius: 999px; border: 1px solid var(--line);
          background: var(--paper-raised); color: var(--ink-soft); cursor: pointer; font-size: 0.8em;
          font-family: inherit; }
        .theme-toggle:hover { border-color: var(--accent); }

        main { max-width: 1200px; margin: 0 auto; padding: 1.5rem 1.25rem 4rem; }
        details.report-section { margin-bottom: 1.25rem; scroll-margin-top: 4.5rem; }
        details.report-section > summary { cursor: pointer; list-style: revert; display: flex;
          align-items: baseline; gap: 0.6rem; flex-wrap: wrap; }
        details.report-section > summary h2 { display: inline; margin: 0; font-size: 1.3em; }
        .section-count { color: var(--ink-soft); font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 0.8em; }
        .match-badge { color: var(--accent); font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 0.8em;
          font-weight: 600; }
        .report-section-body { margin-top: 0.75rem; }
        .report-section.search-hidden { display: none; }

        .status { display: inline-block; padding: 0.15rem 0.6rem; border-radius: 999px; font-weight: 600; font-size: 0.85em; }
        .status-complete { background: var(--info-soft); color: var(--info); }
        .status-incomplete { background: var(--warn-soft); color: var(--warn); }
        .unavailable { color: var(--ink-soft); font-style: italic; }
        .empty { color: var(--ink-soft); font-style: italic; }
        .section-note { color: var(--ink-soft); font-size: 0.9em; max-width: 68ch; }

        .identity { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 0.9rem; color: var(--ink-soft); }
        .identity dt { font-weight: 600; }
        .identity code { color: var(--ink); }

        .code-badge { display: inline-flex; align-items: baseline; gap: 0.35rem; }
        .code-badge code { color: var(--ink-soft); }

        table { width: max-content; min-width: 100%; border-collapse: collapse; margin: 0; background: var(--paper-raised); }
        th, td { text-align: left; padding: var(--row-pad); border-bottom: 1px solid var(--line);
          overflow-wrap: break-word; font-size: var(--row-fs); }
        td code, th code { white-space: nowrap; }
        th { color: var(--ink-soft); font-weight: 600; font-size: 0.78em; text-transform: uppercase; white-space: nowrap; }
        .table-wrap { overflow-x: auto; margin: 0.5rem 0 1rem; border: 1px solid var(--line); border-radius: 8px;
          padding: 0.4rem; }
        .table-wrap table { margin: 0; }

        .metric-list { display: flex; flex-direction: column; gap: 0.5rem; margin: 0.5rem 0 1.25rem; }
        .metric-row { border: 1px solid var(--line); border-radius: 8px; padding: 0.6rem 0.8rem; background: var(--paper-raised); }
        .metric-head { display: flex; justify-content: space-between; align-items: baseline; gap: 0.6rem; }
        .metric-pct { font-family: "IBM Plex Mono", ui-monospace, monospace; font-size: 1.1em; }
        .metric-bar { height: 5px; border-radius: 3px; background: var(--line); margin: 0.5rem 0 0.4rem; overflow: hidden; }
        .metric-bar-fill { height: 5px; background: var(--accent); }
        .metric-ratio { color: var(--ink-soft); font-size: 0.82em; font-family: "IBM Plex Mono", ui-monospace, monospace; }

        .rule-summary-row { display: flex; gap: 0.4rem; flex-wrap: wrap; margin: 0.5rem 0; }
        .rule-chip { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.3rem 0.6rem; border-radius: 999px;
          border: 1px solid var(--line); background: var(--paper-raised); font-size: 0.78em; }
        .rule-chip code { color: var(--ink-soft); }
        .rule-chip-zero { opacity: 0.55; border-style: dashed; }
        .shown-count { color: var(--ink-soft); font-size: 0.82em; font-family: "IBM Plex Mono", ui-monospace, monospace; }

        details.finding-group { border: 1px solid var(--line); border-radius: 8px; background: var(--paper-raised);
          padding: 0.6rem 0.8rem; margin-bottom: 0.6rem; }
        details.finding-group > summary { cursor: pointer; display: flex; align-items: baseline; gap: 0.5rem;
          flex-wrap: wrap; font-weight: 600; }
        .finding-group-count { color: var(--ink-soft); font-weight: 400; font-size: 0.85em; margin-left: auto; }
        .finding-group-desc { color: var(--ink-soft); font-size: 0.88em; margin: 0.5rem 0 0.25rem; }
        .finding-group-action { font-size: 0.88em; margin: 0 0 0.5rem; }
        .finding-rows { border-top: 1px solid var(--line); margin-top: 0.4rem; }
        .finding-row { display: grid; grid-template-columns: auto minmax(0, auto) minmax(0, auto) 1fr; gap: 0.6rem;
          align-items: baseline; padding: 0.4rem 0; border-bottom: 1px dotted var(--line); font-size: 0.88em; }
        .finding-row:last-child { border-bottom: none; }
        .finding-row.search-hidden { display: none; }
        .finding-loc, .finding-method { white-space: nowrap; }
        .finding-message { color: var(--ink-soft); }

        .sev-badge, .conf-badge, .status-chip { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 999px;
          font-size: 0.75em; font-weight: 600; }
        .sev-WARNING { background: var(--warn-soft); color: var(--warn); }
        .sev-INFO { background: var(--info-soft); color: var(--info); }
        .conf-HIGH { background: var(--survive-soft); color: var(--survive); }
        .conf-MEDIUM { background: var(--warn-soft); color: var(--warn); }
        .conf-LOW, .conf-INCONCLUSIVE { background: var(--other-soft); color: var(--other); }

        .status-counts { display: flex; gap: 0.5rem; flex-wrap: wrap; margin: 0.5rem 0 1rem; }
        .status-chip { border: 1px solid var(--line); background: var(--paper-raised); padding: 0.3rem 0.7rem; }
        .status-KILLED { color: var(--kill); }
        .status-SURVIVED { color: var(--survive); }
        .mutation-survived-label { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.85em;
          color: var(--ink-soft); margin: 0 0 0.75rem; }

        details.mutation-class { background: var(--paper-raised); border: 1px solid var(--line); border-radius: 8px;
          padding: 0.5rem 0.8rem; margin-bottom: 0.5rem; }
        details.mutation-class > summary { cursor: pointer; font-weight: 600; }
        table.mutants td:nth-child(3) { font-weight: 600; }
        tr.status-KILLED td:nth-child(3) { color: var(--kill); }
        tr.status-SURVIVED td:nth-child(3) { color: var(--survive); }
        tr.search-hidden { display: none; }

        details.tree-folder { margin-left: 0.2rem; border-left: 1px dashed var(--line); padding-left: 0.7rem; }
        details.tree-folder > summary { cursor: pointer; display: flex; align-items: center; gap: 0.6rem; }
        .tree-file { display: flex; align-items: center; gap: 0.8rem; padding: 0.15rem 0 0.15rem 1rem;
          border-bottom: 1px dotted var(--line); }
        .tree-file.search-hidden, .tree-folder.search-hidden { display: none; }
        .tree-pct { color: var(--ink-soft); font-variant-numeric: tabular-nums; font-size: 0.85em; min-width: 3.4em;
          text-align: right; }
        .tree-pct.na { font-style: italic; color: var(--ink-faint); }
        .tree-file-bar-wrap { margin-left: auto; display: flex; align-items: center; gap: 0.5rem; }
        .tree-file-bar { width: 90px; height: 5px; border-radius: 3px; background: var(--line); overflow: hidden;
          display: inline-block; }
        .tree-file-bar-fill { display: block; height: 5px; background: var(--accent); }

        .reason-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
        .reason-list li { border-left: 2px solid var(--line); padding-left: 0.7rem; font-size: 0.88em; color: var(--ink-soft); }
        .reason-list li.search-hidden { display: none; }
        .reason-list code { color: var(--ink); }

        .deep-link-highlight { outline: 2px solid var(--accent); outline-offset: 2px; border-radius: 4px; }

        footer { color: var(--ink-soft); font-size: 0.82em; border-top: 1px solid var(--line); padding-top: 1rem;
          margin-top: 2rem; display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }

        @media print {
          .topbar, .noscript-warning { display: none; }
          details.report-section, details.finding-group, details.mutation-class, details.tree-folder { break-inside: avoid; }
        }
        """;

    /**
     * Static source text, no interpolation - see the class javadoc's
     * Security section. Reads only the escaped JSON in {@code
     * #coverdict-data} (never {@code innerHTML}/{@code eval}) and builds
     * the DOM with {@code createElement}/{@code textContent}/{@code
     * setAttribute} only.
     */
    private static final String SCRIPT = """
        (function () {
          'use strict';
          var DATA = JSON.parse(document.getElementById('coverdict-data').textContent);
          var FILTER_ACTIVE = false;
          var PRINT_STATE = [];

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
          function txt(s) { return document.createTextNode(s === null || s === undefined ? '' : String(s)); }
          function fmtInt(n) { try { return n.toLocaleString('tr-TR'); } catch (e) { return String(n); } }
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
            var span = el('span', { class: 'code-badge', title: l.description || null });
            span.appendChild(txt(l.name + ' '));
            span.appendChild(el('code', { text: code }));
            return span;
          }

          // ---------- Koşu ----------
          function renderRun() {
            var m = DATA.meta;
            var dl = el('dl', { class: 'identity' });
            function row(k, valueNode) {
              dl.appendChild(el('dt', { text: k }));
              var dd = el('dd');
              dd.appendChild(valueNode);
              dl.appendChild(dd);
            }
            row('Durum', el('span', { class: 'status ' + (m.complete ? 'status-complete' : 'status-incomplete'), text: m.statusLabel }));
            row('Rapor oluşturulma zamanı', txt(m.generatedAt));
            row('Modül(ler)', txt(m.modules));
            row('Fark modu', txt(m.diffMode));
            if (m.baseRef) { row('Karşılaştırma referansı', txt(m.baseRef)); }
            m.commitRows.forEach(function (c) {
              row(c.label, el('code', { title: c.full, text: c.short }));
            });
            if ('dirty' in m) { row('Kaydedilmemiş değişiklik', txt(m.dirty ? 'var' : 'yok')); }
            row('Dil seviyesi', txt(String(m.languageLevel)));
            row('Encoding', txt(m.encoding));
            row('Bulgu kapsamı', txt(m.findingsScopeLabel));
            if (m.exclusions.length) { row('Hariç tutulan desenler', txt(m.exclusions.join(', '))); }
            row('Sürüm', txt('coverdict ' + m.toolVersion + ' \\u00b7 schema ' + m.schemaVersion));
            return dl;
          }

          // ---------- Kapsama ----------
          function metricList(metrics) {
            var box = el('div', { class: 'metric-list' });
            metrics.forEach(function (mtr) {
              var row = el('div', { class: 'metric-row' });
              var head = el('div', { class: 'metric-head' });
              head.appendChild(codeBadge(mtr.mode));
              head.appendChild(el('span', { class: 'metric-pct', text: mtr.pctText }));
              row.appendChild(head);
              var bar = el('div', { class: 'metric-bar' });
              bar.appendChild(el('div', { class: 'metric-bar-fill', style: mtr.pct === null ? 'width:0' : ('width:' + mtr.pct + '%') }));
              row.appendChild(bar);
              row.appendChild(el('div', { class: 'metric-ratio', text: mtr.numeratorText + ' / ' + mtr.denominatorText + ' (' + mtr.numeratorName + '/' + mtr.denominatorName + ')' }));
              box.appendChild(row);
            });
            return box;
          }
          function renderCoverage() {
            var wrap = el('div');
            wrap.appendChild(el('h3', { text: 'Genel' }));
            wrap.appendChild(metricList(DATA.coverage.overall));
            wrap.appendChild(el('h3', { text: 'Yeni kod' }));
            if (DATA.coverage.newCode.available) {
              wrap.appendChild(metricList(DATA.coverage.newCode.metrics));
            } else {
              var p = el('p', { class: 'unavailable' });
              p.appendChild(txt('hesaplanamadı \\u2014 '));
              p.appendChild(codeBadge(DATA.coverage.newCode.unavailableStatus));
              wrap.appendChild(p);
            }
            return wrap;
          }

          // ---------- Değişen dosyalar ----------
          function renderChangedFiles() {
            var files = DATA.changedFiles;
            var wrap = el('div');
            if (!files.length) {
              wrap.appendChild(el('p', { class: 'empty', text: 'Değişen dosya yok.' }));
              return wrap;
            }
            var tableWrap = el('div', { class: 'table-wrap' });
            var table = el('table');
            var headRow = el('tr');
            ['modül', 'yol', 'sınıflandırma', 'yeni satır', 'kapsanan', 'kapsanmayan aralıklar'].forEach(function (h) {
              headRow.appendChild(el('th', { text: h }));
            });
            table.appendChild(el('thead', {}, [headRow]));
            var tbody = el('tbody');
            files.forEach(function (f) {
              var tr = el('tr', { 'data-search': f.search });
              tr.appendChild(el('td', { text: f.module }));
              tr.appendChild(el('td', {}, [el('code', { text: f.path })]));
              tr.appendChild(el('td', {}, [codeBadge(f.classification)]));
              if ('newLines' in f) {
                tr.appendChild(el('td', { text: String(f.newLines) }));
                tr.appendChild(el('td', { text: String(f.coveredNewLines) }));
                tr.appendChild(el('td', { text: f.uncoveredRanges }));
              } else {
                tr.appendChild(el('td', { text: '-' }));
                tr.appendChild(el('td', { text: '-' }));
                tr.appendChild(el('td', { text: '-' }));
              }
              tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            tableWrap.appendChild(table);
            wrap.appendChild(tableWrap);
            return wrap;
          }

          // ---------- Bulgular ----------
          function renderFindings() {
            var f = DATA.findings;
            var wrap = el('div');
            wrap.appendChild(el('p', { class: 'section-note', text: 'Kapsam: ' + f.scopeLabel + '.' }));

            var groups = {};
            f.items.forEach(function (it) { (groups[it.rule] = groups[it.rule] || []).push(it); });

            var summaryRow = el('div', { class: 'rule-summary-row' });
            DATA.ruleIds.forEach(function (rule) {
              var count = (groups[rule] || []).length;
              var l = label(rule);
              var chip = el('span', { class: 'rule-chip' + (count === 0 ? ' rule-chip-zero' : ''), title: l.description });
              chip.appendChild(txt(l.name + ' '));
              chip.appendChild(el('code', { text: rule }));
              chip.appendChild(txt(' ' + count));
              summaryRow.appendChild(chip);
            });
            wrap.appendChild(summaryRow);

            if (!f.items.length) {
              wrap.appendChild(el('p', { class: 'empty', text: 'Bulgu yok.' }));
              return wrap;
            }

            wrap.appendChild(el('p', { class: 'shown-count', text: f.items.length + ' bulgu, ' + Object.keys(groups).length + ' kural.' }));

            var list = el('div', { class: 'finding-groups' });
            Object.keys(groups).sort().forEach(function (rule) {
              var items = groups[rule];
              var l = label(rule);
              var group = el('details', { class: 'finding-group filter-group', 'data-default-open': '1' });
              group.open = true;
              var summary = el('summary');
              summary.appendChild(txt(l.name + ' '));
              summary.appendChild(el('code', { text: rule }));
              summary.appendChild(el('span', { class: 'sev-badge sev-' + items[0].severity, text: label(items[0].severity).name }));
              summary.appendChild(el('span', { class: 'finding-group-count', text: items.length + ' bulgu' }));
              group.appendChild(summary);
              group.appendChild(el('p', { class: 'finding-group-desc', text: l.description }));
              var action = el('p', { class: 'finding-group-action' });
              action.appendChild(el('strong', { text: '\\u00d6neri: ' }));
              action.appendChild(txt(items[0].suggestedAction));
              group.appendChild(action);

              var rows = el('div', { class: 'finding-rows' });
              items.forEach(function (it) {
                var row = el('div', {
                  class: 'finding-row', 'data-search': it.search,
                  id: it.fingerprint ? ('f-' + it.fingerprint) : null
                });
                row.appendChild(el('span', { class: 'conf-badge conf-' + it.confidence, text: label(it.confidence).name }));
                var loc = el('code', { class: 'finding-loc' });
                loc.appendChild(txt(it.path + ':' + it.startLine + (it.endLine !== it.startLine ? ('-' + it.endLine) : '')));
                row.appendChild(loc);
                row.appendChild(el('code', { class: 'finding-method', text: it.anchorMethod || '\\u2014' }));
                row.appendChild(el('span', { class: 'finding-message', text: it.message }));
                rows.appendChild(row);
              });
              group.appendChild(rows);
              list.appendChild(group);
            });
            wrap.appendChild(list);
            return wrap;
          }

          // ---------- Uyarılar / eksik nedenler ----------
          function renderReasonList(items) {
            var wrap = el('div');
            if (!items.length) {
              wrap.appendChild(el('p', { class: 'empty', text: 'Yok.' }));
              return wrap;
            }
            var ul = el('ul', { class: 'reason-list' });
            items.forEach(function (r) {
              var li = el('li', { 'data-search': r.search });
              li.appendChild(codeBadge(r.code));
              li.appendChild(txt(': ' + r.message));
              if (r.path) { li.appendChild(el('code', { text: ' ' + r.path })); }
              if (r.count !== undefined && r.count !== null) { li.appendChild(txt(' (' + r.count + ')')); }
              ul.appendChild(li);
            });
            wrap.appendChild(ul);
            return wrap;
          }

          // ---------- Test bazlı kanıt (L2) ----------
          function renderPerTest() {
            var wrap = el('div');
            wrap.appendChild(el('p', { class: 'section-note',
              text: 'Testlerin hangi üretim satırlarını çalıştırdığına dair kanıt. \\u201cambient\\u201d: her testte aynı şekilde çalışan, o teste özgü olmayan satırlar.' }));
            var tableWrap = el('div', { class: 'table-wrap' });
            var table = el('table');
            table.appendChild(el('thead', {}, [el('tr', {}, [
              el('th', { text: 'modül' }), el('th', { text: 'metot-satır eşleşmesi' }), el('th', { text: 'ambient satır' })
            ])]));
            var tbody = el('tbody');
            DATA.perTest.forEach(function (m) {
              tbody.appendChild(el('tr', {}, [
                el('td', { text: m.moduleId }),
                el('td', { text: fmtInt(m.entryLineCount) }),
                el('td', { text: fmtInt(m.ambientLineCount) })
              ]));
            });
            table.appendChild(tbody);
            tableWrap.appendChild(table);
            wrap.appendChild(tableWrap);
            return wrap;
          }

          // ---------- Mutasyon (L3) ----------
          function statusCountsText(counts) {
            return Object.keys(counts).sort().map(function (s) { return label(s).name + ' ' + counts[s]; }).join(' \\u00b7 ');
          }
          function mutantTable(mutants, className, methodName) {
            var wrap = el('div', { class: 'table-wrap' });
            var table = el('table', { class: 'mutants' });
            table.appendChild(el('thead', {}, [el('tr', {}, [
              el('th', { text: 'mutator' }), el('th', { text: 'satır' }), el('th', { text: 'durum' }), el('th', { text: 'öldüren testler' })
            ])]));
            var tbody = el('tbody');
            mutants.forEach(function (m) {
              var killingTests = m.killingTests.join(', ');
              var search = (className + ' ' + methodName + ' ' + m.mutator + ' ' + m.status + ' ' + killingTests).toLowerCase();
              var tr = el('tr', { class: 'mutant-row status-' + m.status, 'data-status': m.status, 'data-search': search });
              tr.appendChild(el('td', { text: m.mutator }));
              tr.appendChild(el('td', { text: String(m.line) }));
              tr.appendChild(el('td', { text: label(m.status).name }));
              tr.appendChild(el('td', { text: killingTests || '\\u2014' }));
              tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            wrap.appendChild(table);
            return wrap;
          }
          function renderMutation() {
            var mut = DATA.mutation;
            var wrap = el('div');
            var totals = el('div', { class: 'status-counts' });
            Object.keys(mut.totalsByStatus).sort().forEach(function (status) {
              var l = label(status);
              var chip = el('span', { class: 'status-chip status-' + status });
              chip.appendChild(txt(l.name + ' '));
              chip.appendChild(el('strong', { text: String(mut.totalsByStatus[status]) }));
              totals.appendChild(chip);
            });
            wrap.appendChild(totals);

            var survivedOnly = el('input', { type: 'checkbox', id: 'mutation-survived-only', onchange: applyFilter });
            wrap.appendChild(el('label', { class: 'mutation-survived-label' }, [survivedOnly, ' sadece SURVIVED']));

            mut.modules.forEach(function (mod) {
              wrap.appendChild(el('h3', {}, ['Modül: ', el('code', { text: mod.moduleId })]));
              mod.classes.forEach(function (cls) {
                var details = el('details', { class: 'mutation-class filter-group', 'data-default-open': '0' });
                var summary = el('summary');
                summary.appendChild(el('code', { text: cls.className }));
                summary.appendChild(txt(' \\u00b7 ' + statusCountsText(cls.countsByStatus)));
                details.appendChild(summary);
                cls.methods.forEach(function (method) {
                  details.appendChild(el('h4', {}, [
                    el('code', { title: method.signatureFull, text: cls.className + '#' + method.signatureShort }),
                    txt(' (satır ' + method.firstLine + '-' + method.lastLine + ')')
                  ]));
                  details.appendChild(mutantTable(method.mutants, cls.className, method.methodName));
                });
                wrap.appendChild(details);
              });
            });
            return wrap;
          }

          // ---------- Dosya bazlı kapsama ----------
          function pctSpan(node) {
            return el('span', { class: 'tree-pct' + (node.pct === null ? ' na' : ''), text: node.pctText });
          }
          function buildTreeNode(node, defaultOpen) {
            if (node.isFile) {
              var row = el('div', { class: 'tree-file', 'data-search': (node.name + ' ' + (node.path || '')).toLowerCase() });
              row.appendChild(el('code', { text: node.name }));
              var barWrap = el('span', { class: 'tree-file-bar-wrap' });
              var bar = el('span', { class: 'tree-file-bar' });
              bar.appendChild(el('span', { class: 'tree-file-bar-fill', style: node.pct === null ? 'width:0' : ('width:' + node.pct + '%') }));
              barWrap.appendChild(bar);
              barWrap.appendChild(pctSpan(node));
              row.appendChild(barWrap);
              return row;
            }
            var details = el('details', { class: 'tree-folder filter-group', 'data-default-open': defaultOpen ? '1' : '0' });
            details.open = defaultOpen;
            var summary = el('summary');
            summary.appendChild(txt(node.name + '/ '));
            summary.appendChild(pctSpan(node));
            details.appendChild(summary);
            node.children.forEach(function (child) { details.appendChild(buildTreeNode(child, false)); });
            return details;
          }
          function renderFileTree() {
            var fc = DATA.fileCoverage;
            var wrap = el('div');
            wrap.appendChild(el('p', { class: 'section-note', text: fmtInt(fc.totalFiles) + ' dosya.' }));
            var box = el('div', { class: 'tree-box' });
            fc.tree.forEach(function (node) { box.appendChild(buildTreeNode(node, true)); });
            wrap.appendChild(box);
            if (fc.excluded.length) {
              var exDetails = el('details', {});
              exDetails.appendChild(el('summary', { text: fc.excluded.length + ' hariç tutulan dosya' }));
              var ul = el('ul');
              fc.excluded.forEach(function (p) { ul.appendChild(el('li', {}, [el('code', { text: p })])); });
              exDetails.appendChild(ul);
              wrap.appendChild(exDetails);
            }
            return wrap;
          }

          // ---------- Section shell ----------
          var TOP_SECTIONS = [];
          function buildSection(id, title, countText, bodyNode) {
            var details = el('details', { class: 'report-section', id: id });
            var key = 'coverdict-section-' + id;
            var stored = null;
            try { stored = window.localStorage.getItem(key); } catch (e) { /* file:// veya gizli sekmede engellenebilir */ }
            details.open = stored !== null ? stored === '1' : true;
            var summary = el('summary');
            summary.appendChild(el('h2', { text: title }));
            if (countText) { summary.appendChild(el('span', { class: 'section-count', text: countText })); }
            summary.appendChild(el('span', { class: 'match-badge' }));
            details.appendChild(summary);
            var body = el('div', { class: 'report-section-body' });
            body.appendChild(bodyNode);
            details.appendChild(body);
            details.addEventListener('toggle', function () {
              if (FILTER_ACTIVE) { return; }
              try { window.localStorage.setItem(key, details.open ? '1' : '0'); } catch (e) { /* aynı, sorun değil */ }
            });
            TOP_SECTIONS.push(details);
            return details;
          }

          var NAV_ITEMS = [
            { id: 'run', label: 'Koşu' },
            { id: 'findings', label: 'Bulgular' },
            { id: 'coverage', label: 'Kapsama' },
            { id: 'changed-files', label: 'Değişen dosyalar' },
            { id: 'mutation', label: 'Mutasyon', needs: 'mutation' },
            { id: 'file-coverage', label: 'Dosya bazlı kapsama', needs: 'fileCoverage' },
            { id: 'per-test', label: 'Test bazlı kanıt', needs: 'perTest' },
            { id: 'warnings', label: 'Uyarılar', needsLen: 'warnings' },
            { id: 'incomplete', label: 'Eksik nedenler', needsLen: 'incompleteReasons' }
          ];

          function buildTopbar() {
            var bar = el('div', { class: 'topbar' });
            var inner = el('div', { class: 'topbar-inner' });

            var brand = el('div', { class: 'brand' });
            brand.appendChild(el('span', { class: 'brand-title', text: 'coverdict raporu' }));
            brand.appendChild(el('span', { class: 'brand-sub', text: DATA.meta.modules }));
            inner.appendChild(brand);

            var search = el('input', { type: 'text', id: 'global-search', class: 'global-search',
              placeholder: 'Rapor genelinde ara (kural, dosya, mesaj, mutator...)' });
            inner.appendChild(search);

            var nav = el('nav', { class: 'section-nav' });
            NAV_ITEMS.forEach(function (item) {
              if (item.needs && !DATA[item.needs]) { return; }
              if (item.needsLen && !(DATA[item.needsLen] && DATA[item.needsLen].length)) { return; }
              var a = el('a', { href: '#' + item.id, text: item.label });
              a.addEventListener('click', function () {
                var target = document.getElementById(item.id);
                if (target && target.tagName === 'DETAILS') { target.open = true; }
              });
              nav.appendChild(a);
            });
            inner.appendChild(nav);

            var actions = el('div', { class: 'topbar-actions' });
            actions.appendChild(el('button', { type: 'button', class: 'ghost-btn', text: 'Hepsini aç', onclick: function () { setAllSections(true); } }));
            actions.appendChild(el('button', { type: 'button', class: 'ghost-btn', text: 'Hepsini kapat', onclick: function () { setAllSections(false); } }));
            actions.appendChild(el('button', { type: 'button', class: 'ghost-btn', id: 'density-toggle', text: 'Sık\\u0131 görünüm', onclick: toggleDensity }));
            actions.appendChild(el('button', { type: 'button', id: 'theme-toggle', class: 'theme-toggle', 'aria-label': 'Açık/koyu tema değiştir', onclick: toggleTheme }));
            inner.appendChild(actions);

            bar.appendChild(inner);
            return bar;
          }

          function setAllSections(open) {
            document.querySelectorAll('details').forEach(function (d) { d.open = open; });
          }

          // ---------- Filtering ----------
          function applyFilter() {
            var search = document.getElementById('global-search');
            var q = search ? search.value.trim().toLowerCase() : '';
            FILTER_ACTIVE = q !== '';

            document.querySelectorAll('[data-search]').forEach(function (leaf) {
              var match = q === '' || leaf.dataset.search.indexOf(q) !== -1;
              leaf.classList.toggle('search-hidden', !match);
            });

            var survivedOnlyEl = document.getElementById('mutation-survived-only');
            var survivedOnly = !!(survivedOnlyEl && survivedOnlyEl.checked);
            if (survivedOnly) {
              document.querySelectorAll('.mutant-row').forEach(function (row) {
                if (row.dataset.status !== 'SURVIVED') { row.classList.add('search-hidden'); }
              });
            }
            var filtering = q !== '' || survivedOnly;

            document.querySelectorAll('.filter-group').forEach(function (grp) {
              var leaves = grp.querySelectorAll('[data-search]');
              if (!leaves.length) { return; }
              var anyVisible = false;
              leaves.forEach(function (leaf) { if (!leaf.classList.contains('search-hidden')) { anyVisible = true; } });
              grp.classList.toggle('search-hidden', !anyVisible);
              grp.open = filtering ? anyVisible : grp.dataset.defaultOpen === '1';
            });

            TOP_SECTIONS.forEach(function (sec) {
              var leaves = sec.querySelectorAll('[data-search]');
              if (!leaves.length) { return; }
              if (!filtering) {
                var key = 'coverdict-section-' + sec.id;
                var stored = null;
                try { stored = window.localStorage.getItem(key); } catch (e) { /* aynı */ }
                sec.open = stored !== null ? stored === '1' : true;
              } else {
                var anyVisible = false;
                leaves.forEach(function (leaf) { if (!leaf.classList.contains('search-hidden')) { anyVisible = true; } });
                sec.open = anyVisible;
              }
            });

            TOP_SECTIONS.forEach(function (sec) {
              var badge = sec.querySelector('.match-badge');
              if (!badge) { return; }
              if (!filtering) { badge.textContent = ''; return; }
              var leaves = sec.querySelectorAll('[data-search]');
              var visible = 0;
              leaves.forEach(function (leaf) { if (!leaf.classList.contains('search-hidden')) { visible++; } });
              badge.textContent = leaves.length ? (visible + ' eşleşme') : '';
            });
          }

          // ---------- Theme / density ----------
          function effectiveTheme() {
            var explicit = document.documentElement.getAttribute('data-theme');
            if (explicit === 'light' || explicit === 'dark') { return explicit; }
            return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
          }
          function updateThemeButton() {
            var btn = document.getElementById('theme-toggle');
            if (!btn) { return; }
            var dark = effectiveTheme() === 'dark';
            btn.textContent = dark ? '\\u2600\\ufe0f Aç\\u0131k Mod' : '\\ud83c\\udf19 Koyu Mod';
            btn.setAttribute('aria-pressed', String(dark));
          }
          function setTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            try { window.localStorage.setItem('coverdict-report-theme', theme); } catch (e) { /* aynı */ }
            updateThemeButton();
          }
          function toggleTheme() { setTheme(effectiveTheme() === 'dark' ? 'light' : 'dark'); }
          function restoreTheme() {
            try {
              var saved = window.localStorage.getItem('coverdict-report-theme');
              if (saved === 'light' || saved === 'dark') { document.documentElement.setAttribute('data-theme', saved); }
            } catch (e) { /* aynı */ }
            updateThemeButton();
          }
          function toggleDensity() {
            var dense = document.documentElement.getAttribute('data-density') === 'compact';
            var next = dense ? 'comfortable' : 'compact';
            document.documentElement.setAttribute('data-density', next);
            try { window.localStorage.setItem('coverdict-report-density', next); } catch (e) { /* aynı */ }
            updateDensityButton();
          }
          function updateDensityButton() {
            var btn = document.getElementById('density-toggle');
            if (!btn) { return; }
            var compact = document.documentElement.getAttribute('data-density') === 'compact';
            btn.textContent = compact ? 'Rahat görünüm' : 'Sık\\u0131 görünüm';
          }
          function restoreDensity() {
            try {
              var saved = window.localStorage.getItem('coverdict-report-density');
              if (saved === 'compact') { document.documentElement.setAttribute('data-density', 'compact'); }
            } catch (e) { /* aynı */ }
            updateDensityButton();
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
          function applyDeepLink() {
            if (location.hash.indexOf('#f-') !== 0) { return; }
            var target = document.getElementById(location.hash.slice(1));
            if (!target) { return; }
            var section = target.closest('details.report-section');
            if (section) { section.open = true; }
            var group = target.closest('.finding-group');
            if (group) { group.open = true; }
            window.requestAnimationFrame(function () {
              target.scrollIntoView({ block: 'center' });
              target.classList.add('deep-link-highlight');
              window.setTimeout(function () { target.classList.remove('deep-link-highlight'); }, 2000);
            });
          }

          // ---------- Boot ----------
          function buildFooter() {
            var f = el('footer');
            f.appendChild(el('span', { text: 'coverdict ' + DATA.meta.toolVersion + ' \\u00b7 schema ' + DATA.meta.schemaVersion + ' \\u00b7 tek dosya, çevrimdışı' }));
            f.appendChild(el('span', { text: DATA.meta.generatedAt }));
            return f;
          }

          function init() {
            var app = document.getElementById('app');
            app.appendChild(buildTopbar());
            var main = el('main');

            main.appendChild(buildSection('run', 'Koşu', '', renderRun()));
            main.appendChild(buildSection('findings', 'Bulgular', DATA.findings.items.length + ' bulgu', renderFindings()));
            main.appendChild(buildSection('coverage', 'Kapsama', '', renderCoverage()));
            main.appendChild(buildSection('changed-files', 'Değişen dosyalar', DATA.changedFiles.length + ' dosya', renderChangedFiles()));
            if (DATA.mutation) {
              main.appendChild(buildSection('mutation', 'Mutasyon kanıtı (L3)', '', renderMutation()));
            }
            if (DATA.fileCoverage) {
              main.appendChild(buildSection('file-coverage', 'Dosya bazlı kapsama', fmtInt(DATA.fileCoverage.totalFiles) + ' dosya', renderFileTree()));
            }
            if (DATA.perTest) {
              main.appendChild(buildSection('per-test', 'Test bazlı kanıt (L2)', '', renderPerTest()));
            }
            if (DATA.warnings.length) {
              main.appendChild(buildSection('warnings', 'Uyarılar', String(DATA.warnings.length), renderReasonList(DATA.warnings)));
            }
            if (DATA.incompleteReasons.length) {
              main.appendChild(buildSection('incomplete', 'Eksik nedenler', String(DATA.incompleteReasons.length), renderReasonList(DATA.incompleteReasons)));
            }

            app.appendChild(main);
            app.appendChild(buildFooter());

            restoreTheme();
            restoreDensity();
            var search = document.getElementById('global-search');
            if (search) { search.addEventListener('input', debounce(applyFilter, 60)); }
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
