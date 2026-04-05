"use client";

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import Editor from "@monaco-editor/react";
import {
  Activity,
  Copy,
  FilePlus2,
  Languages,
  PencilLine,
  Play,
  Plus,
  SquareTerminal,
  Trash2,
  Users,
  X,
} from "lucide-react";
import { SimpleStompClient } from "@/components/dashboard/stompClient";

type FileItem = {
  id: string;
  name: string;
  content: string;
  language: string;
  version: number;
};

type Participant = {
  sessionId: string;
  nickname: string;
};

type RoomState = {
  roomId: string;
  activeFileId: string | null;
  files: FileItem[];
  participants: Participant[];
};

type PresenceMessage = {
  roomId: string;
  participantCount: number;
  participants: Participant[];
};

type EditorDelta = {
  rangeOffset: number;
  rangeLength: number;
  text: string;
};

type DeltaMessage = {
  roomId: string;
  fileId: string;
  sourceSessionId?: string;
  clientInstanceId?: string;
  baseVersion?: number;
  serverVersion?: number;
  accepted?: boolean;
  reason?: string;
  changes: EditorDelta[];
};

type CursorMessage = {
  roomId: string;
  fileId?: string;
  sourceSessionId?: string;
  clientInstanceId?: string;
  nickname?: string;
  lineNumber?: number;
  column?: number;
  visible?: boolean;
};

type RemoteCursor = {
  sessionId: string;
  nickname: string;
  fileId: string;
  lineNumber: number;
  column: number;
};

type PendingDelta = {
  roomId: string;
  fileId: string;
  baseVersion: number;
  baseContent: string;
  nextContent: string;
};

type WorkspaceEventMessage = {
  roomId: string;
  type:
    | "CREATE_FILE"
    | "DELETE_FILE"
    | "RENAME_FILE"
    | "UPDATE_LANGUAGE"
    | "SET_ACTIVE_FILE";
  sourceSessionId?: string;
  clientInstanceId?: string;
  file?: FileItem;
  fileId?: string;
  name?: string;
  language?: string;
};

type MonacoPosition = {
  lineNumber: number;
  column: number;
};

type MonacoModel = {
  getValue(): string;
  getPositionAt(offset: number): MonacoPosition;
  setValue(value: string): void;
};

type MonacoChangeEvent = {
  changes: Array<{ rangeOffset: number; rangeLength: number; text: string }>;
};

type MonacoCursorEvent = {
  position: MonacoPosition;
};

type MonacoDisposable = {
  dispose(): void;
};

type MonacoDecoration = {
  range: {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
  };
  options: {
    beforeContentClassName?: string;
    afterContentClassName?: string;
    hoverMessage?: { value: string };
    stickiness?: number;
  };
};

type MonacoLike = {
  editor: {
    TrackedRangeStickiness: {
      NeverGrowsWhenTypingAtEdges: number;
    };
  };
};

type MonacoEditorLike = {
  getModel(): MonacoModel | null;
  getPosition(): MonacoPosition | null;
  executeEdits(
    source: string,
    edits: Array<{
      range: {
        startLineNumber: number;
        startColumn: number;
        endLineNumber: number;
        endColumn: number;
      };
      text: string;
      forceMoveMarkers: boolean;
    }>,
  ): void;
  deltaDecorations(
    oldDecorations: string[],
    newDecorations: MonacoDecoration[],
  ): string[];
  onDidChangeModelContent(listener: (event: MonacoChangeEvent) => void): void;
  onDidChangeCursorPosition(
    listener: (event: MonacoCursorEvent) => void,
  ): MonacoDisposable;
  onDidFocusEditorText(listener: () => void): MonacoDisposable;
};

type RunState = "idle" | "running" | "success" | "error";
type CollaborationState = "booting" | "connecting" | "live" | "offline";

type InteractiveSessionResponse = {
  sessionId: string;
};

type ExecutionResult = {
  language: string;
  output: string;
  error: string;
  exitCode: number | null;
  success: boolean;
};

type TerminalMessage = {
  type: "output" | "error" | "exit";
  data: string;
  exitCode: number | null;
};

const NICKNAME_STORAGE_KEY = "mcce:nickname";
const REMOTE_CURSOR_STYLE_ID = "mcce-remote-cursor-styles";
const REMOTE_CURSOR_COLORS = [
  "#67e8f9",
  "#34d399",
  "#fbbf24",
  "#fb7185",
  "#60a5fa",
];
const EXECUTION_STRATEGY =
  process.env.NEXT_PUBLIC_EXECUTION_STRATEGY?.toUpperCase() || "DOCKER";

const getLanguageFromName = (name: string) => {
  const lower = name.toLowerCase();
  if (lower.endsWith(".java")) return "java";
  if (lower.endsWith(".py")) return "python";
  if (
    lower.endsWith(".cpp") ||
    lower.endsWith(".cc") ||
    lower.endsWith(".cxx")
  ) {
    return "cpp";
  }
  if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "javascript";
  return "plaintext";
};

const languageOptions = [
  {
    id: "java",
    label: "Java",
    starter:
      'public class Main {\n  public static void main(String[] args) {\n    System.out.println("Hello McCE!");\n  }\n}\n',
  },
  {
    id: "python",
    label: "Python",
    starter:
      'def main():\n    print("Hello McCE!")\n\n\nif __name__ == "__main__":\n    main()\n',
  },
  {
    id: "cpp",
    label: "C++",
    starter:
      '#include <iostream>\n\nint main() {\n  std::cout << "Hello McCE!" << std::endl;\n  return 0;\n}\n',
  },
  {
    id: "javascript",
    label: "Node.js",
    starter: 'function main() {\n  console.log("Hello McCE!");\n}\n\nmain();\n',
  },
];

const createStarterFromLanguage = (language: string) =>
  languageOptions.find((option) => option.id === language)?.starter ?? "";

const createInitialFiles = (): FileItem[] => [
  {
    id: crypto.randomUUID(),
    name: "Utils.java",
    language: "java",
    content:
      "public class Utils {\n" +
      "  public static void main(String args[]) {\n" +
      '   System.out.println("Hello friends!");\n' +
      "  }\n" +
      "}\n",
    version: 0,
  },
];

function mapRoomFiles(files: FileItem[] | undefined) {
  if (!files || files.length === 0) {
    return [];
  }
  return files.map((file) => ({
    id: file.id || crypto.randomUUID(),
    name: file.name,
    content: file.content ?? "",
    language: file.language || getLanguageFromName(file.name),
    version: typeof file.version === "number" ? file.version : 0,
  }));
}

function getAvatarTone(name: string) {
  const tones = [
    "bg-cyan-400/20 text-cyan-100 border-cyan-300/40",
    "bg-emerald-400/20 text-emerald-100 border-emerald-300/40",
    "bg-amber-400/20 text-amber-100 border-amber-300/40",
    "bg-rose-400/20 text-rose-100 border-rose-300/40",
    "bg-sky-400/20 text-sky-100 border-sky-300/40",
  ];
  const hash = Array.from(name).reduce(
    (acc, char) => acc + char.charCodeAt(0),
    0,
  );
  return tones[hash % tones.length];
}

