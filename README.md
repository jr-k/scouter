<div align="center">
  <h1>Scouter</h1>
  <img src="./scouter-logo.png" alt="Scouter logo" width="128">
  <p>Native Android interfaces, composed from JSON and displayed over any app.</p>
</div>

<p align="center">
  <img src="./screenshots/recipe1.jpg" alt="Strawberry tart recipe ingredients" width="30%">
  <img src="./screenshots/recipe2.jpg" alt="Strawberry tart recipe instructions" width="30%">
  <img src="./screenshots/recipe3.jpg" alt="Strawberry tart completed checklist" width="30%">
</p>

<hr>

<p align="center">
  <img src="./screenshots/flight.png" alt="Boarding pass overlay" width="30%">
  <img src="./screenshots/checklist.png" alt="Checklist overlay" width="30%">
  <img src="./screenshots/hold.png" alt="Press and hold overlay" width="30%">
</p>

<p align="center">
  <img src="./screenshots/contact.png" alt="Contact and navigation overlay" width="30%">
  <img src="./screenshots/focus.png" alt="Focus timer overlay" width="30%">
  <img src="./screenshots/telemetry.png" alt="Telemetry overlay" width="30%">
</p>

Scouter is a small server-driven UI renderer for Android. Send one JSON document through an intent, HTTP, or MCP; Scouter turns it into a native bottom sheet with no predefined screen or template.

Sheets slide up from the bottom and update in place when an ID is reused. When several are active, swipe the sheet horizontally; footer dots show the current position. They can also be minimized into local notifications and restored without opening the main activity.

The companion app keeps a persistent history of received cards. Its bottom navigation separates the card library, server settings, and Android access controls. Tapping a history item reopens that exact document without creating a duplicate history entry.

## What it handles

- Native text, cards, rows, columns, grids, buttons, progress, images, and dividers
- Checklists, links, addresses, phone numbers, maps, videos, and controlled web content
- Press-and-hold and slide actions with configurable vibration
- Per-view colors, spacing, typography, borders, opacity, and four independent corner radii
- Stable-ID replacement and navigation between multiple active overlays
- Lock-screen queueing through local notifications
- Android intents, authenticated REST, and embedded MCP

## Install

Requirements:

- Android 8.0 or newer
- Display-over-other-apps permission
- Notification permission on Android 13+

```bash
MAPS_API_KEY="your-restricted-google-maps-key" \
  gradle :app:assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Scouter once, grant the requested permissions, then use the built-in demo or send a document.

The Google Maps key is optional. When supplied, restrict it to package `app.scouter` and the signing certificate SHA-1.

## Send a view

Every document has a protocol version, a stable ID, and a recursive content tree:

```json
{
  "version": 1,
  "id": "delivery-42",
  "title": "Courier",
  "titleIcon": "directions",
  "notification": {
    "title": "Courier approaching",
    "message": "Arrival expected in 3 minutes",
    "icon": "navigation"
  },
  "presentation": {
    "dismissible": true,
    "minimizable": true,
    "radius": {
      "topStart": 30,
      "topEnd": 30,
      "bottomEnd": 6,
      "bottomStart": 6
    }
  },
  "content": {
    "type": "column",
    "gap": 14,
    "children": [
      {
        "type": "text",
        "value": "3 minutes away",
        "variant": "title"
      },
      {
        "type": "progress",
        "label": "Route",
        "value": 0.72,
        "showValue": true
      },
      {
        "type": "button",
        "id": "dismiss",
        "label": "Dismiss",
        "action": {"type": "close"}
      }
    ]
  }
}
```

The complete machine-readable contract lives in [`scouter.schema.json`](./scouter.schema.json). It includes all component properties, defaults, limits, aliases, lifecycle semantics, and a representative document. Unknown properties are rejected by the canonical schema so misspellings do not silently become no-ops.

### Android intent

Scouter listens for implicit broadcasts:

```kotlin
sendBroadcast(
    Intent("app.scouter.SHOW").apply {
        putExtra("scouter_id", "delivery-42")
        putExtra("scouter_json", documentJson)
    }
)
```

```bash
adb shell am broadcast \
  -a app.scouter.SHOW \
  --es scouter_id delivery-42 \
  --es scouter_json '{"version":1,"id":"delivery-42","content":{"type":"text","value":"Arriving now","variant":"title"}}'
```

Other actions:

- `app.scouter.CLOSE` with `scouter_id`
- `app.scouter.CLOSE_ALL`

### REST

The authenticated listener uses the host and port configured on the Connect page.

```bash
curl \
  -H "Authorization: Bearer $SCOUTER_TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @view.json \
  http://100.64.0.2:8765/show
