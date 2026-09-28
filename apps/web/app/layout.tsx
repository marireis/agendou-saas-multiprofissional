import type { Metadata } from "next";
import "./globals.css";
import ThemeToggle from "./components/ThemeToggle";

export const metadata: Metadata = {
  title: "Agendou",
  description: "Seu cliente agenda. Você cuida do seu negócio."
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" data-theme="dark" suppressHydrationWarning>
      <head><script dangerouslySetInnerHTML={{__html:"try{document.documentElement.dataset.theme=localStorage.getItem('agendou-theme')==='light'?'light':'dark'}catch(e){}"}}/></head>
      <body><ThemeToggle/>{children}</body>
    </html>
  );
}

