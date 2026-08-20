# Store asset sources

Committed Play graphics live in `fastlane/metadata/android/en-US/images/`.

Regenerate:

```bash
python3 scripts/generate_icons.py
node store/capture-assets.mjs
```

`capture-assets.mjs` needs Playwright (not a Gradle dependency). HTML mockups in `store/html/` match the Compose UI for listing screenshots — they are not a substitute for device QA before production.
