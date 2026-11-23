/* Signal Desk — minimal page behaviour for static design boards.
   No framework, no network. Interactions exist so states can be inspected. */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // Underlined tabs
  $$('[data-tabs]').forEach((group) => {
    const tabs = $$('[role="tab"]', group);
    const panels = $$('[data-tab-panel]', group.parentElement || document);
    tabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        tabs.forEach((t) => { t.classList.toggle('is-active', t === tab); t.setAttribute('aria-selected', String(t === tab)); });
        const id = tab.getAttribute('aria-controls');
        panels.forEach((p) => { p.hidden = p.id !== id; });
      });
    });
  });

  // Master/detail: selecting a row updates the inspector title
  $$('[data-master]').forEach((master) => {
    const detailId = master.getAttribute('data-master');
    const detail = document.getElementById(detailId);
    if (!detail) return;
    $$('tbody tr', master).forEach((row) => {
      row.addEventListener('click', (e) => {
        if (e.target.closest('button, input, a, label')) return;
        $$('tbody tr', master).forEach((r) => { r.classList.remove('is-selected'); r.setAttribute('aria-selected', 'false'); });
        row.classList.add('is-selected'); row.setAttribute('aria-selected', 'true');
        const title = row.getAttribute('data-title');
        const sub = row.getAttribute('data-sub');
        const t = $('[data-detail-title]', detail); if (t && title) t.textContent = title;
        const s = $('[data-detail-sub]', detail); if (s && sub) s.textContent = sub;
      });
    });
  });

  // Copy buttons: feedback only, nothing leaves the page
  $$('[data-copy]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const text = btn.getAttribute('data-copy') || '';
      try { await navigator.clipboard.writeText(text); } catch { /* file:// has no clipboard permission */ }
      const old = btn.getAttribute('aria-label') || '';
      btn.setAttribute('aria-label', '已复制');
      btn.classList.add('is-copied');
      setTimeout(() => { btn.classList.remove('is-copied'); btn.setAttribute('aria-label', old); }, 1200);
    });
  });

  // Row checkboxes drive the bulk bar and row state
  const selectAll = $('[data-select-all]');
  const rows = $$('[data-selectable]');
  const bulk = $('[data-bulk]');
  const sync = () => {
    const picked = rows.filter((r) => $('input[type="checkbox"]', r)?.checked);
    rows.forEach((r) => r.classList.toggle('is-checked', !!$('input[type="checkbox"]', r)?.checked));
    if (bulk) {
      bulk.hidden = picked.length === 0;
      const c = $('[data-bulk-count]', bulk); if (c) c.textContent = String(picked.length);
    }
    if (selectAll) selectAll.checked = picked.length === rows.length && rows.length > 0;
  };
  if (selectAll) selectAll.addEventListener('change', () => {
    rows.forEach((r) => { const cb = $('input[type="checkbox"]', r); if (cb) cb.checked = selectAll.checked; });
    sync();
  });
  rows.forEach((r) => $('input[type="checkbox"]', r)?.addEventListener('change', sync));
  sync();

  // ⌘K / Ctrl+K focuses the page search field (the hint in the toolbar is a promise)
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      const input = $('[data-search]');
      if (input) { e.preventDefault(); input.focus(); }
    }
  });
})();
