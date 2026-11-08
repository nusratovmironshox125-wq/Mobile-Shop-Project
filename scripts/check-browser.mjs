import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.equal(process.env.RMS_TEST_ONLY, 'yes')
assert(process.env.RMS_PLAYWRIGHT_DIR && process.env.RMS_BROWSER_EXE, 'Set RMS_PLAYWRIGHT_DIR and RMS_BROWSER_EXE')
const require = createRequire(resolve(process.env.RMS_PLAYWRIGHT_DIR, 'package.json'))
const { chromium } = require('playwright')
const origin = 'http://127.0.0.1:18080'
const browser = await chromium.launch({ executablePath: process.env.RMS_BROWSER_EXE, headless: true })
const jsErrors = []
try {
  const page = await browser.newPage({ locale: 'ru-RU', colorScheme: 'light', viewport: { width: 1440, height: 950 } })
  page.on('pageerror', error => jsErrors.push(error.message))
  await page.goto(origin)
  const model = page.getByRole('link', { name: 'Смартфон тестовый', exact: true }).first()
  await model.waitFor()
  const search = page.getByRole('searchbox')
  await search.fill('смартфон')
  await page.waitForURL(url => url.searchParams.get('q') === 'смартфон')
  await model.waitFor()
  await page.getByRole('button', { name: 'Сбросить', exact: true }).click()
  await page.waitForURL(url => !url.searchParams.has('q'))
  for (const width of [320, 360, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      'Horizontal overflow at ' + width + ' px')
  }
  if (process.env.RMS_SCREENSHOT_DIR) {
    await page.screenshot({ path: resolve(process.env.RMS_SCREENSHOT_DIR, 'rms-desktop.png') })
  }

  await page.getByRole('button', { name: 'В избранное', exact: true }).last().click()
  await page.getByRole('link', { name: 'Избранное', exact: true }).click()
  await page.getByRole('heading', { name: 'Избранное', exact: true }).waitFor()
  await model.waitFor()
  await page.reload()
  await model.waitFor()
  await model.click()
  await page.getByRole('heading', { name: 'Смартфон тестовый', exact: true }).waitFor()
  await page.getByText('<script>throw new Error("XSS")</script>', { exact: false }).waitFor()
  await page.setViewportSize({ width: 390, height: 850 })
  await page.getByRole('button', { name: 'Оставить заявку', exact: true }).click()
  await page.getByLabel('Ваше имя').fill('Test Contact')
  await page.getByLabel('Телефон', { exact: true }).fill('+998 (90) 123-45-69')
  await page.getByRole('button', { name: 'Выбрать узбекский' }).click()
  await page.getByRole('button', { name: 'Mavzuni almashtirish' }).click()
  assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark')
  assert.equal(await page.getByLabel('Ismingiz').inputValue(), 'Test Contact')
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Ariza yuborish' }).click()
  await page.getByText('Do‘kon siz bilan bog‘lanadi', { exact: true }).waitFor()
  if (process.env.RMS_SCREENSHOT_DIR) {
    await page.screenshot({ path: resolve(process.env.RMS_SCREENSHOT_DIR, 'rms-mobile.png'), fullPage: true })
  }

  await page.goto(origin + '/admin/login')
  await page.getByLabel('Login').fill(process.env.OWNER_LOGIN)
  await page.getByLabel('Parol').fill(process.env.OWNER_PASSWORD)
  await page.getByRole('button', { name: 'Kirish', exact: true }).click()
  await page.getByRole('heading', { name: 'Do‘kon paneli' }).waitFor()
  await page.getByRole('link', { name: 'Arizalar', exact: true }).click()
  await page.getByText('Test Contact', { exact: false }).first().waitFor()
  assert.deepEqual(jsErrors, [])
  console.log('PASS: Edge ' + browser.version() + ', widths 320–1440 px, favorite reload, theme/locale, lead and admin login')
} finally {
  await browser.close()
}
