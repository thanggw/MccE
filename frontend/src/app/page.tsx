import { randomUUID } from "crypto";
import { redirect } from "next/navigation";

function createRoomId() {
  const token = randomUUID().replace(/-/g, "").slice(0, 8);
  return `${token.slice(0, 4)}-${token.slice(4)}`;
}

export default function Home() {
  redirect(`/room/${createRoomId()}`);
}
