import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import "./globals.css";
import { Menu, ProvedorSessao } from "./sessao";

export const metadata: Metadata = {
  title: "TempoCerto",
  description: "Agendamento de atendimentos e agenda profissional.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body><ProvedorSessao>
        <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
        <header className="site-header">
          <div className="shell flex flex-wrap items-center justify-between gap-4">
            <Link className="wordmark" href="/">Tempo<span>Certo</span></Link>
            <Menu />
          </div>
        </header>
        <main className="shell" id="conteudo" tabIndex={-1}>{children}</main>
      </ProvedorSessao></body>
    </html>
  );
}
