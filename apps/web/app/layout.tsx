import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Agendou",
  description: "SaaS de agendamento com trial Premium/Top de 7 dias"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
