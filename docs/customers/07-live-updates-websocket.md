# Feature C7 — Live updates via WebSocket

## What we built

When you (or anyone else) creates, updates, or deletes a customer,
every other browser tab with the customer list open automatically
refreshes within a fraction of a second — no manual reload, no
polling.

Backend pushes a small `CustomerEvent` on the `/topic/customers`
STOMP destination after each successful mutation. Every connected
client subscribes to that topic and reloads its list when an event
arrives.

This is the customer-side application of the F10 employee pattern.
All the heavy lifting — `spring-boot-starter-websocket`, the
`WebSocketConfig` setup, the proxy `ws: true` setting, the
`@stomp/stompjs` dependency — already exists from F10. C7 just adds
the customer event type, a broadcast helper, and a per-entity
events service.

Files touched:

- `classicmodels-backend/src/main/java/.../realtime/CustomerEvent.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerService.java`
- `classicmodels-ui/src/app/realtime/customer-events.service.ts` (new)
- `classicmodels-ui/src/app/customers/customer-list.component.ts`

No backend config changes — the existing WebSocket endpoint at `/ws`,
the `/topic/*` broker, and the JWT-auth carve-out for `/ws/**` all
cover customers automatically. Same for the `proxy.conf.json` and
the STOMP client dependency.

## Why this is worth learning

This feature is short because it's the *second* time we've done it.
F10 carried the conceptual weight (WebSocket vs HTTP, STOMP frames,
broker topology, Subject vs BehaviorSubject). C7 is mostly the
exercise of "now apply that pattern to a different entity," which
is where the muscle memory consolidates.

The new lesson, specific to Customer: **even live updates make the
SOFT-vs-DELETED distinction collapse**. From the list's perspective,
a soft-delete and a hard-delete both result in "the row disappears
from the active list." Broadcasting `DELETED` for a `SOFT` deletion
keeps the client logic uniform — "row hidden" and "row gone" are
the same thing to the client.

## Background

### Recap: WebSocket + STOMP

WebSocket is a persistent, bidirectional connection between browser
and server, established via an HTTP "Upgrade" request. STOMP (Simple
Text Oriented Messaging Protocol) is a small frame-based protocol on
top, giving us "subscribe to topic" and "send to destination"
semantics.

```
Browser   ── WebSocket connect ── ws://host/api/v1/ws
Browser   ── STOMP CONNECT ────►
Browser   ── STOMP SUBSCRIBE → /topic/customers
                                ...
Server    ── STOMP MESSAGE ◄───  destination=/topic/customers
                                 body={"type":"UPDATED","customerNumber":103}
Browser → fires events$.next() → every component listening fires load()
```

For the conceptual depth see F10's doc; this section is just a
reminder of the moving parts.

References:

