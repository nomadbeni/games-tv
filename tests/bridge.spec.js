// Playwright-tesztek a beinjektált kontroller-hídhoz (app/src/main/assets/bridge.js), asztali
// Chromiumban, az élő oldalon. A natív hívásokat (__gtv.input) a teszt szimulálja.
const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const BRIDGE = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/bridge.js'), 'utf8');
const HOME = 'https://nomadbeni.github.io/minigames/';

// Gombok bitjei a Gamepad API sorrendjében
const BTN = { A: 0, B: 1, X: 2, UP: 12, DOWN: 13, LEFT: 14, RIGHT: 15 };
const bit = (b) => 1 << b;

test.beforeEach(async ({ page }) => {
  page.__errors = [];
  page.on('pageerror', (e) => page.__errors.push(String(e)));
  // Ugyanaz, mint az appban: a híd minden dokumentum elején fut.
  await page.addInitScript(BRIDGE);
  await page.addInitScript(() => {
    window.__events = [];
    for (const t of ['keydown', 'keyup']) {
      addEventListener(t, (e) => window.__events.push(`${t}:${e.code}:${e.isTrusted ? 'T' : 's'}`), true);
    }
  });
});

test.afterEach(async ({ page }) => {
  expect(page.__errors, 'nincs JS-hiba').toEqual([]);
});

/** Egy natív kontrollergomb lenyomása és felengedése, úgy, ahogy a MainActivity.send() küldi. */
async function press(page, button, key, code) {
  await page.evaluate(([m, k, c]) => __gtv.input(m, [0, 0, 0, 0], [0, 0], [['keydown', k, c]]), [bit(button), key, code]);
  await page.waitForTimeout(40);
  await page.evaluate(([k, c]) => __gtv.input(0, [0, 0, 0, 0], [0, 0], [['keyup', k, c]]), [key, code]);
  await page.waitForTimeout(40);
}

test('a híd betöltődik, navigáció után is jelen van', async ({ page }) => {
  await page.goto(HOME);
  expect(await page.evaluate(() => typeof window.__gtv)).toBe('object');
  await page.goto(HOME + 'coloptorony/index.html');
  expect(await page.evaluate(() => typeof window.__gtv)).toBe('object');
});

test('billentyűs játék megkapja a nyilakat helyes key/code/keyCode mezővel', async ({ page }) => {
  await page.goto(HOME + 'coloptorony/index.html');
  const got = await page.evaluate(() => new Promise((res) => {
    addEventListener('keydown', (e) => res({ key: e.key, code: e.code, keyCode: e.keyCode }), { once: true });
    __gtv.input(1 << 14, [0, 0, 0, 0], [0, 0], [['keydown', 'ArrowLeft', 'ArrowLeft']]);
  }));
  expect(got).toEqual({ key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 });
});

test('virtuális gamepad: getGamepads és gamepadconnected', async ({ page }) => {
  await page.goto(HOME + 'coloptorony/index.html');
  expect(await page.evaluate(() => navigator.getGamepads().filter(Boolean).length)).toBe(0);
  const r = await page.evaluate(() => {
    let connected = null;
    addEventListener('gamepadconnected', (e) => { connected = e.gamepad && e.gamepad.mapping; });
    __gtv.input((1 << 0) | (1 << 15), [0.75, -1, 0, 0], [0.2, 0], []);
    const g = navigator.getGamepads()[0];
    return {
      connected, mapping: g.mapping, a: g.buttons[0].pressed, right: g.buttons[15].pressed,
      b: g.buttons[1].pressed, axes: g.axes, lt: g.buttons[6].value, n: g.buttons.length,
    };
  });
  expect(r).toEqual({
    connected: 'standard', mapping: 'standard', a: true, right: true, b: false,
    axes: [0.75, -1, 0, 0], lt: 0.2, n: 17,
  });
});