function getCursorColorIndex(value: string) {
  const hash = Array.from(value).reduce(
    (acc, char) => acc + char.charCodeAt(0),
    0,
  );
  return hash % REMOTE_CURSOR_COLORS.length;
}

function createRemoteCursorStyles() {
  return REMOTE_CURSOR_COLORS.map(
    (color, index) => `
      .monaco-editor .remote-cursor-${index} {
        position: relative;
        display: inline-block;
        width: 0 !important;
        height: 1.45em;
        border-left: 2px solid ${color};
        margin-left: -1px;
        vertical-align: text-top;
      }

      .monaco-editor .remote-cursor-${index}::before {
        content: "";
        position: absolute;
        top: -3px;
        left: -5px;
        width: 10px;
        height: 10px;
        border-radius: 9999px;
        background: ${color};
        box-shadow: 0 0 0 2px rgba(2, 6, 23, 0.92);
      }

    `,
  ).join("\n");
}

function applyEditorChanges(content: string, changes: EditorDelta[]) {
  return [...changes]
    .sort((left, right) => right.rangeOffset - left.rangeOffset)
    .reduce((nextContent, change) => {
      const start = Math.max(
        0,
        Math.min(change.rangeOffset, nextContent.length),
      );
      const end = Math.max(
        start,
        Math.min(start + change.rangeLength, nextContent.length),
      );
      return nextContent.slice(0, start) + change.text + nextContent.slice(end);
    }, content);
}

function getNextActiveFileId(
  snapshot: RoomState,
  snapshotFiles: FileItem[],
  fallbackFiles: FileItem[],
) {
  return (
    snapshot.activeFileId || snapshotFiles[0]?.id || fallbackFiles[0]?.id || ""
  );
}

