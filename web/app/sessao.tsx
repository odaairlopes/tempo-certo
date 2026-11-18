"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import ContaCliente, { type SessaoCliente } from "./conta-cliente";

type Contexto = { sessao: SessaoCliente | null; pronta: boolean; salvar: (s: SessaoCliente | null) => void };
const contexto = createContext<Contexto | null>(null);
const chave = "tempocerto.cliente";

export function ProvedorSessao({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<SessaoCliente | null>(null);
  const [pronta, setPronta] = useState(false);
  useEffect(() => {
    try {
      const s = JSON.parse(sessionStorage.getItem(chave) ?? "null");
      if (s && typeof s.nome === "string" && typeof s.token === "string" && s.expira > Date.now()) setSessao(s);
    } catch { /* O login também funciona apenas em memória. */ }
    setPronta(true);
  }, []);
  const salvar = useCallback((s: SessaoCliente | null) => {
    setSessao(s);
    try {
      if (s) sessionStorage.setItem(chave, JSON.stringify({ ...s, expira: Date.now() + 8 * 3600_000 }));
      else sessionStorage.removeItem(chave);
    } catch { /* O navegador pode bloquear o armazenamento por aba. */ }
  }, []);
  return <contexto.Provider value={{ sessao, pronta, salvar }}>{children}</contexto.Provider>;
}
export function useSessao() {
  const valor = useContext(contexto);
  if (!valor) throw new Error("Sessão indisponível.");
  return valor;
}
export function Menu() {
  const { sessao, salvar } = useSessao();
  const caminho = usePathname();
  if (caminho.startsWith("/profissional")) return null;
  return <nav aria-label="Navegação principal" className="flex flex-wrap items-center gap-5">
    <Link href="/">Agendamento</Link>
    {sessao && <><Link href="/atendimentos">Meus atendimentos</Link><Link href="/perfil">Meu perfil</Link>
      <button type="button" className="secundario" onClick={() => salvar(null)}>Sair</button></>}
  </nav>;
}
export function AreaCliente({ children }: { children: (s: SessaoCliente) => ReactNode }) {
  const { pronta, sessao, salvar } = useSessao();
  if (!pronta) return <p role="status">Carregando…</p>;
  return sessao ? <div key={sessao.token}>{children(sessao)}</div> : <div className="panel"><ContaCliente aoEntrar={salvar} /></div>;
}
