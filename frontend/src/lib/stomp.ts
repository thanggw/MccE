"use client";

type FrameHandler = (frame: {
  command: string;
  headers: Record<string, string>;
  body: string;
}) => void;

type StompClientOptions = {
  url: string;
  onFrame?: FrameHandler;
  onOpen?: () => void;
  onClose?: () => void;
  onError?: (message: string) => void;
};

function encodeFrame(
  command: string,
  headers: Record<string, string>,
  body = "",
) {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return `${command}\n${headerLines.join("\n")}\n\n${body}\0`;
}

function parseFrames(chunk: string) {
  const frames = chunk.split("\0").filter((part) => part.trim() !== "");
  return frames
    .map((frame) => {
      const normalized = frame.replace(/\r/g, "");
      if (normalized === "\n") {
        return null;
      }

      const separatorIndex = normalized.indexOf("\n\n");
      const head = separatorIndex >= 0 ? normalized.slice(0, separatorIndex) : normalized;
      const body = separatorIndex >= 0 ? normalized.slice(separatorIndex + 2) : "";
      const [command, ...headerLines] = head.split("\n").filter(Boolean);
      if (!command) {
        return null;
      }

      const headers: Record<string, string> = {};
      for (const line of headerLines) {
        const colonIndex = line.indexOf(":");
        if (colonIndex <= 0) {
          continue;
        }
        headers[line.slice(0, colonIndex)] = line.slice(colonIndex + 1);
      }

      return { command, headers, body };
    })
    .filter((value): value is { command: string; headers: Record<string, string>; body: string } => value !== null);
}

export class SimpleStompClient {
  private socket: WebSocket | null = null;
  private subscriptionCounter = 0;

  constructor(private readonly options: StompClientOptions) {}

  connect() {
    this.socket = new WebSocket(this.options.url);

    this.socket.onopen = () => {
      this.socket?.send(
        encodeFrame("CONNECT", {
          "accept-version": "1.2",
          "heart-beat": "0,0",
        }),
      );
    };

    this.socket.onmessage = (event) => {
      const payload = typeof event.data === "string" ? event.data : "";
      for (const frame of parseFrames(payload)) {
        if (frame.command === "CONNECTED") {
          this.options.onOpen?.();
          continue;
        }

        if (frame.command === "ERROR") {
          this.options.onError?.(frame.body || frame.headers.message || "STOMP error");
          continue;
        }

        this.options.onFrame?.(frame);
      }
    };

    this.socket.onclose = () => {
      this.options.onClose?.();
    };

    this.socket.onerror = () => {
      this.options.onError?.("Cannot connect to collaboration server");
    };
  }

  subscribe(destination: string) {
    const id = `sub-${this.subscriptionCounter++}`;
    this.socket?.send(
      encodeFrame("SUBSCRIBE", {
        id,
        destination,
      }),
    );
    return id;
  }

  send(destination: string, body: unknown) {
    const payload = JSON.stringify(body);
    this.socket?.send(
      encodeFrame(
        "SEND",
        {
          destination,
          "content-type": "application/json",
          "content-length": String(new TextEncoder().encode(payload).length),
        },
        payload,
      ),
    );
  }

  disconnect() {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(encodeFrame("DISCONNECT", {}));
    }
    this.socket?.close();
    this.socket = null;
  }
}
