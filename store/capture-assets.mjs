import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const androidRoot = path.resolve(here, "..");
const htmlDir = path.join(here, "html");
const lane = path.join(androidRoot, "fastlane/metadata/android/en-US/images");

const shots = [
  {
    scene: "connect",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/01_connect.png"),
  },
  {
    scene: "detected",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/02_detected.png"),
  },
  {
    scene: "ready",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/03_ready.png"),
  },
  {
    scene: "flashing",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/04_flashing.png"),
  },
  {
    scene: "success",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/05_success.png"),
  },
  {
    scene: "settings",
    form: "phone",
    size: { width: 1080, height: 1920 },
    out: path.join(lane, "phoneScreenshots/06_settings.png"),
  },
  {
    scene: "ready",
    form: "seven",
    size: { width: 1200, height: 1920 },
    out: path.join(lane, "sevenInchScreenshots/01_ready.png"),
  },
  {
    scene: "flashing",
    form: "seven",
    size: { width: 1200, height: 1920 },
    out: path.join(lane, "sevenInchScreenshots/02_flashing.png"),
  },
  {
    scene: "ready",
    form: "ten",
    size: { width: 1600, height: 2560 },
    out: path.join(lane, "tenInchScreenshots/01_ready.png"),
  },
  {
    scene: "success",
    form: "ten",
    size: { width: 1600, height: 2560 },
    out: path.join(lane, "tenInchScreenshots/02_success.png"),
  },
];

const screensUrl = pathToFileURL(path.join(htmlDir, "screens.html")).href;
const featureUrl = pathToFileURL(path.join(htmlDir, "feature-graphic.html")).href;

const browser = await chromium.launch();
const page = await browser.newPage();

async function shot(url, size, dest) {
  mkdirSync(path.dirname(dest), { recursive: true });
  await page.setViewportSize(size);
  await page.goto(url, { waitUntil: "networkidle" });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(250);
  await page.screenshot({ path: dest, type: "png" });
  console.log("wrote", dest);
}

await shot(featureUrl, { width: 1024, height: 500 }, path.join(lane, "featureGraphic.png"));
await shot(featureUrl, { width: 1024, height: 500 }, path.join(androidRoot, "store/feature-graphic.png"));

for (const item of shots) {
  const url = `${screensUrl}?scene=${item.scene}&form=${item.form}`;
  await shot(url, item.size, item.out);
}

await browser.close();
