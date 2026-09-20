(() => {
  'use strict';

  const isReceptionMode = () => {
    try {
      const url = new URL(location.href);
      if (url.hostname !== 'comunicacao.adventistasbotujuru.org') return false;

      const requestedTvMode =
        url.searchParams.get('ambiente') === 'recepcao' ||
        url.searchParams.get('tv') === '1';

      if (requestedTvMode) {
        sessionStorage.setItem('comunicacaoTvMode', '1');
      }

      return requestedTvMode || sessionStorage.getItem('comunicacaoTvMode') === '1';
    } catch (_) {
      return false;
    }
  };

  if (!isReceptionMode()) return;

  const normalize = (value) => (value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase();

  const installTvStyle = () => {
    if (document.getElementById('comunicacao-tv-style')) return;
    const style = document.createElement('style');
    style.id = 'comunicacao-tv-style';
    style.textContent = `
      html, body { overscroll-behavior: none !important; }
      a:focus, button:focus, input:focus, select:focus, textarea:focus,
      [role="button"]:focus, [tabindex]:focus {
        outline: 5px solid #D8B15A !important;
        outline-offset: 4px !important;
        box-shadow: 0 0 0 3px rgba(107,16,42,.75) !important;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  };

  const visible = (el) => {
    if (!el) return false;
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return r.width > 1 && r.height > 1 && s.visibility !== 'hidden' && s.display !== 'none' && Number(s.opacity || 1) !== 0;
  };

  const focusables = () => {
    const selector = 'a[href],button,input,select,textarea,[role="button"],[tabindex]';
    const items = [...document.querySelectorAll(selector)].filter(el => {
      if (!visible(el) || el.disabled) return false;
      const ti = el.getAttribute('tabindex');
      return ti !== '-1';
    });

    document.querySelectorAll('[role="button"]').forEach(el => {
      if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '0');
    });

    return items;
  };

  const center = (r) => ({x: r.left + r.width / 2, y: r.top + r.height / 2});

  const moveFocus = (direction) => {
    const list = focusables();
    if (!list.length) return;

    let current = document.activeElement;
    if (!list.includes(current)) {
      const first = list.find(el => visible(el));
      if (first) {
        first.focus({preventScroll: true});
        first.scrollIntoView({block: 'center', inline: 'center', behavior: 'smooth'});
      }
      return;
    }

    const a = center(current.getBoundingClientRect());
    let best = null;
    let bestScore = Infinity;

    for (const candidate of list) {
      if (candidate === current) continue;
      const b = center(candidate.getBoundingClientRect());
      const dx = b.x - a.x;
      const dy = b.y - a.y;

      let primary, secondary, valid = false;
      if (direction === 'ArrowRight' && dx > 8) { valid = true; primary = dx; secondary = Math.abs(dy); }
      if (direction === 'ArrowLeft'  && dx < -8) { valid = true; primary = -dx; secondary = Math.abs(dy); }
      if (direction === 'ArrowDown'  && dy > 8) { valid = true; primary = dy; secondary = Math.abs(dx); }
      if (direction === 'ArrowUp'    && dy < -8) { valid = true; primary = -dy; secondary = Math.abs(dx); }
      if (!valid) continue;

      const score = primary * 10 + secondary * 2 + Math.hypot(dx, dy);
      if (score < bestScore) {
        bestScore = score;
        best = candidate;
      }
    }

    if (best) {
      best.focus({preventScroll: true});
      best.scrollIntoView({block: 'center', inline: 'center', behavior: 'smooth'});
    }
  };

  const maybeOpenRestrictedAccess = () => {
    // Se já há campo de senha, o login já está aberto.
    if ([...document.querySelectorAll('input')].some(i => normalize(i.type) === 'password' && visible(i))) {
      const firstInput = [...document.querySelectorAll('input')].find(visible);
      if (firstInput && document.activeElement === document.body) firstInput.focus();
      return;
    }

    // Evita clicar repetidamente no mesmo carregamento.
    if (sessionStorage.getItem('comunicacaoTvRestrictedOpened') === '1') return;

    const candidates = [...document.querySelectorAll('a,button,[role="button"]')]
      .filter(visible)
      .filter(el => normalize(el.innerText || el.textContent || el.getAttribute('aria-label')).includes('acesso restrito'));

    if (candidates.length) {
      sessionStorage.setItem('comunicacaoTvRestrictedOpened', '1');
      setTimeout(() => candidates[0].click(), 350);
    }
  };

  const enhance = () => {
    installTvStyle();
    maybeOpenRestrictedAccess();
    focusables();
  };

  document.addEventListener('keydown', (event) => {
    if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key)) {
      event.preventDefault();
      moveFocus(event.key);
    }
  }, true);

  const observer = new MutationObserver(() => {
    clearTimeout(window.__comunicacaoTvTimer);
    window.__comunicacaoTvTimer = setTimeout(enhance, 120);
  });

  observer.observe(document.documentElement, {subtree: true, childList: true, attributes: true});
  enhance();
  setTimeout(enhance, 800);
  setTimeout(enhance, 1800);
})();
