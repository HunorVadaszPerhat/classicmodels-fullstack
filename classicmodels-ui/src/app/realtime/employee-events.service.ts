import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';

/**
 * Shape of the events the backend pushes on /topic/employees.
 * Mirrors com.hunor.classicmodelsbackend.realtime.EmployeeEvent.
 */
export interface EmployeeEvent {
  type: 'CREATED' | 'UPDATED' | 'DELETED';
  employeeNumber: number;
}

/**
 * Maintains a single WebSocket+STOMP connection to the backend and
 * exposes employee events as an Observable.
 *
 * <h3>Lifecycle</h3>
 *
 * <p>The service is {@code providedIn: 'root'} so it lives for the
 * entire app lifetime. We connect once on construction and let the
 * STOMP client auto-reconnect (configured below) on transient
 * network failures. Components subscribe to {@link events$} to
 * receive notifications.</p>
 *
 * <h3>Why Subject and not BehaviorSubject?</h3>
 *
 * <p>Events are point-in-time signals — "something changed *now*."
 * A late subscriber shouldn't replay a stale event from earlier in
 * the session; that would trigger a bogus reload. Subject (without
 * "Behavior" or "Replay" prefix) emits only to current subscribers
 * and forgets the value immediately.</p>
 */
@Injectable({ providedIn: 'root' })
export class EmployeeEventsService {

  /** Hot stream of incoming events. Subscribe in components. */
  readonly events$ = new Subject<EmployeeEvent>();

  private readonly client: Client;

  constructor() {
    this.client = new Client({
      // The Angular dev server proxies /api → backend; with `ws: true`
      // it also proxies WebSocket upgrades. window.location.host is
      // whatever the page was served from (localhost:4200 in dev,
      // wherever the frontend lives in prod) — using it makes this
      // work in both environments without per-env config.
      brokerURL: this.buildBrokerUrl(),

      // Auto-reconnect with exponential backoff handled by the lib.
      // 5 seconds is the default; explicit here for clarity.
      reconnectDelay: 5000,

      // Connection lifecycle hooks. Logged so you can see in the
      // browser console that the connection works.
      onConnect: () => {
        // Subscribe to the topic. Every message body comes through as
        // a JSON string we have to parse ourselves.
        this.client.subscribe('/topic/employees', (message: IMessage) => {
          try {
            const event: EmployeeEvent = JSON.parse(message.body);
            this.events$.next(event);
          } catch (e) {
            console.error('Bad event payload', message.body, e);
          }
        });
        console.log('STOMP connected, subscribed to /topic/employees');
      },
      onStompError: frame => {
        // STOMP-level error (e.g. malformed CONNECT). Distinct from
        // network errors which trigger auto-reconnect.
        console.error('STOMP error:', frame.headers['message'], frame.body);
      },
    });

    this.client.activate();
  }

  /**
   * Build the WebSocket URL relative to the page's origin. For the
   * Angular dev server, that's ws://localhost:4200/api/v1/ws which
   * the proxy forwards to ws://localhost:9090/api/v1/ws.
   */
  private buildBrokerUrl(): string {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${location.host}/api/v1/ws`;
  }
}