test('Gamepad API-s játékban nincs dupla esemény: a játék maga fordít billentyűre', async ({ page }) => {
  // A Tankőrség a minigames közös gamepad moduljával olvassa a kontrollert (A → szóköz).
  await page.goto(HOME + 'tankorseg/index.html');
  await page.waitForTimeout(300);
  await page.evaluate(() => { window.__events.length = 0; });
  await press(page, BTN.A, 'Enter', 'Enter');
  await page.waitForTimeout(100);
  const ev = await page.evaluate(() => window.__events);
  expect(ev).toContain('keydown:Space:s'); // a játék gamepad modulja küldte
  expect(ev.filter((e) => e.startsWith('keydown:Enter'))).toEqual([]); // a híd hallgatott
});

test('startlap: a D-pad kártyáról kártyára lép, látható kerettel, A megnyitja', async ({ page }) => {
  await page.setViewportSize({ width: 960, height: 540 }); // a TV WebView CSS-mérete
  await page.goto(HOME);
  await page.waitForSelector('.card>a');

  await press(page, BTN.RIGHT, 'ArrowRight', 'ArrowRight'); // első nyomás: kijelöli az első kártyát
  const first = await page.evaluate(() => document.activeElement.getAttribute('href'));
  expect(first).toMatch(/index\.html$/);
  expect(await page.evaluate(() => document.activeElement.parentElement.classList.contains('gtv-focus'))).toBe(true);

  await press(page, BTN.RIGHT, 'ArrowRight', 'ArrowRight');
  const second = await page.evaluate(() => document.activeElement.getAttribute('href') || document.activeElement.className);
  expect(second).not.toBe(first);
  expect(second).toMatch(/index\.html$/); // oldalra a következő kártya jön, nem a saját Kedvenc gombja

  // Lefelé: a következő sorba lép, a fókusz lejjebb kerül
  const y0 = await page.evaluate(() => document.activeElement.getBoundingClientRect().top + scrollY);
  await press(page, BTN.DOWN, 'ArrowDown', 'ArrowDown');
  await press(page, BTN.DOWN, 'ArrowDown', 'ArrowDown');
  const y1 = await page.evaluate(() => document.activeElement.getBoundingClientRect().top + scrollY);
  expect(y1).toBeGreaterThan(y0);

  // Vissza egy kártyára, és A (Enter) megnyitja a játékot
  for (let i = 0; i < 6; i++) {
    const isCard = await page.evaluate(() => !!document.activeElement.closest('.card') && document.activeElement.tagName === 'A');
    if (isCard) break;
    await press(page, BTN.LEFT, 'ArrowLeft', 'ArrowLeft');
  }
  const href = await page.evaluate(() => document.activeElement.href);
  expect(href).toContain('/minigames/');
  // Csak a lenyomás: a megnyitott oldal már új dokumentum, a felengedést nem kell követni.
  await Promise.all([
    page.waitForURL(href),
    page.evaluate((m) => __gtv.input(m, [0, 0, 0, 0], [0, 0], [['keydown', 'Enter', 'Enter']]), bit(BTN.A)),
  ]);
  expect(await page.evaluate(() => typeof window.__gtv)).toBe('object');
});

test('startlap: a külső (GitHub) link és a keresőmező nem kap fókuszt', async ({ page }) => {
  await page.setViewportSize({ width: 960, height: 540 });
  await page.goto(HOME);
  await page.waitForSelector('.card>a');
  const seen = new Set();
  await press(page, BTN.UP, 'ArrowUp', 'ArrowUp');
  for (let i = 0; i < 8; i++) {
    await press(page, BTN.UP, 'ArrowUp', 'ArrowUp');
    seen.add(await page.evaluate(() => document.activeElement.outerHTML.slice(0, 60)));
  }
  for (let i = 0; i < 4; i++) {
    await press(page, BTN.RIGHT, 'ArrowRight', 'ArrowRight');
    seen.add(await page.evaluate(() => document.activeElement.outerHTML.slice(0, 60)));
  }
  for (const s of seen) {
    expect(s).not.toContain('github.com');
    expect(s.startsWith('<input')).toBe(false);
  }
});
