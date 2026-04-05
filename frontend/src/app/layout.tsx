import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "McCE | Multi-Lang Collaboration Cloud Engine",
  description:
    "Nen tang lap trinh truc tuyen cho phep nhieu nguoi cung soan thao va thuc thi ma nguon da ngon ngu trong moi truong sandbox.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