```

Available routes:

- `GET /health`
- `POST /show`, optionally with `X-Scouter-Id`
- `POST /close/{id}`
- `POST /close-all`

Binding to `0.0.0.0` listens on every interface. If Tailscale is available, Scouter advertises its `100.64.0.0/10` address before Wi-Fi. A specific IPv4 address can be entered to force one interface.

### MCP

The same listener exposes stateless Streamable HTTP at `/mcp`.

```json
{
  "mcpServers": {
    "scouter": {
      "url": "http://100.64.0.2:8765/mcp",
      "headers": {
        "Authorization": "Bearer <token shown in Scouter>"
      }
    }
  }
}
```

Tools:

- `scouter_show(document, id?)`
- `scouter_close(id)`
- `scouter_close_all()`
- `scouter_get_schema()`
- `scouter_status()`

MCP controls Scouter views only. It does not inject arbitrary intents, read device data, simulate touch, or bypass Android permissions.

For an emulator:

```bash
adb forward tcp:8765 tcp:8765
```

Then connect REST or MCP to `http://127.0.0.1:8765`.

## UI language

Layout:

- `column`, `row`, `grid`, `box`, `card`
- `spacer`, `divider`

Content:

- `text`, `badge`, `progress`, `image`
- `checklist`
- `link`, `link_list`
- `address`, `address_list`
- `phone`, `phone_list`
- `action_list`
- `map`, `video`, `web`

Interaction:

- `button`
- `hold_button`
- `slide_action`

Actions can close the sheet, open a trusted HTTP(S) URL, open navigation, open the dialer, or send a package-targeted callback broadcast. Navigation opens Google Maps directly when available and falls back to the system chooser. External actions close the sheet when `closeAfter: true`; otherwise it stays visible unless the document is minimizable and the user enabled **Minimize after actions** in Preferences.

Every component accepts a `style` object for dimensions, weight, padding, margin, background, opacity, radius, and border. Interactive elements require stable semantic IDs.

Lists (`link_list`, `address_list`, `phone_list`, and `action_list`) accept `checkable: true`; in that mode the list itself needs a stable `id`. Each item may define its initial `checked` value. Scouter persists subsequent checkbox changes by document, list, and item ID, including after reopening a card from Home history. Regular `checklist` state is persisted the same way. A checklist item may also define an independent `action`, optional `buttonLabel`, and optional `actionIcon`; its action button does not toggle the checkbox, and the icon replaces visible button text when present.

Global list actions use normal composition: place a list and one or more `button`, `hold_button`, or `slide_action` components in the same `column`. The `navigate_route` action opens an ordered route with an optional origin and 1–10 address or coordinate stops; this allows an `address_list` to keep its per-address navigation while a separate button launches the complete itinerary.

## Overlay lifecycle

- A new ID adds another horizontally swipeable sheet and a corresponding footer dot.
- Reusing an ID replaces the current or queued document and selects it.
- Closing or swiping away Scouter's activity does not stop the detached foreground overlay service.
- Active sheets are persisted and restored after a process restart, application update, or device reboot. Automatic restart after boot can be disabled in Preferences.
- Minimizing stores the document and creates a configurable local notification.
- `presentation.minimizable` defaults to `false` and must be set to `true` for the Minimize control to appear; `dismissible` defaults to `true`.
- When Android is locked, incoming views are queued instead of forcing an overlay.
- Locked delivery requires notification permission. If Android denies notification posting, Scouter does not retain an unreachable pending entry; the document remains available in Home history.
- Tapping a card notification restores only the requested sheet over the current app; it never opens the companion activity.
- The grey handle follows the user's Pull-down action preference (Minimize or Close) and is hidden when the selected action is not allowed by the document.
- Long-pressing Close applies Close to every active sheet. Long-pressing Minimize minimizes only active sheets whose documents set `minimizable: true`.
- Tapping the darkened area outside the active card minimizes it by default. Preferences can change this behavior to Close or Ignore; document `minimizable` and `dismissible` flags are always respected.
- `CLOSE` removes both visible and queued views.

Android force-stop is the one exception: after **Force stop**, the operating system blocks every receiver and service until Scouter is opened manually again. Persisted sheets are restored at that point.

## Safety

- The network listener requires a bearer token.
- Payloads are limited to 256 KiB, 120 components, and 12 nesting levels.
- Remote web pages cannot use the JavaScript bridge.
- URL actions accept HTTP(S); phone actions open the dialer without placing a call.
- Sensitive or destructive work should always require an explicit confirmation.
- Use Scouter on a trusted LAN or through a private network such as Tailscale.

## Desktop console

The included local console can generate and send varied documents:

```bash
python3 desktop/scouter_console.py
```

Open `http://127.0.0.1:8787`, then enter the address and token shown in Scouter.

## Project references

- JSON contract: [`scouter.schema.json`](./scouter.schema.json)
- Agent skill: [`.cursor/skills/scouter-sdui/SKILL.md`](./.cursor/skills/scouter-sdui/SKILL.md)
- Android renderer: [`app/src/main/java/app/scouter/ui/ScouterRenderer.kt`](./app/src/main/java/app/scouter/ui/ScouterRenderer.kt)

