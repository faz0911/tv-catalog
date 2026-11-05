# TV Catalog

Android TV app that shows a 3-column card grid (thumbnail, title, subtitle, tag) loaded from a JSON feed you control.
Selecting a card opens its link inside the app. All links stay in the in-app browser.

## Using it
1. Host a JSON file (see `sample-feed.json`) anywhere reachable by HTTP(S), e.g. GitHub Pages or your own server.
2. Launch the app -> Menu (button at top, or MENU/Settings/Red key) -> **Set feed URL**.
3. Cards appear. Select one to open its link in-app. BACK returns to the catalog.
4. Menu -> **Open a web page by URL** opens any single page in the in-app browser.

## Feed format
```json
{
  "title": "Header text (optional)",
  "items": [
    { "title": "...", "subtitle": "...", "tag": "...", "image": "https://.../thumb.jpg", "url": "https://.../page" }
  ]
}
```
A bare top-level array of items also works. Relative `image`/`url` values are resolved against the feed URL.
`subtitle`, `tag` and `image` are optional.

## Inner pages
Opening a card loads its link in an in-app Chromium WebView, so the page renders exactly as the server sends it.
Remote control in the page view:
- **Cursor mode (default):** D-pad moves a pointer (hold to speed up), OK clicks, pushing against a screen edge scrolls.
- **MENU / Settings / Red:** Reload, toggle cursor mode (OFF = native focus navigation), text size 100-200%, desktop/mobile site, back to catalog.
- **BACK:** previous page, then back to the catalog. Fullscreen video exits first.

## Build
Push to GitHub -> Actions -> **Build APK** -> download `tv-webview-apk` (app-debug.apk).
Install: `adb connect <tv-ip>` then `adb install app-debug.apk`.

Grid width: change `COLUMNS` in `CatalogActivity.java`.
