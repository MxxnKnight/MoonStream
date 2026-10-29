# MoonStream Backend Contract

This is the deal between the app's screens and Telegram. Written for moon
(the Director), not for devs.

## The idea in one paragraph

The app never talks to Telegram directly. It only talks to a **contract**
(a fixed list of promises) defined in
`app/src/main/java/com/moon/moonstream/backend/MoonStreamBackend.kt`.
Right now a **fake** version of that contract is plugged in
(`FakeMoonStreamBackend.kt`) — pretend channels, pretend files, everything
works, nothing is real. When the real Telegram piece is ready, it gets
plugged in at one single spot (`BackendProvider.install(...)`) and every
screen goes real without changing.

## The split

| Piece | Who | Notes |
|---|---|---|
| All screens (Library, Detail, Channels, Settings, …) | Assistant | Only speak to the contract. Done. |
| The contract itself (`MoonStreamBackend`) | Assistant | Frozen. Don't change it without both sides agreeing. |
| The fake (`FakeMoonStreamBackend`) | Assistant | Done. Deleted or ignored once the real one lands. |
| **The real Telegram implementation** | **moon** | **Your part, in Spck.** |
| The real login screens (phone → code → password) | moon | Your part — they drive your implementation. |

The assistant cannot help with the real Telegram/login code — not writing
it, not step-by-step guidance. That part is genuinely yours.

## What your implementation must provide

A class that implements `MoonStreamBackend` — i.e. it keeps these promises:

1. **`authState`** — a live signal saying `LOGGED_OUT` or `READY`.
   The app watches it: logged out → login screens; ready → main screens.
   Your login flow flips it to `READY` when the session is live
   (including on cold start, when you restore a saved session silently).
2. **`listChannels()`** — every channel/group the account can read.
   Just id + title each. Empty list is fine (never null).
3. **`fetchMessages(channelId, fromMessageId, limit)`** — file messages
   from a channel, newest first. Skip text/stickers/service messages.
   `fromMessageId` pages backwards (pass the last id you saw).
4. **`readFileRange(channelId, messageId, offset, length)`** — raw file
   bytes for the future streaming server. Random access: any offset.
5. **`getMe()`** — the logged-in user (name, @username, id, phone)
   for the Account screen. Only called when `READY`.
6. **`logout()`** — kills the session, wipes stored secrets, flips
   `authState` back to `LOGGED_OUT`.

Rules: never crash the app from these — return empty lists on failure and
let the UI show its empty states. Never log secrets. API ID/hash and
session data live in YOUR module's private storage, never in the repo,
never in chat.

## How it plugs in

Your module, after its login flow completes (or a saved session is
restored), calls:

```kotlin
BackendProvider.install(YourRealBackend(...))
```

Once. After that call the fake is gone and the app is live. The current
fake login/logout buttons keep working until then, so the app is always
testable.

## What the assistant will build against the contract

Phase 3 (indexing: scan channels → parse filenames → match metadata →
library), Phase 4 (in-phone server), Phase 5 (streaming) — all written
against `MoonStreamBackend`, verified against the fake. When your
implementation lands, they run against the real thing unchanged.