export default function Dashboard({ roomId }: { roomId: string }) {
  const [files, setFiles] = useState<FileItem[]>(() => createInitialFiles());
  const [activeFileId, setActiveFileId] = useState<string>("");
  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createLanguage, setCreateLanguage] = useState("java");
  const [terminalInput, setTerminalInput] = useState("");
  const [runState, setRunState] = useState<RunState>("idle");
  const [executionLog, setExecutionLog] = useState("");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [isTerminalOpen, setIsTerminalOpen] = useState(false);
  const [terminalHeight, setTerminalHeight] = useState(220);
  const [copiedRoomLink, setCopiedRoomLink] = useState(false);
  const [remoteCursors, setRemoteCursors] = useState<
    Record<string, RemoteCursor>
  >({});
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [nickname, setNickname] = useState("");
  const [nicknameDraft, setNicknameDraft] = useState("");
  const [showNicknameGate, setShowNicknameGate] = useState(false);
  const [collaborationState, setCollaborationState] =
    useState<CollaborationState>("booting");
  const wsRef = useRef<WebSocket | null>(null);
  const clientInstanceIdRef = useRef(crypto.randomUUID());
  const collabClientRef = useRef<SimpleStompClient | null>(null);
  const terminalViewportRef = useRef<HTMLPreElement | null>(null);
  const terminalInputRef = useRef<HTMLInputElement | null>(null);
  const createPanelRef = useRef<HTMLDivElement | null>(null);
  const addFileButtonRef = useRef<HTMLButtonElement | null>(null);
  const editorRef = useRef<MonacoEditorLike | null>(null);
  const monacoRef = useRef<MonacoLike | null>(null);
  const mainPanelRef = useRef<HTMLElement | null>(null);
  const remoteCursorDecorationIdsRef = useRef<string[]>([]);
  const deltaTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cursorTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingCursorRef = useRef<CursorMessage | null>(null);
  const pendingDeltaRef = useRef<PendingDelta | null>(null);
  const collabSessionIdRef = useRef<string | null>(null);
  const isApplyingRemoteDeltaRef = useRef(false);
  const hasJoinedRoomRef = useRef(false);
  const [collabSocketEpoch, setCollabSocketEpoch] = useState(0);
  const collabReconnectTimeoutRef = useRef<ReturnType<
    typeof setTimeout
  > | null>(null);
  const collaborationStateRef = useRef<CollaborationState>("booting");
  const roomIdRef = useRef(roomId);
  roomIdRef.current = roomId;
  const roomSeededRef = useRef<string | null>(null);
  const currentFilesRef = useRef<FileItem[]>(files);
  const currentActiveFileIdRef = useRef<string | null>(activeFileId);

  const activeFile = useMemo(
    () => files.find((file) => file.id === activeFileId) ?? files[0],
    [files, activeFileId],
  );

  const applyRoomSnapshot = useCallback((snapshot: RoomState) => {
    const snapshotFiles = mapRoomFiles(snapshot.files);
    const nextActiveFileId = getNextActiveFileId(
      snapshot,
      snapshotFiles,
      currentFilesRef.current,
    );

    if (snapshotFiles.length > 0) {
      setFiles(snapshotFiles);
      setActiveFileId(nextActiveFileId);
    }

    setParticipants(snapshot.participants ?? []);
  }, []);

  const reloadRoomSnapshot = useCallback(
    async (signal?: AbortSignal) => {
      const response = await fetch(getBackendApiUrl(`/api/rooms/${roomId}`), {
        signal,
      });
      const snapshot = (await response.json()) as RoomState;
      applyRoomSnapshot(snapshot);
      return snapshot;
    },
    [applyRoomSnapshot, roomId],
  );

  useEffect(() => {
    collaborationStateRef.current = collaborationState;
  }, [collaborationState]);

  useEffect(() => {
    currentFilesRef.current = files;
    currentActiveFileIdRef.current = activeFileId;
  }, [files, activeFileId]);

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }

    if (document.getElementById(REMOTE_CURSOR_STYLE_ID)) {
      return;
    }

    const style = document.createElement("style");
    style.id = REMOTE_CURSOR_STYLE_ID;
    style.textContent = createRemoteCursorStyles();
    document.head.appendChild(style);

    return () => {
      style.remove();
    };
  }, []);

  useEffect(() => {
    if (!activeFileId && files[0]) {
      setActiveFileId(files[0].id);
    }
  }, [activeFileId, files]);

  useEffect(() => {
    if (!editorRef.current || !activeFile) {
      return;
    }

    const model = editorRef.current.getModel?.();
    if (!model) {
      return;
    }

    if (model.getValue() === activeFile.content) {
      return;
    }

    isApplyingRemoteDeltaRef.current = true;
    model.setValue(activeFile.content);
    isApplyingRemoteDeltaRef.current = false;
  }, [activeFile, activeFileId]);

  useEffect(() => {
    const storedNickname = window.sessionStorage.getItem(NICKNAME_STORAGE_KEY);
    if (storedNickname && storedNickname.trim()) {
      setNickname(storedNickname.trim());
      setNicknameDraft(storedNickname.trim());
      setShowNicknameGate(false);
      return;
    }
    setShowNicknameGate(true);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    async function loadRoomSnapshot() {
      try {
        await reloadRoomSnapshot(controller.signal);
        setCollaborationState((prev) =>
          prev === "booting" ? "offline" : prev,
        );
      } catch {
        setCollaborationState("offline");
      }
    }

    loadRoomSnapshot();
    return () => {
      controller.abort();
    };
  }, [reloadRoomSnapshot]);

  useEffect(() => {
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      collabClientRef.current?.disconnect();
      collabClientRef.current = null;
      if (deltaTimeoutRef.current) {
        clearTimeout(deltaTimeoutRef.current);
      }
      if (cursorTimeoutRef.current) {
        clearTimeout(cursorTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (terminalViewportRef.current) {
      terminalViewportRef.current.scrollTop =
        terminalViewportRef.current.scrollHeight;
    }
  }, [executionLog]);

  useEffect(() => {
    if (runState === "running" && sessionId) {
      terminalInputRef.current?.focus();
    }
  }, [runState, sessionId, executionLog]);

  useEffect(() => {
    if (!copiedRoomLink) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setCopiedRoomLink(false);
    }, 1800);

    return () => window.clearTimeout(timeout);
  }, [copiedRoomLink]);

  useEffect(() => {
    const participantSessionIds = new Set(
      participants.map((participant) => participant.sessionId),
    );
    setRemoteCursors((prev) => {
      const nextEntries = Object.entries(prev).filter(([sessionId]) =>
        participantSessionIds.has(sessionId),
      );
      if (nextEntries.length === Object.keys(prev).length) {
        return prev;
      }
      return Object.fromEntries(nextEntries);
    });
  }, [participants]);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) {
      return;
    }

    const visibleRemoteCursors = Object.values(remoteCursors).filter(
      (cursor) => cursor.fileId === activeFileId,
    );

    remoteCursorDecorationIdsRef.current = editor.deltaDecorations(
      remoteCursorDecorationIdsRef.current,
      visibleRemoteCursors.map((cursor) => ({
        range: {
          startLineNumber: cursor.lineNumber,
          startColumn: cursor.column,
          endLineNumber: cursor.lineNumber,
          endColumn: cursor.column,
        },
        options: {
          className: `remote-cursor-${getCursorColorIndex(cursor.sessionId)}`,
          hoverMessage: {
            value: `${cursor.nickname} is at line ${cursor.lineNumber}`,
          },
          stickiness:
            monacoRef.current?.editor.TrackedRangeStickiness
              .NeverGrowsWhenTypingAtEdges,
        },
      })),
    );
  }, [activeFileId, remoteCursors]);

  useEffect(() => {
    if (!showCreate) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node | null;
      if (!target) {
        return;
      }

      const clickedCreatePanel = createPanelRef.current?.contains(target);
      const clickedAddButton = addFileButtonRef.current?.contains(target);

      if (!clickedCreatePanel && !clickedAddButton) {
        setShowCreate(false);
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setShowCreate(false);
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [showCreate]);

  useEffect(() => {
    if (!nickname) {
      return;
    }

    let connectionActive = true;
    let joinSnapshotRetryTimeout: ReturnType<typeof setTimeout> | null = null;

    const scheduleReconnect = () => {
      if (!connectionActive) {
        return;
      }
      if (collabReconnectTimeoutRef.current) {
        return;
      }
      collabReconnectTimeoutRef.current = setTimeout(() => {
        collabReconnectTimeoutRef.current = null;
        if (!connectionActive) {
          return;
        }
        setCollabSocketEpoch((value) => value + 1);
      }, 1500);
    };

    const handleCollabDisconnect = () => {
      if (!connectionActive) {
        return;
      }
      setCollaborationState("offline");
      hasJoinedRoomRef.current = false;
      collabSessionIdRef.current = null;
      setRemoteCursors({});
      scheduleReconnect();
    };

    const syncRoomSnapshotAfterJoin = async (attempt = 0) => {
      if (!connectionActive) {
        return;
      }

      try {
        const snapshot = await reloadRoomSnapshot();
        if ((snapshot.participants?.length ?? 0) > 0 || attempt >= 2) {
          return;
        }
      } catch {
        if (attempt >= 2) {
          return;
        }
      }

      joinSnapshotRetryTimeout = setTimeout(() => {
        joinSnapshotRetryTimeout = null;
        void syncRoomSnapshotAfterJoin(attempt + 1);
      }, 250);
    };

    setCollaborationState("connecting");
    hasJoinedRoomRef.current = false;

    const client = new SimpleStompClient({
      url: getCollaborationWebSocketUrl(),
      onOpen: (headers) => {
        collabSessionIdRef.current = headers.session ?? null;
        setCollaborationState("live");
        client.subscribe(`/topic/rooms/${roomId}/presence`);
        client.subscribe(`/topic/rooms/${roomId}/workspace`);
        client.subscribe(`/topic/rooms/${roomId}/delta`);
        client.subscribe(`/topic/rooms/${roomId}/cursor`);
        if (headers.session) {
          setParticipants((prev) => {
            if (
              prev.some((participant) => participant.sessionId === headers.session)
            ) {
              return prev;
            }
            return [
              ...prev,
              {
                sessionId: headers.session,
                nickname,
              },
            ];
          });
        }
        client.send("/app/collab.join", {
          roomId,
          nickname,
        });
        hasJoinedRoomRef.current = true;
        void syncRoomSnapshotAfterJoin();
      },
      onFrame: (frame) => {
        if (frame.command !== "MESSAGE") {
          return;
        }

        const destination = frame.headers.destination ?? "";
        if (destination.endsWith("/presence")) {
          const presence = JSON.parse(frame.body) as PresenceMessage;
          setParticipants(presence.participants ?? []);
          return;
        }

        if (destination.endsWith("/workspace")) {
          const event = JSON.parse(frame.body) as WorkspaceEventMessage;
          if (
            event.clientInstanceId === clientInstanceIdRef.current ||
            (event.sourceSessionId &&
              event.sourceSessionId === collabSessionIdRef.current)
          ) {
            return;
          }

          setFiles((prev) => {
            switch (event.type) {
              case "CREATE_FILE":
                return event.file ? [...prev, event.file] : prev;
              case "DELETE_FILE":
                return prev.filter((file) => file.id !== event.fileId);
              case "RENAME_FILE":
                return prev.map((file) =>
                  file.id === event.fileId
                    ? { ...file, name: event.name ?? file.name }
                    : file,
                );
              case "UPDATE_LANGUAGE":
                return prev.map((file) =>
                  file.id === event.fileId
                    ? { ...file, language: event.language ?? file.language }
                    : file,
                );
              case "SET_ACTIVE_FILE":
              default:
                return prev;
            }
          });

          if (
            event.type === "DELETE_FILE" &&
            event.fileId === currentActiveFileIdRef.current
          ) {
            const nextFiles = currentFilesRef.current.filter(
              (file) => file.id !== event.fileId,
            );
            if (nextFiles[0]) {
              setActiveFileId(nextFiles[0].id);
            }
          }
          if (event.type === "SET_ACTIVE_FILE" && event.fileId) {
            setActiveFileId(event.fileId);
          }
          return;
        }

        if (!destination.endsWith("/delta")) {
          if (!destination.endsWith("/cursor")) {
            return;
          }

          const message = JSON.parse(frame.body) as CursorMessage;
          const isSelfMessage =
            message.clientInstanceId === clientInstanceIdRef.current ||
            (message.sourceSessionId &&
              message.sourceSessionId === collabSessionIdRef.current);

          if (isSelfMessage || !message.sourceSessionId) {
            return;
          }

          setRemoteCursors((prev) => {
            if (
              !message.visible ||
              !message.fileId ||
              !message.lineNumber ||
              !message.column
            ) {
              if (!prev[message.sourceSessionId!]) {
                return prev;
              }
              const next = { ...prev };
              delete next[message.sourceSessionId!];
              return next;
            }

            return {
              ...prev,
              [message.sourceSessionId!]: {
                sessionId: message.sourceSessionId!,
                nickname: message.nickname?.trim() || "Collaborator",
                fileId: message.fileId,
                lineNumber: message.lineNumber,
                column: message.column,
              },
            };
          });
          return;
        }

        const message = JSON.parse(frame.body) as DeltaMessage;
        const isSelfMessage =
          message.clientInstanceId === clientInstanceIdRef.current ||
          (message.sourceSessionId &&
            message.sourceSessionId === collabSessionIdRef.current);

        if (message.accepted === false) {
          if (isSelfMessage) {
            void reloadRoomSnapshot();
          }
          return;
        }

        if (isSelfMessage) {
          if (typeof message.serverVersion === "number") {
            setFiles((prev) =>
              prev.map((file) =>
                file.id === message.fileId
                  ? { ...file, version: message.serverVersion ?? file.version }
                  : file,
              ),
            );
          }
          return;
        }

        setFiles((prev) =>
          prev.map((file) => {
            if (file.id !== message.fileId) {
              return file;
            }

            const nextContent = applyEditorChanges(
              file.content ?? "",
              message.changes ?? [],
            );
            return {
              ...file,
              content: nextContent,
              version:
                typeof message.serverVersion === "number"
                  ? message.serverVersion
                  : file.version,
            };
          }),
        );

        if (
          editorRef.current &&
          message.fileId === currentActiveFileIdRef.current &&
          Array.isArray(message.changes)
        ) {
          const model = editorRef.current.getModel?.();
          if (model) {
            isApplyingRemoteDeltaRef.current = true;
            editorRef.current.executeEdits(
              "remote-collab",
              message.changes.map((change) => {
                const start = model.getPositionAt(change.rangeOffset);
                const end = model.getPositionAt(
                  change.rangeOffset + change.rangeLength,
                );
                return {
                  range: {
                    startLineNumber: start.lineNumber,
                    startColumn: start.column,
                    endLineNumber: end.lineNumber,
                    endColumn: end.column,
                  },
                  text: change.text,
                  forceMoveMarkers: true,
                };
              }),
            );
            isApplyingRemoteDeltaRef.current = false;
          }
        }
      },
      onClose: handleCollabDisconnect,
      onError: handleCollabDisconnect,
    });

    collabClientRef.current = client;
    client.connect();

    return () => {
      connectionActive = false;
      if (joinSnapshotRetryTimeout) {
        clearTimeout(joinSnapshotRetryTimeout);
      }
      if (collabReconnectTimeoutRef.current) {
        clearTimeout(collabReconnectTimeoutRef.current);
        collabReconnectTimeoutRef.current = null;
      }
      hasJoinedRoomRef.current = false;
      setRemoteCursors({});
      client.disconnect();
      if (collabClientRef.current === client) {
        collabClientRef.current = null;
      }
    };
  }, [nickname, reloadRoomSnapshot, roomId, collabSocketEpoch]);

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }

    const onVisibilityChange = () => {
      if (document.visibilityState !== "visible" || !nickname?.trim()) {
        return;
      }

      void reloadRoomSnapshot();

      const client = collabClientRef.current;
      const socketDead = !client || !client.isOpen();
      const state = collaborationStateRef.current;
      const shouldReconnect =
        state === "offline" || (state === "live" && socketDead);
      if (shouldReconnect) {
        setCollabSocketEpoch((value) => value + 1);
      }
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [nickname, reloadRoomSnapshot]);

  const flushPendingDeltaRef = useRef<() => void>(() => {});

  const seedRoomIfNeeded = useCallback(async () => {
    if (
      !collabClientRef.current ||
      roomSeededRef.current === roomIdRef.current
    ) {
      return;
    }

    const snapshot = await reloadRoomSnapshot();
    if (snapshot.files.length > 0) {
      roomSeededRef.current = roomIdRef.current;
      return;
    }

    const localFiles = currentFilesRef.current;
    if (localFiles.length === 0) {
      return;
    }

    for (const file of localFiles) {
      collabClientRef.current.send("/app/collab.workspace", {
        roomId: roomIdRef.current,
        type: "CREATE_FILE",
        clientInstanceId: clientInstanceIdRef.current,
        file: {
          id: file.id,
          name: file.name,
          language: file.language,
          content: file.content,
          version: 0,
        },
      });
    }

    roomSeededRef.current = roomIdRef.current;
    setTimeout(() => {
      void reloadRoomSnapshot();
    }, 150);
  }, [reloadRoomSnapshot]);

  const flushPendingDelta = useCallback(() => {
    if (
      !pendingDeltaRef.current ||
      !collabClientRef.current ||
      collaborationStateRef.current !== "live" ||
      !hasJoinedRoomRef.current
    ) {
      return;
    }

    const pendingDelta = pendingDeltaRef.current;
    const currentFile = currentFilesRef.current.find(
      (file) => file.id === pendingDelta.fileId,
    );
    if (!currentFile) {
      pendingDeltaRef.current = null;
      return;
    }

    const deltaMessage: DeltaMessage = {
      roomId: pendingDelta.roomId,
      fileId: pendingDelta.fileId,
      clientInstanceId: clientInstanceIdRef.current,
      baseVersion: pendingDelta.baseVersion,
      changes: [
        {
          rangeOffset: 0,
          rangeLength: pendingDelta.baseContent.length,
          text: pendingDelta.nextContent,
        },
      ],
    };

    setFiles((prev) =>
      prev.map((file) =>
        file.id === pendingDelta.fileId
          ? { ...file, version: file.version + 1 }
          : file,
      ),
    );
    collabClientRef.current.send("/app/collab.delta", deltaMessage);
    pendingDeltaRef.current = null;
  }, []);

  flushPendingDeltaRef.current = flushPendingDelta;

  useEffect(() => {
    if (!nickname || collaborationState !== "live") {
      return;
    }

    void seedRoomIfNeeded();
  }, [collaborationState, nickname, seedRoomIfNeeded]);

  const sendWorkspaceEvent = useCallback((event: WorkspaceEventMessage) => {
    if (
      !collabClientRef.current ||
      collaborationStateRef.current !== "live" ||
      !hasJoinedRoomRef.current
    ) {
      return;
    }

    collabClientRef.current.send("/app/collab.workspace", {
      ...event,
      clientInstanceId: clientInstanceIdRef.current,
    });
  }, []);

  const handleSelectActiveFile = useCallback(
    (fileId: string) => {
      setActiveFileId(fileId);
      sendWorkspaceEvent({
        roomId: roomIdRef.current,
        type: "SET_ACTIVE_FILE",
        fileId,
      });
    },
    [sendWorkspaceEvent],
  );

  const flushPendingCursor = useCallback(() => {
    if (
      !pendingCursorRef.current ||
      !collabClientRef.current ||
      collaborationStateRef.current !== "live" ||
      !hasJoinedRoomRef.current
    ) {
      return;
    }

    collabClientRef.current.send(
      "/app/collab.cursor",
      pendingCursorRef.current,
    );
    pendingCursorRef.current = null;
  }, []);

  const queueCursorBroadcast = useCallback(
    (message: CursorMessage) => {
      pendingCursorRef.current = {
        ...message,
        roomId: roomIdRef.current,
        clientInstanceId: clientInstanceIdRef.current,
      };

      if (cursorTimeoutRef.current) {
        return;
      }

      cursorTimeoutRef.current = setTimeout(() => {
        cursorTimeoutRef.current = null;
        flushPendingCursor();
      }, 60);
    },
    [flushPendingCursor],
  );

  useEffect(() => {
    const editor = editorRef.current;
    const position = editor?.getPosition();
    if (!position || !activeFileId) {
      return;
    }

    queueCursorBroadcast({
      roomId: roomIdRef.current,
      fileId: activeFileId,
      lineNumber: position.lineNumber,
      column: position.column,
      visible: true,
    });
  }, [activeFileId, queueCursorBroadcast]);

  const handleEditorDidMount = (
    editor: MonacoEditorLike,
    monaco: MonacoLike,
  ) => {
    editorRef.current = editor;
    monacoRef.current = monaco;
    remoteCursorDecorationIdsRef.current = [];

    editor.onDidChangeModelContent((event: MonacoChangeEvent) => {
      if (isApplyingRemoteDeltaRef.current) {
        return;
      }

      const model = editor.getModel?.();
      const nextValue =
        typeof model?.getValue === "function"
          ? model.getValue()
          : (activeFile?.content ?? "");

      const fileId = currentActiveFileIdRef.current;
      if (!fileId) {
        return;
      }

      setFiles((prev) =>
        prev.map((file) =>
          file.id === fileId ? { ...file, content: nextValue } : file,
        ),
      );

      if ((event.changes ?? []).length === 0) {
        return;
      }

      if (
        pendingDeltaRef.current &&
        pendingDeltaRef.current.fileId !== fileId
      ) {
        flushPendingDeltaRef.current();
      }

      pendingDeltaRef.current = {
        roomId: roomIdRef.current,
        fileId,
        baseVersion:
          pendingDeltaRef.current?.fileId === fileId
            ? pendingDeltaRef.current.baseVersion
            : (currentFilesRef.current.find((file) => file.id === fileId)
                ?.version ?? 0),
        baseContent:
          pendingDeltaRef.current?.fileId === fileId
            ? pendingDeltaRef.current.baseContent
            : (currentFilesRef.current.find((file) => file.id === fileId)
                ?.content ?? ""),
        nextContent: nextValue,
      };

      if (deltaTimeoutRef.current) {
        clearTimeout(deltaTimeoutRef.current);
      }
      deltaTimeoutRef.current = setTimeout(() => {
        flushPendingDeltaRef.current();
      }, 300);
    });

    editor.onDidChangeCursorPosition((event: MonacoCursorEvent) => {
      const fileId = currentActiveFileIdRef.current;
      if (!fileId) {
        return;
      }

      queueCursorBroadcast({
        roomId: roomIdRef.current,
        fileId,
        lineNumber: event.position.lineNumber,
        column: event.position.column,
        visible: true,
      });
    });

    editor.onDidFocusEditorText(() => {
      const fileId = currentActiveFileIdRef.current;
      const position = editor.getPosition();
      if (!fileId || !position) {
        return;
      }

      queueCursorBroadcast({
        roomId: roomIdRef.current,
        fileId,
        lineNumber: position.lineNumber,
        column: position.column,
        visible: true,
      });
    });
  };

  const handleCreateFile = () => {
    const name = createName.trim();
    if (!name) return;

    const language = createLanguage || getLanguageFromName(name);
    const id = crypto.randomUUID();
    const newFile = {
      id,
      name,
      language,
      content: createStarterFromLanguage(language),
      version: 0,
    };
    setFiles((prev) => [...prev, newFile]);
    setActiveFileId(id);
    sendWorkspaceEvent({
      roomId,
      type: "CREATE_FILE",
      file: newFile,
    });
    sendWorkspaceEvent({
      roomId,
      type: "SET_ACTIVE_FILE",
      fileId: id,
    });
    setShowCreate(false);
    setCreateName("");
    setCreateLanguage("java");
  };

  const handleDeleteFile = (id: string) => {
    flushPendingDeltaRef.current();
    let replacementFile: FileItem | null = null;
    let nextActiveId: string | null = null;

    setFiles((prev) => {
      const next = prev.filter((file) => file.id !== id);
      if (next.length === 0) {
        replacementFile = {
          id: crypto.randomUUID(),
          name: "Main.java",
          language: "java",
          content: createStarterFromLanguage("java"),
          version: 0,
        };
        nextActiveId = replacementFile.id;
        setActiveFileId(replacementFile.id);
        return [replacementFile];
      }

      if (activeFileId === id) {
        nextActiveId = next[0].id;
        setActiveFileId(next[0].id);
      }
      return next;
    });
    sendWorkspaceEvent({
      roomId,
      type: "DELETE_FILE",
      fileId: id,
    });
    if (replacementFile) {
      sendWorkspaceEvent({
        roomId,
        type: "CREATE_FILE",
        file: replacementFile,
      });
    }
    if (nextActiveId) {
      sendWorkspaceEvent({
        roomId,
        type: "SET_ACTIVE_FILE",
        fileId: nextActiveId,
      });
    }
  };

  const handleRenameFile = (id: string) => {
    const file = files.find((item) => item.id === id);
    if (!file) return;

    const newName = window.prompt("New file name (e.g. Main.java):", file.name);
    if (!newName) return;

    const trimmed = newName.trim();
    if (!trimmed) return;

    const nextLanguage = getLanguageFromName(trimmed);
    setFiles((prev) =>
      prev.map((item) =>
        item.id === id
          ? { ...item, name: trimmed, language: nextLanguage }
          : item,
      ),
    );
    sendWorkspaceEvent({
      roomId,
      type: "RENAME_FILE",
      fileId: id,
      name: trimmed,
    });
  };

  const handleUpdateLanguage = (id: string, language: string) => {
    setFiles((prev) =>
      prev.map((file) => (file.id === id ? { ...file, language } : file)),
    );
    sendWorkspaceEvent({
      roomId,
      type: "UPDATE_LANGUAGE",
      fileId: id,
      language,
    });
  };

  const handleRunCode = async () => {
    if (!activeFile) return;

    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    setRunState("running");
    setExecutionLog("");
    setTerminalInput("");
    setSessionId(null);
    setIsTerminalOpen(true);

    try {
      if (EXECUTION_STRATEGY === "PISTON") {
        const response = await fetch(getBackendApiUrl("/api/executions"), {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            language: activeFile.language,
            code: activeFile.content,
          }),
        });

        const data = (await response.json()) as
          | ExecutionResult
          | { message?: string };

        if (!response.ok) {
          throw new Error(
            "message" in data && data.message
              ? data.message
              : "Execution failed",
          );
        }

        if (!("output" in data)) {
          throw new Error("Invalid execution response");
        }

        const nextLog = [data.output, data.error].filter(Boolean).join("");
        setExecutionLog(nextLog || "Execution finished with no output.");
        setRunState(data.success ? "success" : "error");
        return;
      }

      const response = await fetch(
        getBackendApiUrl("/api/executions/interactive"),
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            language: activeFile.language,
            entryFile: activeFile.name,
            files: files.map((file) => ({
              name: file.name,
              content: file.content,
            })),
          }),
        },
      );

      const data = (await response.json()) as
        | InteractiveSessionResponse
        | { message?: string };

      if (!response.ok) {
        throw new Error(
          "message" in data && data.message ? data.message : "Execution failed",
        );
      }

      if (!("sessionId" in data)) {
        throw new Error("Invalid interactive session response");
      }

      setSessionId(data.sessionId);
      const socket = new WebSocket(getExecutionWebSocketUrl(data.sessionId));
      wsRef.current = socket;

      socket.onopen = () => {
        terminalInputRef.current?.focus();
      };

      socket.onmessage = (event) => {
        const message = JSON.parse(event.data) as TerminalMessage;

        if (message.type === "output" || message.type === "error") {
          setExecutionLog((prev) => prev + message.data);
          return;
        }

        if (message.type === "exit") {
          setRunState(message.exitCode === 0 ? "success" : "error");
          socket.close();
          wsRef.current = null;
        }
      };

      socket.onerror = () => {
        setExecutionLog((prev) => prev || "Terminal connection failed.");
        setRunState("error");
      };
    } catch (error) {
      setExecutionLog(
        error instanceof Error ? error.message : "Cannot connect to backend",
      );
      setRunState("error");
    }
  };

  const handleTerminalInput = (
    event: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (event.key !== "Enter") {
      return;
    }

    event.preventDefault();
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      return;
    }

    const nextInput = terminalInput;
    wsRef.current.send(
      JSON.stringify({
        type: "input",
        data: `${nextInput}\n`,
      }),
    );
    setExecutionLog((prev) => prev + nextInput + "\n");
    setTerminalInput("");
  };

  const handleNicknameSubmit = () => {
    const nextNickname = nicknameDraft.trim();
    if (!nextNickname) {
      return;
    }
    window.sessionStorage.setItem(NICKNAME_STORAGE_KEY, nextNickname);
    setNickname(nextNickname);
    setShowNicknameGate(false);
  };

  const handleCopyRoomLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopiedRoomLink(true);
    } catch {
      window.prompt("Copy room link:", window.location.href);
    }
  };

  const handleTerminalResizeStart = (
    event: React.PointerEvent<HTMLButtonElement>,
  ) => {
    const panel = mainPanelRef.current;
    if (!panel) {
      return;
    }

    event.preventDefault();
    const panelRect = panel.getBoundingClientRect();
    const minHeight = 220;
    const maxHeight = Math.max(220, panelRect.height - 180);

    const updateHeight = (clientY: number) => {
      const nextHeight = panelRect.bottom - clientY;
      setTerminalHeight(Math.min(maxHeight, Math.max(minHeight, nextHeight)));
    };

    updateHeight(event.clientY);

    const handlePointerMove = (moveEvent: PointerEvent) => {
      updateHeight(moveEvent.clientY);
    };

    const handlePointerUp = () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
  };

  if (!activeFile) {
    return null;
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_#11213d,_#050816_55%)] text-slate-100">
      <header className="border-b border-white/10 bg-slate-950/70 backdrop-blur">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between gap-6 px-4 py-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-cyan-400/30 bg-cyan-400/15 text-cyan-200">
              <Languages className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold uppercase tracking-[0.28em] text-cyan-200/90">
                McCE
              </div>
              <div className="truncate text-base font-semibold text-white">
                Multi-Lang Collaboration Cloud Engine
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button className="hidden items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3 py-2 text-xs font-semibold text-slate-200 transition hover:bg-white/[0.1] md:inline-flex">
              Room: {roomId}
            </button>

            <div className="hidden items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3 py-2 text-xs text-slate-300 xl:flex">
              <Users className="h-3.5 w-3.5 text-cyan-200" />
              {collaborationState === "live" ? "Live" : collaborationState}
            </div>

            <div className="flex items-center gap-2">
              {participants.slice(0, 5).map((participant) => (
                <div
                  key={participant.sessionId}
                  className={[
                    "flex h-9 w-9 items-center justify-center rounded-full border text-xs font-semibold uppercase",
                    getAvatarTone(participant.nickname),
                  ].join(" ")}
                  title={participant.nickname}
                >
                  {participant.nickname.slice(0, 2)}
                </div>
              ))}
              {participants.length > 5 && (
                <div className="flex h-9 w-9 items-center justify-center rounded-full border border-white/15 bg-white/[0.06] text-xs font-semibold text-slate-200">
                  +{participants.length - 5}
                </div>
              )}
            </div>
          </div>
        </div>
      </header>

      <div className="mx-auto flex max-w-[1600px] flex-col gap-4 px-4 py-4 sm:px-6">
        <section className="grid gap-4 xl:grid-cols-[300px_minmax(0,1fr)_360px]">
          <aside className="rounded-3xl border border-white/10 bg-slate-950/55 backdrop-blur">
            <div className="flex items-center justify-between border-b border-white/10 px-4 py-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-white">
                <FilePlus2 className="h-4 w-4 text-cyan-200" />
                Workspace files
              </div>
              <button
                ref={addFileButtonRef}
                onClick={() => setShowCreate((value) => !value)}
                className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-cyan-400/30 bg-cyan-400/15 px-3 py-2 text-xs font-semibold text-cyan-100 transition hover:bg-cyan-400/25"
              >
                <Plus className="h-3.5 w-3.5" />
                Add file
              </button>
            </div>

            <div
              ref={createPanelRef}
              className={[
                "overflow-hidden border-b border-white/10 px-4 transition-all duration-300 ease-out",
                showCreate
                  ? "max-h-72 translate-y-0 py-4 opacity-100"
                  : "pointer-events-none max-h-0 -translate-y-2 py-0 opacity-0",
              ].join(" ")}
            >
              <div className="space-y-2">
                <div className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                  New file
                </div>
                <input
                  value={createName}
                  onChange={(event) => setCreateName(event.target.value)}
                  placeholder="Main.java"
                  className="w-full rounded-2xl border border-white/10 bg-slate-900/80 px-3 py-2.5 text-sm text-white outline-none placeholder:text-slate-500 focus:border-cyan-300"
                />
                <select
                  aria-label="Choose language for new file"
                  title="Choose language for new file"
                  value={createLanguage}
                  onChange={(event) => setCreateLanguage(event.target.value)}
                  className="w-full cursor-pointer rounded-2xl border border-white/10 bg-slate-900/80 px-3 py-2.5 text-sm text-white outline-none focus:border-cyan-300"
                >
                  {languageOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
                <button
                  onClick={handleCreateFile}
                  className="w-full cursor-pointer rounded-2xl bg-emerald-500 px-3 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400"
                >
                  Create file
                </button>
              </div>
            </div>

            <div className="max-h-[620px] space-y-2 overflow-auto p-3">
              {files.map((file) => {
                const selected = file.id === activeFile.id;

                return (
                  <div
                    key={file.id}
                    className={[
                      "rounded-2xl border p-3 transition",
                      selected
                        ? "border-cyan-300/60 bg-cyan-400/10 shadow-[0_0_0_1px_rgba(103,232,249,0.08)]"
                        : "border-white/10 bg-white/[0.03]",
                    ].join(" ")}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <button
                        onClick={() => handleSelectActiveFile(file.id)}
                        className="min-w-0 flex-1 cursor-pointer text-left"
                      >
                        <div className="truncate text-sm font-semibold text-white">
                          {file.name}
                        </div>
                        <div className="mt-1 inline-flex rounded-full bg-white/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-slate-300">
                          {file.language}
                        </div>
                        <div className="mt-2 text-[11px] font-medium text-slate-400">
                          Revision {file.version}
                        </div>
                      </button>

                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleRenameFile(file.id)}
                          className="cursor-pointer rounded-full p-2 text-slate-300 transition hover:bg-white/10 hover:text-white"
                          title="Rename"
                        >
                          <PencilLine className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => handleDeleteFile(file.id)}
                          className="cursor-pointer rounded-full p-2 text-slate-300 transition hover:bg-white/10 hover:text-white"
                          title="Delete"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    {selected && (
                      <div className="mt-3">
                        <select
                          aria-label={`Change language for ${file.name}`}
                          title={`Change language for ${file.name}`}
                          value={file.language}
                          onChange={(event) =>
                            handleUpdateLanguage(file.id, event.target.value)
                          }
                          className="w-full cursor-pointer rounded-2xl border border-white/10 bg-slate-900/80 px-3 py-2 text-xs text-white outline-none focus:border-cyan-300"
                        >
                          {languageOptions.map((option) => (
                            <option key={option.id} value={option.id}>
                              {option.label}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </aside>

          <main
            ref={mainPanelRef}
            className="flex h-[720px] min-h-0 flex-col overflow-hidden rounded-3xl border border-white/10 bg-slate-950/55 backdrop-blur"
          >
            <div className="flex flex-col gap-3 border-b border-white/10 px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0">
                <div className="truncate text-lg font-semibold text-white">
                  {activeFile.name}
                </div>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-300">
                  <span className="rounded-full border border-white/10 px-2.5 py-1 uppercase tracking-[0.18em]">
                    {activeFile.language}
                  </span>

                  <span>{files.length} file(s) in workspace</span>
                  <span>{participants.length} collaborator(s)</span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setIsTerminalOpen((value) => !value)}
                  className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-4 py-2.5 text-sm font-semibold text-slate-200 transition hover:bg-white/[0.1]"
                >
                  <SquareTerminal className="h-4 w-4" />
                  {isTerminalOpen ? "Hide terminal" : "Show terminal"}
                </button>
                <button
                  onClick={handleRunCode}
                  className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300"
                >
                  <Play className="h-4 w-4" />
                  Run code
                </button>
              </div>
            </div>

            <div className="min-h-0 flex-1">
              <Editor
                key={activeFile.id}
                height="100%"
                language={activeFile.language}
                path={activeFile.id}
                theme="vs-dark"
                defaultValue={activeFile.content}
                onMount={handleEditorDidMount}
                options={{
                  minimap: { enabled: false },
                  fontSize: 13,
                  lineNumbersMinChars: 3,
                  padding: { top: 18 },
                  scrollBeyondLastLine: false,
                  wordWrap: "on",
                }}
              />
            </div>

            {isTerminalOpen && (
              <div
                className="flex min-h-0 shrink-0 flex-col border-t border-white/10 bg-slate-950/95"
                style={{ height: `${terminalHeight}px` }}
              >
                <button
                  type="button"
                  aria-label="Resize execution panel"
                  onPointerDown={handleTerminalResizeStart}
                  className="group flex h-4 w-full cursor-row-resize items-center justify-center"
                >
                  <span className="h-1.5 w-16 rounded-full bg-white/10 transition group-hover:bg-cyan-300/50" />
                </button>

                <div className="flex min-h-0 flex-1 flex-col px-4 pb-4">
                  <div className="flex items-center justify-between gap-3 border-b border-white/10 pb-3">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-2xl border border-cyan-400/20 bg-cyan-400/10 text-cyan-200">
                        <Activity className="h-4 w-4" />
                      </div>
                      <div>
                        <div className="text-sm font-semibold text-white">
                          Execution panel
                        </div>
                        <p className="text-xs text-slate-400">
                          Idle timeout: 10s without input. Drag to resize.
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <div
                        className={[
                          "rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]",
                          runState === "success"
                            ? "bg-emerald-400/15 text-emerald-200"
                            : runState === "running"
                              ? "bg-amber-400/15 text-amber-200"
                              : runState === "error"
                                ? "bg-rose-400/15 text-rose-200"
                                : "bg-white/10 text-slate-300",
                        ].join(" ")}
                      >
                        {runState}
                      </div>
                      <button
                        type="button"
                        onClick={() => setIsTerminalOpen(false)}
                        className="cursor-pointer rounded-full p-2 text-slate-400 transition hover:bg-white/10 hover:text-white"
                        title="Close terminal"
                      >
                        <X className="h-4 w-4" />
                      </button>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => terminalInputRef.current?.focus()}
                    className="mt-3 block min-h-0 flex-1 cursor-text overflow-hidden rounded-xl border border-white/10 bg-slate-950 px-3 py-3 text-left"
                  >
                    <pre
                      ref={terminalViewportRef}
                      className="h-full overflow-auto whitespace-pre-wrap break-words font-mono text-xs leading-6 text-emerald-200"
                    >
                      {executionLog || "Run code to start terminal"}
                    </pre>
                  </button>

                  <div className="mt-3 shrink-0 flex items-center gap-2 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2.5">
                    <span className="font-mono text-xs text-cyan-200">
                      {">"}
                    </span>
                    <input
                      ref={terminalInputRef}
                      value={terminalInput}
                      onChange={(event) => setTerminalInput(event.target.value)}
                      onKeyDown={handleTerminalInput}
                      disabled={runState !== "running" || !sessionId}
                      placeholder={
                        EXECUTION_STRATEGY === "PISTON"
                          ? "Interactive input is unavailable in PISTON mode"
                          : runState === "running"
                            ? "Nhap input va nhan Enter"
                            : "Run code to interact"
                      }
                      className="w-full bg-transparent font-mono text-sm text-white caret-cyan-200 outline-none placeholder:text-slate-500"
                    />
                  </div>
                </div>
              </div>
            )}
          </main>

          <aside className="space-y-4">
            <div className="rounded-3xl border border-white/10 bg-slate-950/55 p-4 backdrop-blur">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-white">
                    Collaboration
                  </div>
                  <p className="mt-1 text-xs text-slate-400">
                    Share this room link and everyone in the same room will see
                    code updates in near real time.
                  </p>
                </div>
                <div
                  className={[
                    "rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em]",
                    collaborationState === "live"
                      ? "bg-emerald-400/15 text-emerald-200"
                      : collaborationState === "connecting"
                        ? "bg-amber-400/15 text-amber-200"
                        : "bg-white/10 text-slate-300",
                  ].join(" ")}
                >
                  {collaborationState}
                </div>
              </div>

              <div className="mt-4 rounded-2xl border border-white/10 bg-slate-950/60 p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">
                    Room Link
                  </div>
                  <button
                    type="button"
                    onClick={handleCopyRoomLink}
                    className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-white/10 bg-white/[0.05] px-3 py-1.5 text-xs font-semibold text-slate-200 transition hover:bg-white/[0.1]"
                    title="Copy room link"
                  >
                    <Copy className="h-3.5 w-3.5" />
                    {copiedRoomLink ? "Copied" : "Copy"}
                  </button>
                </div>
                <div className="mt-2 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2 text-sm text-slate-200">
                  {typeof window === "undefined"
                    ? `/room/${roomId}`
                    : window.location.href}
                </div>
              </div>

              <div className="mt-4 rounded-2xl border border-white/10 bg-slate-950/60 p-3">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">
                  Active Members
                </div>
                <div className="mt-2 text-sm font-semibold text-cyan-100">
                  {participants.length} online now
                </div>
                <div className="mt-3 space-y-2">
                  {participants.length === 0 && (
                    <div className="text-sm text-slate-400">
                      Waiting for collaborators to join this room.
                    </div>
                  )}
                  {participants.map((participant) => (
                    <div
                      key={participant.sessionId}
                      className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-2.5"
                    >
                      <div
                        className={[
                          "flex h-9 w-9 items-center justify-center rounded-full border text-xs font-semibold uppercase",
                          getAvatarTone(participant.nickname),
                        ].join(" ")}
                      >
                        {participant.nickname.slice(0, 2)}
                      </div>
                      <div className="min-w-0">
                        <div className="truncate text-sm font-semibold text-white">
                          {participant.nickname}
                        </div>
                        <div className="truncate text-xs text-slate-400">
                          {participant.nickname === nickname
                            ? "You"
                            : "Collaborator"}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {Object.values(remoteCursors).length > 0 && (
                  <div className="mt-4 rounded-xl border border-white/10 bg-slate-950/60 p-3">
                    <div className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">
                      Live cursors
                    </div>
                    <div className="mt-2 space-y-2">
                      {Object.values(remoteCursors).map((cursor) => (
                        <div
                          key={cursor.sessionId}
                          className="flex items-center justify-between gap-3 text-xs text-slate-300"
                        >
                          <span className="truncate font-semibold text-white">
                            {cursor.nickname}
                          </span>
                          <span className="rounded-full border border-white/10 px-2 py-1 text-[11px] text-cyan-200">
                            {cursor.fileId === activeFileId
                              ? "this file"
                              : "other file"}{" "}
                            • L{cursor.lineNumber}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </aside>
        </section>
      </div>

      {showNicknameGate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur">
          <div className="w-full max-w-md rounded-3xl border border-white/10 bg-slate-950 p-6 shadow-2xl">
            <div className="text-sm font-semibold uppercase tracking-[0.24em] text-cyan-200">
              Join Room
            </div>
            <h2 className="mt-3 text-2xl font-semibold text-white">
              Pick a nickname before entering the workspace
            </h2>
            <p className="mt-2 text-sm text-slate-400">
              Everyone with this room link can join and edit together in real
              time.
            </p>
            <input
              autoFocus
              value={nicknameDraft}
              onChange={(event) => setNicknameDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  handleNicknameSubmit();
                }
              }}
              placeholder="Your nickname"
              className="mt-5 w-full rounded-2xl border border-white/10 bg-slate-900/80 px-4 py-3 text-sm text-white outline-none placeholder:text-slate-500 focus:border-cyan-300"
            />
            <button
              onClick={handleNicknameSubmit}
              className="mt-4 w-full cursor-pointer rounded-2xl bg-emerald-400 px-4 py-3 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300"
            >
              Enter workspace
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function getExecutionWebSocketUrl(sessionId: string) {
  return `${getBackendWebSocketBaseUrl()}/ws/executions/${sessionId}`;
}

function getCollaborationWebSocketUrl() {
  return `${getBackendWebSocketBaseUrl()}/ws-collab`;
}

function getBackendApiUrl(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${getBackendHttpBaseUrl()}${normalizedPath}`;
}

function getBackendHttpBaseUrl() {
  return trimTrailingSlash(
    process.env.NEXT_PUBLIC_API_URL ||
      process.env.NEXT_PUBLIC_BACKEND_HTTP_URL ||
      "http://localhost:8080",
  );
}

function getBackendWebSocketBaseUrl() {
  const explicitUrl = process.env.NEXT_PUBLIC_BACKEND_WS_URL;
  if (explicitUrl) {
    return trimTrailingSlash(explicitUrl);
  }

  const backendHttpBaseUrl = getBackendHttpBaseUrl();
  if (backendHttpBaseUrl.startsWith("https://")) {
    return `wss://${backendHttpBaseUrl.slice("https://".length)}`;
  }
  if (backendHttpBaseUrl.startsWith("http://")) {
    return `ws://${backendHttpBaseUrl.slice("http://".length)}`;
  }
  return backendHttpBaseUrl;
}

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, "");
}
