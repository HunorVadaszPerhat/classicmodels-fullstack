# Feature 10 — Live updates via WebSocket

## What we built

When you (or anyone else) creates, updates, or deletes an employee,
every other browser tab with the employee list open automatically
refreshes within a fraction of a second — no manual reload, no
polling.

The mechanism: the backend pushes a small event message on a
WebSocket topic after each successful mutation; every connected
client subscribes to that topic and reloads its list when an event
arrives.

This is the first time in this project we've used a **persistent
server-to-client connection** rather than the request-response
pattern HTTP gives us. WebSocket is the transport;
**STOMP** (a tiny pub-sub protocol on top) gives us the "topics +
subscriptions" semantics for free.

Files touched:

- `classicmodels-backend/pom.xml` — `spring-boot-starter-websocket`
- `classicmodels-backend/src/main/java/.../realtime/WebSocketConfig.java` (new)
- `classicmodels-backend/src/main/java/.../realtime/EmployeeEvent.java` (new)
- `classicmodels-backend/src/main/java/.../security/SecurityConfig.java` — permit `/ws/**`
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java` — broadcast on mutations
- `classicmodels-ui/package.json` — `@stomp/stompjs`
- `classicmodels-ui/src/proxy.conf.json` — `"ws": true`
- `classicmodels-ui/src/app/realtime/employee-events.service.ts` (new)
- `classicmodels-ui/src/app/employees/employee-list.component.ts` — subscribe + reload

## Why this is worth learning

Three concepts converge. **WebSockets** as a persistent connection
that lets the server push to the client (impossible with plain
HTTP). **STOMP** as the canonical messaging-protocol-on-WebSocket
that gives you topics, subscriptions, and frame structure without
inventing them yourself. **Pub-sub broadcast as a UI primitive** —
"backend says something changed, all clients react" is a pattern
you'll meet in dashboards, collaborative editors, chat apps, real-
time monitoring.

## Background

### WebSocket vs. HTTP

| HTTP                  | WebSocket             |
|-----------------------|-----------------------|
| Request / response    | Persistent, bidirectional |
| Client always initiates | Either side can send |
| Connection per request | One connection, many messages |
| Easy to scale         | Stateful per connection |

A WebSocket connection starts with an HTTP "Upgrade" request: client
sends `Connection: Upgrade, Upgrade: websocket`; server responds
`101 Switching Protocols` and from that point the connection
exchanges WebSocket frames instead of HTTP. The connection stays open
until either side closes it.

You'd use WebSocket whenever the server needs to PUSH to the client:
chat, live dashboards, collaborative editing, presence indicators,
real-time notifications, multiplayer games. Polling (client asking
"anything new yet?" every few seconds) is the HTTP fallback — works,
but burns bandwidth and adds latency.

References:

- [MDN — WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455)
- [Mozilla — WebSocket vs HTTP comparison](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)

### STOMP

WebSocket gives you bytes; STOMP gives you semantics. Specifically:

- **CONNECT** — start a STOMP session inside the WebSocket connection.
- **SUBSCRIBE** to a destination (e.g. `/topic/employees`).
- **SEND** to a destination.
- **MESSAGE** — what the server sends to subscribers.
- **DISCONNECT** — end the session.

Each frame is a small text payload with a verb, headers, and an
optional body. The syntax is human-readable, like HTTP:

```
SEND
destination:/topic/employees
content-type:application/json

{"type":"UPDATED","employeeNumber":1370}
```

Spring Boot has first-class STOMP support
(`spring-boot-starter-websocket`), and STOMP clients exist in every
major language. You don't have to use STOMP — you could send raw
JSON over WebSocket — but you'd reinvent topic routing, subscribe/
unsubscribe semantics, and ack/nack handling. STOMP is small enough
that it's almost always worth the dependency.

References:

- [STOMP Protocol Specification](https://stomp.github.io/stomp-specification-1.2.html)
- [Spring docs — WebSocket / STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)
- [Baeldung — Intro to WebSockets with Spring](https://www.baeldung.com/websockets-spring)

### `SimpMessagingTemplate`

Spring's helper for "send a message to a destination." Auto-wired
once `spring-boot-starter-websocket` is on the classpath. Use it
from any service to broadcast:

```java
@Service
public class EmployeeService {
    private final SimpMessagingTemplate events;

