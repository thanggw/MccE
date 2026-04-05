import Dashboard from "@/components/dashboard/Dashboard";

export default async function RoomPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  return <Dashboard roomId={roomId} />;
}
