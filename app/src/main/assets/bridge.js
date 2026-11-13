/* Mini Games TV – kontroller-híd. Az app minden oldal elején beinjektálja.
   A natív oldal a kontroller állapotát küldi:  __gtv.input(gombmaszk, [tengelyek], [lt, rt], [[típus, key, code], ...])
   Ebből két dolog lesz:
   1. billentyű-emuláció: szintetikus keydown/keyup a fókuszban lévő elemre (onnan felbuborékol a window-ig);
   2. virtuális gamepad (Gamepad API), lásd lent.
   Hálózati kérést nem küld, adatot nem gyűjt. */
(function () {
  'use strict';
  if (window.__gtv) return;

  var KEYCODES = {
    ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40,
    Enter: 13, Escape: 27, ' ': 32, Backspace: 8, Tab: 9
  };
  function legacyCode(key) {
    if (KEYCODES[key]) return KEYCODES[key];
    if (key.length === 1) return key.toUpperCase().charCodeAt(0);
    return 0;
  }

  function target() {
    var a = document.activeElement;
    if (a && a !== document.body && a !== document.documentElement) return a;
    return document.body || document.documentElement || document;
  }

  function clickable(el) {
    if (!el || el === document.body) return false;
    var t = el.tagName;
    if (t === 'BUTTON' || t === 'SUMMARY') return !el.disabled;
    if (t === 'A') return !!el.getAttribute('href');
    if (t === 'INPUT') return /^(button|submit|reset|checkbox|radio)$/i.test(el.type) && !el.disabled;
    var r = el.getAttribute && el.getAttribute('role');
    return r === 'button' || r === 'link' || r === 'checkbox' || r === 'tab' || r === 'menuitem';
  }

  /* Egy szintetikus billentyűesemény. A böngésző az ilyet nem tekinti valódi gombnyomásnak, ezért a
     „gombnyomás Enterrel/szóközzel” alapműködést mi pótoljuk, ha az oldal nem kezelte (preventDefault). */
  function sendKey(type, key, code) {
    var el = target();
    var kc = legacyCode(key);
    var ev = new KeyboardEvent(type, { key: key, code: code, bubbles: true, cancelable: true, composed: true });
    try {
      Object.defineProperty(ev, 'keyCode', { get: function () { return kc; } });
      Object.defineProperty(ev, 'which', { get: function () { return kc; } });
    } catch (e) { /* régi motor: marad 0 */ }
    var notCancelled = el.dispatchEvent(ev);
    if (type === 'keydown' && notCancelled && (key === 'Enter' || key === ' ') && clickable(el)) {
      el.click();
    }
    return notCancelled;
  }

  /* ------------------------------------------------------------ virtuális gamepad (Gamepad API)
     A böngészőmotor a kontrollert nem kapja meg (az app elnyeli), ezért a navigator.getGamepads()
     egy „standard” leképezésű virtuális gamepadot ad vissza, amelyet a natív oldal frissít.
     Az első kontrollereseménynél gamepadconnected esemény is jön. */
  var pad = { mask: 0, axes: [0, 0, 0, 0], trig: [0, 0], ts: 0, connected: false };
  var lastPoll = -1e9;
  var nativeGet = null;
  try { if (navigator.getGamepads) nativeGet = navigator.getGamepads.bind(navigator); } catch (e) { /* nincs */ }

  function snapshot() {
    var buttons = [];
    for (var i = 0; i < 17; i++) {
      var p = (pad.mask & (1 << i)) !== 0;
      var v = i === 6 ? pad.trig[0] : i === 7 ? pad.trig[1] : (p ? 1 : 0);
      buttons.push({ pressed: p, touched: p || v > 0, value: v });
    }
    return {
      id: 'Mini Games TV virtual controller (STANDARD GAMEPAD)',
      index: 0, connected: true, mapping: 'standard', timestamp: pad.ts,
      axes: pad.axes.slice(), buttons: buttons, vibrationActuator: null, hapticActuators: []
    };
  }

  function getGamepads() {
    lastPoll = performance.now();
    var real = [];
    try { real = nativeGet ? Array.prototype.slice.call(nativeGet()) : []; } catch (e) { real = []; }
    for (var i = 0; i < real.length; i++) if (real[i]) return real;
    return pad.connected ? [snapshot(), null, null, null] : [null, null, null, null];
  }
  try {
    Object.defineProperty(navigator, 'getGamepads', { value: getGamepads, configurable: true, writable: true });
  } catch (e) { /* nem írható felül: marad a böngészőé */ }

  function updatePad(mask, axes, triggers) {
    pad.mask = mask | 0;
    if (axes) pad.axes = [+axes[0] || 0, +axes[1] || 0, +axes[2] || 0, +axes[3] || 0];
    if (triggers) pad.trig = [+triggers[0] || 0, +triggers[1] || 0];
    pad.ts = performance.now();
    if (!pad.connected) {
      pad.connected = true;
      var ev = new Event('gamepadconnected');
      try { Object.defineProperty(ev, 'gamepad', { value: snapshot() }); } catch (e) { /* régi motor */ }
      window.dispatchEvent(ev);
    }
  }

  var listeners = [updatePad];
  var api = {
    version: 1,
    /* Natív hívja minden kontrollereseménynél. */
    input: function (mask, axes, triggers, keys) {
      for (var i = 0; i < listeners.length; i++) {
        try { listeners[i](mask, axes, triggers); } catch (e) { /* egy hibás figyelő ne állítsa meg a többit */ }
      }
      if (!keys) return;
      var suppress = api.pageUsesGamepad();
      for (var k = 0; k < keys.length; k++) {
        if (suppress) continue;
        sendKey(keys[k][0], keys[k][1], keys[k][2]);
      }
    },
    /* Ha az oldal az elmúlt fél másodpercben lekérdezte a gamepadot, maga fordítja billentyűre
       (a minigames közös gamepad modulja) – ilyenkor a billentyű-emuláció hallgat, nincs dupla esemény. */
    pageUsesGamepad: function () { return performance.now() - lastPoll < 500; },
    onInput: function (fn) { listeners.push(fn); },
    sendKey: sendKey
  };
  Object.defineProperty(window, '__gtv', { value: api, configurable: false, enumerable: false, writable: false });

  /* ------------------------------------------------------------ térbeli navigáció, csak a startlapon
     A D-pad (nyilak) a kártyák és gombok között lép, az A (Enter) megnyitja a kijelöltet.
     A minigames tárolót nem módosítja: a fókuszkeretet is innen kapja az oldal. */
  var START_PATH = /^\/minigames\/(index\.html)?$/;
  function onStartPage() {
    return location.hostname === 'nomadbeni.github.io' && START_PATH.test(location.pathname);
  }
  if (!onStartPage()) return;

  var SEL = 'a[href], button, [role="button"], [tabindex]:not([tabindex="-1"])';
  var FOCUS_CLASS = 'gtv-focus';
  var focused = null;

  function addStyle() {
    if (document.getElementById('gtv-style')) return;
    var s = document.createElement('style');
    s.id = 'gtv-style';
    s.textContent =
      '.' + FOCUS_CLASS + '{outline:4px solid #fff!important;outline-offset:4px!important;' +
      'box-shadow:0 0 0 9px rgba(63,210,187,.55)!important;border-radius:10px}' +
      '.card.' + FOCUS_CLASS + '{transform:scale(1.03)}' +
      '.card>a.' + FOCUS_CLASS + '{outline:none!important;box-shadow:none!important}' +
      '*{cursor:none!important}';
    (document.head || document.documentElement).appendChild(s);
  }

  function visible(el) {
    if (el.disabled || el.closest('[hidden],[inert],[aria-hidden="true"]')) return false;
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none';
  }

  /* Csak a minigames alá mutató link: a külső (pl. GitHub) linket az app úgyis elnyelné. */
  function inScope(el) {
    if (el.tagName !== 'A') return true;
    return el.href.indexOf(location.origin + '/minigames/') === 0 && el.target !== '_blank';
  }

  function candidates() {
    var all = document.querySelectorAll(SEL), out = [];
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') continue;
      if (visible(el) && inScope(el)) out.push(el);
    }
    return out;
  }

  /* A kártyán a link (a kártya egészére kifeszítve) a kiemelt elem, a kerete a kártyáé. */
  function frameOf(el) {
    var card = el.tagName === 'A' && el.parentElement && el.parentElement.classList.contains('card') ? el.parentElement : null;
    return card || el;
  }

  function setFocus(el) {
    if (!el) return;
    if (focused) {
      focused.classList.remove(FOCUS_CLASS);
      frameOf(focused).classList.remove(FOCUS_CLASS);
    }
    focused = el;
    el.classList.add(FOCUS_CLASS);
    frameOf(el).classList.add(FOCUS_CLASS);
    try { el.focus({ preventScroll: true }); } catch (e) { el.focus(); }
    var r = frameOf(el).getBoundingClientRect();
    var margin = Math.min(120, innerHeight / 4);
    if (r.top < margin || r.bottom > innerHeight - margin) {
      frameOf(el).scrollIntoView({ block: 'center', inline: 'nearest' });
    }
  }

  function current(list) {
    var a = document.activeElement;
    if (a && list.indexOf(a) >= 0) return a;
    if (focused && list.indexOf(focused) >= 0) return focused;
    return null;
  }

  function first(list) {
    var card = document.querySelector('.card>a[href]');
    return card && list.indexOf(card) >= 0 ? card : list[0];
  }

  /* A legközelebbi elem a megadott irányban. A keresztirányú eltérés többet számít, így rácsban
     sorban/oszlopban marad; ha a keresztirányban átfednek, az eltérés nulla. */
  function next(from, dir, list) {
    var fr = frameOf(from).getBoundingClientRect();
    var horiz = dir === 'ArrowLeft' || dir === 'ArrowRight';
    var sign = dir === 'ArrowRight' || dir === 'ArrowDown' ? 1 : -1;
    var best = null, bestScore = Infinity;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === from || frameOf(el) === frameOf(from)) continue;
      var r = frameOf(el).getBoundingClientRect();
      var main = horiz
        ? (sign > 0 ? r.left - fr.right : fr.left - r.right)
        : (sign > 0 ? r.top - fr.bottom : fr.top - r.bottom);
      var centerMain = horiz
        ? sign * ((r.left + r.right) - (fr.left + fr.right)) / 2
        : sign * ((r.top + r.bottom) - (fr.top + fr.bottom)) / 2;
      if (centerMain <= 4) continue;
      if (main < -4) main = 0;
      var overlap = horiz
        ? Math.min(r.bottom, fr.bottom) - Math.max(r.top, fr.top)
        : Math.min(r.right, fr.right) - Math.max(r.left, fr.left);
      var ortho = overlap > 0 ? 0 : horiz
        ? Math.abs((r.top + r.bottom) - (fr.top + fr.bottom)) / 2
        : Math.abs((r.left + r.right) - (fr.left + fr.right)) / 2;
      var score = Math.max(main, 0) + ortho * 3;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    return best;
  }

  function move(dir) {
    var list = candidates();
    if (!list.length) return;
    var cur = current(list);
    if (!cur) { setFocus(first(list)); return; }
    var n = next(cur, dir, list);
    if (n) setFocus(n);
    else if (dir === 'ArrowUp') scrollTo({ top: 0 }); // a lap tetején a fejléc is látsszon
  }

  /* Nyomva tartott irány: rövid szünet után ismétel. */
  var repeatTimer = 0, repeatKey = null;
  function stopRepeat() { clearTimeout(repeatTimer); repeatTimer = 0; repeatKey = null; }

  addEventListener('keydown', function (e) {
    var k = e.key;
    if (k === 'ArrowUp' || k === 'ArrowDown' || k === 'ArrowLeft' || k === 'ArrowRight') {
      if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
      e.preventDefault();
      if (repeatKey === k) return;
      stopRepeat();
      repeatKey = k;
      move(k);
      var tick = function () { move(k); repeatTimer = setTimeout(tick, 140); };
      repeatTimer = setTimeout(tick, 420);
    } else if (k === 'Enter' || k === ' ') {
      var list = candidates();
      if (!current(list)) { e.preventDefault(); setFocus(first(list)); }
      /* ha van kijelölt elem, a billentyű-emuláció megnyomja (click) */
    }
  }, true);
  addEventListener('keyup', function (e) { if (e.key === repeatKey) stopRepeat(); }, true);
  addEventListener('blur', stopRepeat);

  /* Az oldal a nyelvváltáskor és kedvencnél újrarajzolja a kártyákat: a kijelölés ne vesszen el. */
  addEventListener('focusin', function (e) {
    var el = e.target;
    if (el && el !== focused && el.matches && el.matches(SEL) && !/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)) {
      setFocus(el);
    }
  });

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', addStyle);
  else addStyle();
})();
