import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "TempoCerto",
  description: "Agendamento de atendimentos e agenda profissional.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
        <header className="site-header">
          <div className="shell flex flex-wrap items-center justify-between gap-4">
            <Link className="wordmark" href="/">Tempo<span>Certo</span></Link>
            <nav aria-label="Navegação principal" className="flex flex-wrap gap-5">
              <Link href="/">Agendamento</Link>
              <Link href="/agenda">Agenda profissional</Link>
            </nav>
          </div>
        </header>
        <main className="shell" id="conteudo" tabIndex={-1}>{children}</main>
      </body>
    </html>
  );
}
