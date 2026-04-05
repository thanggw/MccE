"use client";

import { Client, type IFrame, type StompSubscription } from "@stomp/stompjs";

type FrameHandler = (frame: {
  command: string;
  headers: Record<string, string>;
  body: string;
}) => void;

type StompClientOptions = {
  url: string;
  onFrame?: FrameHandler;
  onOpen?: (headers: Record<string, string>) => void;
  onClose?: () => void;
  onError?: (message: string) => void;
};

function mapFrame(frame: IFrame) {
  return {
    command: frame.command,
    headers: frame.headers,
    body: frame.body,
  };
}

export class SimpleStompClient {
  private client: Client;
  private subscriptions = new Map<string, StompSubscription>();
  private subscriptionCounter = 0;

  constructor(private readonly options: StompClientOptions) {
    const brokerHost = (() => {
      try {
        return new URL(options.url).host;
      } catch {
        return typeof window === "undefined" ? "localhost" : window.location.host;
      }
    })();

    this.client = new Client({
      brokerURL: options.url,
      connectHeaders: {
        host: brokerHost,
      },
      reconnectDelay: 0,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      onConnect: (frame) => {
        options.onOpen?.(frame.headers);
      },
      onStompError: (frame) => {
        options.onError?.(frame.headers.message || frame.body || "STOMP error");
      },
      onWebSocketClose: () => {
        options.onClose?.();
      },
      onWebSocketError: () => {
        options.onError?.("Cannot connect to collaboration server");
      },
    });
  }

  connect() {
    this.client.activate();
  }

  isOpen() {
    return this.client.connected;
  }

  subscribe(destination: string) {
    const id = `sub-${this.subscriptionCounter++}`;
    const subscription = this.client.subscribe(destination, (message) => {
      this.options.onFrame?.(mapFrame(message));
    });
    this.subscriptions.set(id, subscription);
    return id;
  }

  send(destination: string, body: unknown) {
    this.client.publish({
      destination,
      body: JSON.stringify(body),
      headers: {
        "content-type": "application/json",
      },
    });
  }

  disconnect() {
    for (const subscription of this.subscriptions.values()) {
      subscription.unsubscribe();
    }
    this.subscriptions.clear();
    void this.client.deactivate();
  }
}
