---
name: scouter-sdui
description: Compose and send native Android overlay interfaces to the Scouter app through JSON intents, REST, or MCP. Use when a user asks to display, update, stack, replace, or close a Scouter HUD, bottom sheet, dynamic UI, or server-driven UI on Android.
---

# Scouter SDUI

Create content-specific native interfaces from JSON. Do not create permanent screens or templates: Scouter's generic renderer must remain the only installed rendering surface.

## Workflow

1. Decide whether to create a new sheet or replace one.
   - Reuse a stable `scouter_id` to replace and raise an existing sheet.
   - Use a new ID only for an independent sheet that should stack.
2. Compose a version 1 document from supported components.
3. Keep a visible close path. Use `presentation.dismissible: true` by default.
4. Send an implicit broadcast with action `app.scouter.SHOW`.
5. Check the device state:
   - Unlocked: inspect the overlay immediately.
   - Locked: verify that Scouter created a local notification. Unlock, tap it, then inspect the overlay.
6. Confirm the sheet, essential content, controls, clipping, and close behavior are visible.
7. If rendering fails, inspect `adb logcat -s ScouterOverlay ScouterReceiver`, fix the JSON, and resend with the same ID.
8. Announce success only after visual verification.

## Intent transport

Prefer Android intents when the sender runs on the same device. This path needs no network listener, address, port, or bearer token:

```text
sender → implicit broadcast → ScouterReceiver → JSON validation
       → overlay service → native bottom sheet
```

Scouter accepts these implicit broadcast actions:

| Action | Extra | Result |
| --- | --- | --- |
| `app.scouter.SHOW` | `scouter_json` required, `scouter_id` optional | Show, stack, or replace |
| `app.scouter.CLOSE` | `scouter_id` required | Close one visible or queued view |
| `app.scouter.CLOSE_ALL` | none | Close and clear everything |

`scouter_json` is the complete JSON document serialized as a string. `scouter_id` overrides the document's `id`. Reusing an ID replaces and raises that sheet; a distinct ID creates an independently stackable sheet.

## Show or replace

Send:

```text
action: app.scouter.SHOW
extra scouter_json: complete JSON document as a string
extra scouter_id: optional intent-level ID override
```

Example:

```bash
adb shell am broadcast -a app.scouter.SHOW \
  --es scouter_id scan-current \
  --es scouter_json '{"version":1,"id":"scan-current","title":"Scan","content":{"type":"column","gap":12,"children":[{"type":"text","value":"Target acquired","style":"title"},{"type":"button","id":"dismiss","label":"Dismiss","action":{"type":"close"}}]}}'
```

When Android is locked, Scouter stores this document and posts a local notification. Sending the same ID replaces the queued document. Tapping the notification restores only the bottom sheet over the current app, without opening Scouter's main activity; Android handles authentication when required.

For network delivery, send the same JSON to `POST http://<phone>:8765/show` with `Authorization: Bearer <token>` and optional `X-Scouter-Id`. Use the address and token displayed by Scouter. A wildcard `0.0.0.0` bind listens on every interface and advertises a detected Tailscale `100.64.0.0/10` address before Wi-Fi. On the emulator, run `adb forward tcp:8765 tcp:8765` and target `http://127.0.0.1:8765`.

## MCP transport

Use MCP when an agent or MCP-capable desktop client needs to discover and operate Scouter directly. The endpoint is `http://<phone>:8765/mcp` and uses the same bind host, port, and bearer token as REST. Read the exact endpoint and token from Scouter's Settings screen.

Example remote-server configuration:

```json
{
  "mcpServers": {
    "scouter": {
      "url": "http://127.0.0.1:8765/mcp",
      "headers": {
        "Authorization": "Bearer <scouter-token>"
      }
    }
  }
}
```

For the emulator, first run `adb forward tcp:8765 tcp:8765`. The transport is stateless Streamable HTTP with JSON responses.

Available tools:

- `scouter_get_schema`: retrieve the canonical protocol v1 JSON Schema. Call this before composing a document if the contract is not already available.
- `scouter_show`: pass the complete document as the `document` JSON object and an optional stable `id` override.
- `scouter_close`: pass one stable `id`.
- `scouter_close_all`: takes no arguments.
- `scouter_status`: inspect endpoint, app/protocol versions, and rendering capabilities.

An MCP show call follows the same validated path as REST and intents. When unlocked it opens the overlay; when locked it stores the view and posts its local notification. Reusing the ID replaces a visible or queued view. Tool errors are returned with `isError: true`; fix the document and call `scouter_show` again with the same ID.

