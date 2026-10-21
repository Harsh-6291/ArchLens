import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ArchLens — Architecture Discovery",
  description: "Discover and explore the architecture of any GitHub repository. Detect services, databases, message brokers, and API routes automatically.",
  keywords: ["architecture", "github", "spring boot", "microservices", "visualization"],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet" />
      </head>
      <body style={{ margin: 0, padding: 0, height: '100%' }}>
        {children}
      </body>
    </html>
  );
}
