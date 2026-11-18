"use client";

import { useState, type FormEvent } from "react";
import { api } from "../lib/api";
import LoginGoogle from "./agenda/google";

export type SessaoCliente = { nome: string; token: string };

export default function ContaCliente({ aoEntrar }: { aoEntrar: (sessao: SessaoCliente) => void }) {
  const [cadastro, setCadastro] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");

  async function autenticar(rota: string, dados: object) {
    if (enviando) return;
    setEnviando(true);
    setErro("");
    try {
      aoEntrar(await api<SessaoCliente>(`/auth/clientes/${rota}`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(dados),
      }));
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível entrar.");
    } finally { setEnviando(false); }
  }

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void autenticar(cadastro ? "cadastro" : "login", Object.fromEntries(new FormData(event.currentTarget)));
  }

  return <div className="grid gap-4">
    <LoginGoogle entrar={credential => void autenticar("google", { credential })} />
    <form onSubmit={enviar} key={String(cadastro)}>
      <fieldset disabled={enviando} className="grid gap-4">
        {cadastro && <label>Seu nome<input name="nome" autoComplete="name" required maxLength={120} /></label>}
        <label>E-mail<input type="email" name="email" autoComplete="email" required maxLength={254} /></label>
        <label>{cadastro ? "Senha (mínimo de 8 caracteres)" : "Senha"}
          <input type="password" name="senha" autoComplete={cadastro ? "new-password" : "current-password"}
            required minLength={cadastro ? 8 : undefined} maxLength={128} /></label>
        <button>{enviando ? "Aguarde…" : cadastro ? "Criar conta e continuar" : "Entrar"}</button>
        <button type="button" className="secundario" onClick={() => { setErro(""); setCadastro(!cadastro); }}>
          {cadastro ? "Já tenho conta" : "Criar uma conta"}</button>
      </fieldset>
    </form>
    {erro && <p role="alert" className="notice">{erro}</p>}
  </div>;
}