Never ask the MCP server to control arbitrary Android features. Scouter MCP is intentionally limited to its own UI lifecycle and cannot bypass overlay, notification, lock-screen, or battery permissions.

### Tasker

Use Tasker's **Send Intent** action:

```text
Action: app.scouter.SHOW
Target: Broadcast Receiver
Extra: scouter_id:stable-screen-id
Extra: scouter_json:%document_json
Package: leave empty
```

Keep the broadcast implicit. `%document_json` must contain valid compact JSON, not a path to a file and not a nested Tasker object.

To close:

```text
Action: app.scouter.CLOSE
Target: Broadcast Receiver
Extra: scouter_id:stable-screen-id
```

### Android application

```kotlin
val intent = Intent("app.scouter.SHOW").apply {
    putExtra("scouter_id", "stable-screen-id")
    putExtra("scouter_json", documentJson)
}
sendBroadcast(intent)
```

The sender should be active or executing a user-visible automation. Recent Android versions may reject foreground-service starts initiated by a fully backgrounded app. If a vendor is stricter, grant Scouter the battery-optimization exemption from its Permissions menu.

## Close

```bash
adb shell am broadcast -a app.scouter.CLOSE --es scouter_id scan-current
adb shell am broadcast -a app.scouter.CLOSE_ALL
```

## Document contract

Required root fields:

```json
{
  "version": 1,
  "id": "stable-id",
  "content": {"type": "column", "children": []}
}
```

Optional root fields:

- `title`
- `titleIcon`
- `notification`: `title`, `message`, and semantic `icon`
- `theme`: `background`, `surface`, `text`, `muted`, `accent`, `danger`, `cornerRadius`, `fontScale`
- `presentation`: `dismissible`, `minimizable`, `showHandle`, `maxHeight`, `horizontalMargin`, `stackOffset`, `radius`

Colors use `#RRGGBB` or `#RRGGBBAA`.
For minimized or lock-screen delivery, set a concise `notification.title`, `notification.message`, and an icon from `scouter`, `info`, `warning`, `message`, `navigation`, `phone`, or `task`. When omitted, Scouter derives the title and message from the view content.
For visible UI, `titleIcon` and the `icon` field on text, badge, button, hold/slide controls, links, addresses and phones accept `null`, an icon name, or `{name, position, color, size, spacing}`. Names: `add`, `back`, `calendar`, `call`, `check`, `close`, `delete`, `directions`, `edit`, `email`, `favorite`, `home`, `info`, `link`, `list`, `location`, `lock`, `menu`, `message`, `more`, `notification`, `open`, `pause`, `person`, `phone`, `play`, `search`, `settings`, `share`, `star`, `task`, `time`, `unlock`, `warning`.
`presentation.radius` accepts one number or independent `topStart`, `topEnd`, `bottomEnd`, and `bottomStart` values from 0 to 160 dp.
Set `presentation.minimizable` to `true` to expose a Minimize control. It stores the current JSON as a local notification; tapping it restores the same view and stable ID directly over the current app without opening Scouter's main activity. Notification permission is required.

## Components

