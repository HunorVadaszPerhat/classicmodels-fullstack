import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';

/**
 * Shape of the events the backend pushes on /topic/customers.
 * Mirrors com.hunor.classicmodelsbackend.realtime.CustomerEvent.
 */
export interface CustomerEvent {
  type: 'CREATED' | 'UPDATED' | 'DELETED';
  customerNumber: number;
}

/**
 * Maintains a single WebSocket+STOMP connection to the backend and
 * exposes customer events as an Observable.
 *
 * <h3>Why a separate service from {@link EmployeeEventsService}?</h3>
 *
 * <p>Each entity gets its own {@code /topic/...} destination, and each
 * destination needs its own subscription. Two services means each
 * component subscribes only to the topic it cares about — the customer
 * list doesn't get woken up every time an employee is updated. The
 * STOMP client itself is independent per service, so connections are
 * established lazily as components inject the service.</p>
 *
 * <p>If WebSocket churn ever becomes a concern (multiple services →
 * multiple connections), the right refactor is a single shared
 * {@code StompClientService} that both event services subscribe
 * through. For now (two entity types) the duplication is cheap and
 * obvious.</p>
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
export class CustomerEventsService {

  /** Hot stream of incoming events. Subscribe in components. */
  readonly events$ = new Subject<CustomerEvent>();

  private readonly client: Client;

  constructor() {
    this.client = new Client({
      // Same broker URL pattern as EmployeeEventsService — relative to
      // the page origin so it works in both dev (proxied through
      // localhost:4200) and prod (wherever the frontend is served).
      brokerURL: this.buildBrokerUrl(),

      // Auto-reconnect with exponential backoff handled by the lib.
      // 5 seconds is the default; explicit here for clarity.
      reconnectDelay: 5000,

      onConnect: () => {
        // Subscribe to the customer topic. Every message body comes
        // through as a JSON string we have to parse ourselves.
        this.client.subscribe('/topic/customers', (message: IMessage) => {
          try {
            const event: CustomerEvent = JSON.parse(message.body);
            this.events$.next(event);
          } catch (e) {
            console.error('Bad customer event payload', message.body, e);
          }
        });
        console.log('STOMP connected, subscribed to /topic/customers');
      },
      onStompError: frame => {
        // STOMP-level error (e.g. malformed CONNECT). Distinct from
        // network errors which trigger auto-reconnect.
        console.error('Customer STOMP error:', frame.headers['message'], frame.body);
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
