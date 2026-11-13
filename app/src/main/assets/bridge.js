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

  var listeners = [];
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
    /* Felülírja a gamepad-rész (M3); addig minden játék billentyűt kap. */
    pageUsesGamepad: function () { return false; },
    onInput: function (fn) { listeners.push(fn); },
    sendKey: sendKey
  };
  Object.defineProperty(window, '__gtv', { value: api, configurable: false, enumerable: false, writable: false });
})();