- `column`: `children`, optional `gap`, `align`
- `row`: `children`, optional `gap`, `align`
- `grid`: `children`, `columns`, optional `gap`, `horizontalGap`, `verticalGap`
- `box`: `child`, optional `align`
- `card`: `child`, optional `tone`, `padding`
- `text`: `value`, optional `variant`, `icon`, `color`, `fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `fontFamily`, `italic`, `align`, `maxLines`
- `button`: stable `id`, `label`, optional `icon`, `variant`, `color`, `pressedColor`, `pressedScale`, `textColor`, `enabled`, `action`
- `hold_button`: stable `id`, `label`, optional `icon`, `durationMs`, `completedLabel`, `color`, `trackColor`, `textColor`, `enabled`, `vibrateSequence`, `action`; release before completion cancels it
- `slide_action`: stable `id`, `label`, optional `icon`, `threshold`, `completedLabel`, `color`, `trackColor`, `textColor`, `enabled`, `vibrateSequence`, `action`
- `badge`: `label`, optional `icon`, `tone`
- `progress`: `value` between 0 and 1, optional `label`, `showValue`, `color`, `trackColor`
- `image`: `url`, optional `description`, `height`, `cornerRadius`, `contentScale`
- `checklist`: stable `id`, `items`, optional callback `action`
- `link`: stable `id`, `label`, HTTP(S) `url`, optional `icon`, `description`
- `link_list`: `links` with IDs, labels, URLs, and optional icons and descriptions
- `address`: stable `id`, `address`, optional `icon`, `label`, `description`, `latitude`, `longitude`, `buttonLabel`, `chooserTitle`, `closeAfter`
- `address_list`: `addresses` using the same fields
- `phone`: stable `id`, `phoneNumber`, optional `icon`, `label`, `description`, `buttonLabel`, `closeAfter`
- `phone_list`: `phones` using the same fields
- `action_list`: mixed `items` of type `address`, `phone`, or `link`
- `map`: `latitude`, `longitude`, optional `zoom`, `markerLabel`, `height`, `mapType`, `gestures`, `controls`; requires a build-time Google Maps SDK key
- `video`: HTTP(S) `url`, optional `autoplay`, `controls`, `height`
- `web`: local `html`, optional `javaScriptEnabled`, `height`
- `divider`: optional `color`
- `spacer`: `height`

Text variants: `display`, `title`, `subtitle`, `body`, `caption`, `mono`.

Button variants: `primary`, `secondary`, `ghost`, `danger`.

Tones: `accent`, `danger`, `surface`, `background`.

For `hold_button` and `slide_action`, `vibrateSequence` is an Android waveform in milliseconds: `[initialDelay, vibrate, pause, vibrate, ...]`. Keep it to at most 20 values, 5000 ms per value, and 15000 ms total. Their callback events are `hold_complete` and `slide_complete`.

Every component accepts a `style` object:

```json
{
  "width":"fill",
  "height":64,
  "minHeight":48,
  "maxHeight":120,
  "weight":1,
  "aspectRatio":1.5,
  "padding":{"horizontal":16,"vertical":12},
  "margin":{"start":8,"top":4,"end":8,"bottom":4},
  "background":"#17221C",
  "opacity":0.95,
  "radius":{"topStart":18,"topEnd":18,"bottomEnd":8,"bottomStart":8},
  "border":{"width":1,"color":"#C9FF3F"}
}
```

`padding` and `margin` also accept one number. `radius` accepts one number. `width` accepts `fill`, `wrap`, or a numeric dp value. Use `variant`, not uppercase transformations or decorative numbering, to establish hierarchy.

## Actions

Close:

```json
{"type":"close"}
```

System navigation chooser:

```json
{"type":"navigate","address":"12 rue de Charonne, Paris","chooserTitle":"Open directions"}
```

System dialer:

```json
{"type":"dial","phoneNumber":"+33 1 42 00 00 00"}
```

Open only a trusted HTTP(S) URL:

```json
{"type":"open_url","url":"https://example.com","closeAfter":true}
```

Send a callback:

```json
{
  "type":"broadcast",
  "action":"com.example.SCOUTER_EVENT",
  "package":"com.example",
  "extras":{"item_id":"42"},
  "closeAfter":false
}
```

Scouter adds these extras to callback broadcasts:

- `scouter_id`: document that emitted the event
- `element_id`: stable interactive element ID
- `event`: `click`, `change`, `hold_complete`, or `slide_complete`
- `value`: changed or generated value when relevant

Checklist changes use `event=change` with `value=true|false`. Hold completion sends its duration as `value`; slide completion sends `value=1`. Prefer a package-targeted callback so unrelated applications cannot receive it.

`navigate` opens an Android `geo:` chooser. `dial` opens the dialer but never places a call automatically. `open_url` accepts HTTP(S) only.

## Composition rules

- Design around the content, not a generic text dump.
- Use short semantic IDs such as `confirm_order`, `track_train`, or `dismiss_alert`.
- Put the primary action within the first visible viewport.
- Prefer native text, cards, progress, and buttons over a remote web page.
- Account for small screens and long text; avoid wide rows unless horizontal scrolling is acceptable.
- Re-send the complete document with the same ID for updates.
- Use intent delivery for same-device automation, MCP for agent-driven composition, and REST for a simple remote sender.
- Use a restrained palette with sufficient contrast; the accent should identify state and primary action.
- Never request credentials, payment details, or security codes in an overlay.
- Treat external data as untrusted JSON values. Do not construct JSON through unsafe string concatenation.
- Stay below 256 KiB, 120 components, and 12 nesting levels.
- Remember Android's Binder transaction limit. Keep intent JSON compact and reference remote media by URL.
- The receiver is exported so automation applications can reach it; any installed application can send it a document. Require confirmation before destructive or security-sensitive callbacks.

## Verification

After sending:

1. Check that the intended sheet is visible, correctly stacked or replaced, and not clipped.
2. Check title, icon, essential content, primary action, pressed state, and close/minimize controls.
3. Exercise callbacks, hold duration, slide threshold, vibration, navigation, or dialer when present.
4. For minimization, verify notification title, message, icon, and direct restoration over the current app.
5. On failure, inspect `adb logcat -s ScouterOverlay ScouterReceiver`, correct the JSON, and resend with the same ID.