    public void notifyChange(int id) {
        events.convertAndSend("/topic/employees",
                new EmployeeEvent(Type.UPDATED, id));
    }
}
```

`convertAndSend` serializes the second argument to JSON via Jackson
(since spring-web is on the classpath) and pushes it as a STOMP
MESSAGE frame to every subscriber of `/topic/employees`. Twelve
characters of code, full broadcast.

References:

- [Spring docs — `SimpMessagingTemplate`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/SimpMessagingTemplate.html)

### `@stomp/stompjs` — the client side

The official STOMP-over-WebSocket client. The `Client` class wraps
the WebSocket lifecycle plus STOMP frames:

```ts
const client = new Client({
  brokerURL: 'ws://localhost:9090/api/v1/ws',
  reconnectDelay: 5000,
  onConnect: () => {
    client.subscribe('/topic/employees', message => {
      const event = JSON.parse(message.body);
      // ...
    });
  },
});
client.activate();   // opens connection
```

`reconnectDelay` is the auto-reconnect window — if the connection
drops (network blip, backend restart), the client automatically
re-establishes after that delay. We don't have to write retry logic.

References:

- [@stomp/stompjs documentation](https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html)

### Subject vs BehaviorSubject for events

Why `Subject` and not `BehaviorSubject`?

Events are point-in-time signals — "an employee changed *now*." A
`BehaviorSubject` would replay the last value to every new
subscriber, so a component that mounts after an event fired would
trigger a bogus reload from the stale event. `Subject` (without the
"Behavior" or "Replay" prefix) emits only to current subscribers
and forgets immediately, which is what we want.

References:

- [Learn RxJS — Subject](https://www.learnrxjs.io/learn-rxjs/subjects/subject)
- [Learn RxJS — BehaviorSubject](https://www.learnrxjs.io/learn-rxjs/subjects/behaviorsubject)

## The code, walked through

### Backend — the broadcast pattern

```java
public EmployeeResponseDTO create(EmployeeRequestDTO dto) {
    Employee saved = repo.save(mapper.toEntity(dto));
    var response = mapper.toResponseDTO(saved);
    broadcast(EmployeeEvent.Type.CREATED, response.employeeNumber());
    return response;
}

private void broadcast(EmployeeEvent.Type type, int employeeNumber) {
    events.convertAndSend("/topic/employees",
            new EmployeeEvent(type, employeeNumber));
}
```

One extra line per mutation method. The event payload is tiny — just
"this id changed" — because clients re-fetch their current page on
any event anyway. Keeping the event small means the schema doesn't
shift when we add fields to the employee record.

### Frontend — connect once, subscribe once, broadcast via Subject

```ts
@Injectable({ providedIn: 'root' })
export class EmployeeEventsService {
  readonly events$ = new Subject<EmployeeEvent>();
  private readonly client: Client;

  constructor() {
    this.client = new Client({
      brokerURL: this.buildBrokerUrl(),
      reconnectDelay: 5000,
      onConnect: () => {
        this.client.subscribe('/topic/employees', message => {
          this.events$.next(JSON.parse(message.body));
        });
      },
    });
    this.client.activate();
  }
}
```

`providedIn: 'root'` makes the service a singleton, so the entire
app shares one connection. Every component that wants live updates
subscribes to `events$`.

### List component — react to events

```ts
this.eventsSub = this.liveEvents.events$.subscribe(event => {
  this.load();
});

ngOnDestroy(): void {
  this.eventsSub?.unsubscribe();
}
```

Unsubscribing on destroy matters: forgetting it means the list
component would receive events forever, even after the user has
navigated away. Eventually you'd accumulate ghost subscribers each
calling `load()` on every event, multiplying network traffic.

## How to test

You'll need two browser windows side by side. Easiest:

1. Open the app in **Window A** at `/employees`.
2. Open the app in an incognito **Window B** at `/employees`.
3. In Window A, edit any employee's email and Save.
4. Within ~1 second, Window B's list refreshes — the new email is
   visible without you doing anything.

Open the browser console in either window. You should see a one-time
log on connect:

```
STOMP connected, subscribed to /topic/employees
```

…and a log per incoming event:

```
Live employee event: { type: "UPDATED", employeeNumber: 1370 }
```

DevTools → Network → `WS` filter shows the persistent WebSocket
connection. Click it to see the individual STOMP frames flowing.

If the live update doesn't fire, check the proxy: the dev server
needs `"ws": true` in `proxy.conf.json` (or the connection won't be
upgraded to WebSocket). Without that, the STOMP client errors with
something like "Cannot upgrade to WebSocket."

## What you just learned

- **WebSocket as a persistent server-to-client connection**, vs HTTP's
  request/response only.
- **STOMP** as the canonical pub-sub protocol over WebSocket, and
  why it's worth using over raw frames.
- **`@EnableWebSocketMessageBroker` + `SimpMessagingTemplate`** as
  Spring's idiomatic way to set up topics and broadcast to them.
- **`@stomp/stompjs` `Client`** as the standard JS-side STOMP
  consumer with auto-reconnect.
- **The Subject-as-event-bus pattern** in Angular for distributing
  point-in-time signals across components.

## Study materials

### WebSocket fundamentals

- [MDN — WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)
- [MDN — Writing WebSocket client applications](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications)
- [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455) — the canonical reference

### STOMP

- [STOMP 1.2 specification](https://stomp.github.io/stomp-specification-1.2.html)
- [Spring docs — STOMP messaging](https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html)
- [Baeldung — WebSockets with Spring](https://www.baeldung.com/websockets-spring) — narrative walk-through

### Spring messaging

- [Spring docs — `@MessageMapping`](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-annotations.html)
- [Spring docs — `SimpMessagingTemplate`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/simp/SimpMessagingTemplate.html)
- [Baeldung — Spring `@SendTo`](https://www.baeldung.com/spring-websockets-sendtosession) — alternative to manually using template

### Angular + STOMP

- [@stomp/stompjs documentation](https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html)
- [@stomp/ng2-stompjs (Angular wrapper, optional)](https://github.com/stomp-js/ng2-stompjs) — adds an injectable RxJS-friendly wrapper if you want to skip writing the service layer yourself

### Production-grade WebSocket auth (out of scope here)

- [Spring docs — STOMP authentication](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)
- [Baeldung — Spring WebSocket Security](https://www.baeldung.com/spring-security-websockets)
