(() => {
  const visible = (e) => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0'; };
  const cls = (e) => (typeof e.className === 'string' ? e.className : e.tagName);
  const all = [...document.querySelectorAll('body *')].filter(visible);
  const rgb = (c) => { const m = c.match(/rgba?\(([^)]+)\)/); if (!m) return null; const p = m[1].split(',').map(Number); return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 }; };
  const lin = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
  const lum = (c) => 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };

  // glass: composite every translucent ancestor over the two extreme canvas tones,
  // then judge the worse of the two.
  const BASE = [{ r: 245, g: 248, b: 252 }, { r: 227, g: 234, b: 244 }];
  const layersOf = (el) => {
    const layers = []; let n = el;
    while (n && n !== document.documentElement) {
      const s = getComputedStyle(n);
      let c = rgb(s.backgroundColor);
      if ((!c || c.a < 0.02) && s.backgroundImage.includes('gradient(')) {
        const stops = (s.backgroundImage.match(/rgba?\([^)]+\)/g) || []).map(rgb).filter(Boolean);
        if (stops.length) {
          const n = stops.length;
          c = {
            r: stops.reduce((a, x) => a + x.r, 0) / n,
            g: stops.reduce((a, x) => a + x.g, 0) / n,
            b: stops.reduce((a, x) => a + x.b, 0) / n,
            a: stops.reduce((a, x) => a + x.a, 0) / n,
          };
        }
      }
      if (c && c.a > 0.02) layers.push(c);
      n = n.parentElement;
    }
    return layers;
  };
  const compose = (layers, base) => {
    let out = { ...base };
    for (let i = layers.length - 1; i >= 0; i--) {
      const l = layers[i];
      out = { r: l.r * l.a + out.r * (1 - l.a), g: l.g * l.a + out.g * (1 - l.a), b: l.b * l.a + out.b * (1 - l.a) };
    }
    return out;
  };
  const worstBg = (el, fg) => {
    const layers = layersOf(el);
    let worst = null;
    for (const base of BASE) {
      const bg = compose(layers, base);
      const r = ratio({ ...fg, a: 1 }, bg);
      if (!worst || r < worst.r) worst = { r, bg };
    }
    return worst;
  };

  const textEls = all.filter((e) => !e.closest('.sr-only') && !e.classList.contains('sr-only') && [...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 0));
  const contrast = [];
  for (const e of textEls) {
    const s = getComputedStyle(e);
    const fg = rgb(s.color); if (!fg || fg.a < 0.4) continue;
    const size = parseFloat(s.fontSize), bold = parseInt(s.fontWeight, 10) >= 700;
    const need = (size >= 24 || (size >= 18.66 && bold)) ? 3 : 4.5;
    const w = worstBg(e, fg);
    if (w.r < need) contrast.push({ text: e.textContent.trim().slice(0, 34), cls: cls(e), size: +size.toFixed(1), ratio: +w.r.toFixed(2), need });
  }

  const small = textEls.filter((e) => parseFloat(getComputedStyle(e).fontSize) < 11).map((e) => ({ cls: cls(e), size: parseFloat(getComputedStyle(e).fontSize), text: e.textContent.trim().slice(0, 24) }));
  const clipped = all.filter((e) => { const r = e.getBoundingClientRect(); return r.right > innerWidth + 1 || r.left < -1; }).map((e) => ({ cls: cls(e), right: Math.round(e.getBoundingClientRect().right) }));

  const shadows = all.filter((e) => { const s = getComputedStyle(e); return s.boxShadow !== 'none' || s.textShadow !== 'none'; }).length;
  const gradients = all.filter((e) => getComputedStyle(e).backgroundImage.includes('gradient(')).length;
  const blurs = all.filter((e) => getComputedStyle(e).backdropFilter !== 'none' && getComputedStyle(e).backdropFilter !== '').length;
  const external = [...document.querySelectorAll('link[href],script[src],img[src]')].map((e) => e.getAttribute('href') || e.getAttribute('src')).filter((u) => /^(https?:)?\/\//.test(u));
  const unnamed = [...document.querySelectorAll('button,a[href],input,select,textarea')].filter((e) => {
    const name = e.getAttribute('aria-label') || e.textContent.trim() || e.getAttribute('title') || e.getAttribute('placeholder') || (e.labels && e.labels.length ? 'label' : '');
    return !name;
  }).map((e) => cls(e));
  const deadLinks = [...document.querySelectorAll('a[href="#"], a:not([href])')].map((e) => cls(e));
  const tall = [...document.querySelectorAll('body *')].filter(visible).map((e) => ({ cls: cls(e), bottom: Math.round(e.getBoundingClientRect().bottom), h: Math.round(e.getBoundingClientRect().height) })).filter((x) => x.bottom > innerHeight + 1).slice(0, 6);
  return {
    title: document.title, width: innerWidth, height: innerHeight,
    docOverflow: document.documentElement.scrollWidth > innerWidth,
    overflowPx: document.documentElement.scrollWidth - innerWidth,
    docVertical: document.documentElement.scrollHeight > innerHeight,
    verticalPx: document.documentElement.scrollHeight - innerHeight,
    clipped, external, smallText: small, lowContrast: contrast, unnamed, deadLinks, tall,
    material: { shadows, gradients, blurs },
    counts: { nodes: all.length, text: textEls.length }
  };
})()