- [MDN — WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
- [STOMP Protocol Specification](https://stomp.github.io/stomp-specification-1.2.html)
- [Spring docs — WebSocket / STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)

### Two topics, two events services, one connection

Each entity gets its own `/topic/...` destination
(`/topic/employees`, `/topic/customers`). Each destination needs
its own subscription. So the frontend has **one events service per
entity**:

- `EmployeeEventsService` subscribes to `/topic/employees`
- `CustomerEventsService` subscribes to `/topic/customers`

Currently each service opens its *own* STOMP client and connection.
Two browser-to-server WebSocket connections. That's slightly wasteful
but very easy to reason about, and it scales linearly — adding a
third entity adds one more service and one more connection. If
connection count ever becomes a bottleneck (say 10+ entities with
real-time events), the right refactor is a single shared
`StompClientService` with multiple `subscribe(topic, callback)`
calls. Two connections aren't worth that yet.

### Why broadcast `DELETED` for soft deletes too

The customer service emits the same `DELETED` event whether the user
picked SOFT or DEEP_CASCADE. From the list's perspective these are
indistinguishable: both result in the row disappearing from the
active list (SOFT because of the `WHERE active = 1` filter, hard
delete because the row is gone). Forcing the client to reason about
the difference would be making it solve a problem that doesn't
exist.

There's a small cost: a detail page open on the soft-deleted
customer doesn't get a specific "this customer is now soft-deleted"
event — it just gets `DELETED`. If the detail page subscribed to
events later (it currently doesn't), the page would have to re-fetch
the customer to discover the actual state (soft-deleted with
`active=0` vs. truly gone with 404). For the list page this is
moot.

### `Subject<T>` not `BehaviorSubject<T>` not `ReplaySubject<T>`

| Variant | Replays past values to new subscribers | Has an "initial" value |
|---|---|---|
| `Subject` | No | No |
| `BehaviorSubject` | Yes (only the most recent) | Yes (required) |
| `ReplaySubject(N)` | Yes (last N) | No |

For event streams we want `Subject`. A late subscriber should
receive only events that happen *after* it subscribed; replaying a
stale "Customer 103 was UPDATED two minutes ago" would trigger a
reload for no reason. The "moment in time" semantics of `Subject`
match what events naturally represent.

Reference: [RxJS — Subject types](https://rxjs.dev/guide/subject)

### Subscription lifecycle in components

The events service is `providedIn: 'root'` — it lives for the entire
app session, with one STOMP client. Components subscribe and
unsubscribe individually:

```ts
ngOnInit() {
  this.eventsSub = this.liveEvents.events$.subscribe(event => {
    this.load();
  });
}

ngOnDestroy() {
  this.eventsSub?.unsubscribe();
}
```

The `unsubscribe()` is essential. Without it, a component that's
been navigated away from still triggers `this.load()` every time an
event arrives — making HTTP calls against a component that no longer
exists, leaking memory, and (worse) potentially stomping on the
state of whatever component is currently displayed.

There are fancier patterns — `takeUntilDestroyed()` from
`@angular/core/rxjs-interop`, the `async` pipe with auto-cleanup —
but the explicit subscribe/unsubscribe pair is the fundamental form.
Once you've internalised the lifecycle, the shortcuts make sense as
syntactic sugar.

References:

- [Angular — `OnDestroy`](https://angular.dev/api/core/OnDestroy)
- [Angular blog — `takeUntilDestroyed`](https://blog.angular.io/) — the modern shortcut

### Why a refresh, not a patch?

When the event arrives saying "customer 103 was UPDATED," the
client could either:

1. **Patch in place** — fetch only customer 103 and replace it in
   the local rows array.
2. **Refresh** — call `load()` to re-fetch the current page.

We pick (2). Reasons:

- The list is paged + sorted + filtered. The update might have
  changed `customerName` (which sorts), or the customer might no
  longer match the search filter, or the row that just appeared
  might push another row off the page boundary. Patching in place
  produces incorrect order or stale rows.
- The cost is one HTTP call per event (debounced naturally by the
  frequency of mutations). At normal usage levels this is
  negligible.

For very chatty backends or very large pages, patch-in-place becomes
worth the complexity. At our scale, refresh is correct and dead
simple.

### Why no broadcast on `createBulk`?

`createBulk` is an admin operation (data import, fixture seeding).
Broadcasting N events for a 500-row import would flood every
connected client with N HTTP requests. The cost of skipping the
broadcast is that bulk-imported customers don't appear in open lists
until the user does something — they trigger their own next refresh
naturally on page change, sort change, or search.

If a use case ever needs "live update during bulk import," the
right pattern is a single "BULK_CREATED" event the client treats
specially (one refresh after the whole bulk completes), not N
individual events.

## The code, walked through

### The event record

```java
public record CustomerEvent(Type type, int customerNumber) {
    public enum Type { CREATED, UPDATED, DELETED }
}
```

Two fields. Sticking to the bare minimum keeps the wire format
schema-stable across backend changes — adding "who did it" or
"what fields changed" later is a non-breaking schema addition,
because clients ignore unknown JSON keys.

### Service-side broadcast

```java
private final SimpMessagingTemplate events;

public CustomerService(..., SimpMessagingTemplate events) {
    this.events = events;
}

private void broadcast(CustomerEvent.Type type, int customerNumber) {
    events.convertAndSend("/topic/customers",
            new CustomerEvent(type, customerNumber));
}
```

Spring auto-wires `SimpMessagingTemplate` as long as
`spring-boot-starter-websocket` is on the classpath (already pulled
in for employees in F10). `convertAndSend` handles JSON serialisation
via Jackson by default, so we just hand it a `CustomerEvent` and it
emerges on the wire as `{"type":"UPDATED","customerNumber":103}`.

### Frontend subscription

```ts
this.client.subscribe('/topic/customers', (message: IMessage) => {
  try {
    const event: CustomerEvent = JSON.parse(message.body);
    this.events$.next(event);
  } catch (e) {
    console.error('Bad customer event payload', message.body, e);
  }
});
```

The `try/catch` around the parse is paranoia — if the backend ever
sent malformed JSON (server bug, version mismatch), the rest of the
client should keep working. We log and drop instead of letting the
exception surface into RxJS, which would terminate the `events$`
stream and prevent further events from being delivered.

### List component subscription

```ts
this.eventsSub = this.liveEvents.events$.subscribe(event => {
  console.log('Live customer event:', event);
  this.load();
});
```

The `console.log` stays in production code as a debugging aid — it's
behind the WebSocket connection's status logs anyway, and lets you
verify in the browser console that events are flowing.

`this.load()` re-uses the existing pagination/sort/search state. The
right page, sorted the right way, filtered to the right query — same
as if the user had clicked Refresh.

## How to test

### Single-tab — the boring case

1. Restart the backend so the new broadcast code is loaded.
2. Open the customer list. Open browser dev tools → console.
3. You should see `STOMP connected, subscribed to /topic/customers`.
4. Edit a customer → save. After the form's success navigation,
   the list reloads. (Could be the form's own navigation, could
   be the WebSocket event — both work.) The console shows the
   live event.

### Two tabs — the interesting case

This is the cleanest test:

1. Open the customer list in **window A**.
2. Open the customer list in **window B**.
3. In **window B**: edit any customer's name, save.
4. Within ~100ms, **window A**'s list refreshes — the new name is
   visible. Window A's console shows
   `Live customer event: {type: "UPDATED", customerNumber: 103}`.

### Soft delete → DELETED event

1. Two windows open on the customer list.
2. In window A, soft-delete a customer.
3. Window B's list refreshes. The deleted row is gone.
4. Window B's console shows `{type: "DELETED", customerNumber: 103}`.

### Direct STOMP test

If you have `wscat` installed:

```bash
wscat -c 'ws://localhost:9090/api/v1/ws/websocket'
> CONNECT
  accept-version:1.2
  heart-beat:10000,10000
  ^@                    # null byte ends the frame

> SUBSCRIBE
  id:sub-0
  destination:/topic/customers
  ^@
```

(You'll have to type `^@` as Ctrl-V Ctrl-@.) Then trigger any
mutation in the UI; the wscat session shows the raw STOMP MESSAGE
frame.

### Server-side health check

```bash
curl http://localhost:9090/api/v1/actuator/health
# Should still be UP.
```

WebSocket isn't a health endpoint per se, but if the application
context is up, STOMP is up.

## What you just learned

- **Pattern reuse for live updates** — the F10 infrastructure
  (Spring `SimpMessagingTemplate`, `@stomp/stompjs`,
  `WebSocketConfig`) generalises to any new entity: one event
  record, one broadcast helper, one frontend events service.
- **Per-entity topic + per-entity events service** as the
  isolation pattern, with the trade-off being one connection per
  entity instead of one shared.
- **Broadcasting `DELETED` for soft-delete too** — because from the
  client list's perspective the row disappears either way, the
  uniform event keeps client logic simple.
- **`Subject` vs `BehaviorSubject`** for event streams — point-in-
  time semantics need `Subject`.
- **Subscription lifecycle in components** — `subscribe` in
  `ngOnInit`, `unsubscribe` in `ngOnDestroy`, otherwise leaks.
- **Refresh over patch-in-place** — sort/filter/page state means a
  refresh is the correct response to a single-row change.

## Study materials

### WebSocket / STOMP

- [MDN — WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455)
- [STOMP 1.2 spec](https://stomp.github.io/stomp-specification-1.2.html)

### Spring side

- [Spring docs — WebSocket / STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)
- [Spring docs — `SimpMessagingTemplate`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/SimpMessagingTemplate.html)
- [Baeldung — Spring WebSockets](https://www.baeldung.com/websockets-spring)

### `@stomp/stompjs`

- [@stomp/stompjs — Getting started](https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Getting%20Started.md.html)
- [@stomp/stompjs — Auto-reconnect](https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Auto%20Reconnect.md.html)

### RxJS Subjects

- [RxJS — Subject](https://rxjs.dev/api/index/class/Subject)
- [RxJS — Subject vs BehaviorSubject vs ReplaySubject](https://rxjs.dev/guide/subject)
- [Learn RxJS — Subjects](https://www.learnrxjs.io/learn-rxjs/subjects)

### Angular component lifecycle

- [Angular — Lifecycle hooks](https://angular.dev/guide/components/lifecycle)
- [Angular — `OnDestroy`](https://angular.dev/api/core/OnDestroy)
- [Angular — `takeUntilDestroyed`](https://angular.dev/api/core/rxjs-interop/takeUntilDestroyed) — the modern shortcut for unsubscribe-on-destroy
