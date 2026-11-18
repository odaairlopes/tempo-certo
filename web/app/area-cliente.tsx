"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, ApiErro, horarioLegivel } from "../lib/api";
import Foto from "./foto";
import Calendario from "./calendario";
import { AreaCliente, useSessao } from "./sessao";
import type { SessaoCliente } from "./conta-cliente";

type Perfil = { nome: string; email: string; foto: string | null };
type Atendimento = { id: string; profissional: string; dataHora: string };

function Painel({ sessao, perfil }: { sessao: SessaoCliente; perfil: boolean }) {
  const { salvar } = useSessao();
  const [dados, setDados] = useState<Perfil | null>(null);
  const [lista, setLista] = useState<Atendimento[] | null>(null);
  const [foto, setFoto] = useState<File | null>(null);
  const [aviso, setAviso] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState("");
  const [revisao, setRevisao] = useState(0);
  const token = sessao.token;
  useEffect(() => {
    let ativo = true;
    async function ler() {
      const opts = { headers: { Authorization: "Bearer " + token } };
      try {
        if (perfil) {
          const p = await api<Perfil>("/cliente/perfil", opts);
          if (ativo) setDados(p);
        } else {
          const a = await api<Atendimento[]>("/cliente/atendimentos", opts);
          if (ativo) setLista(a);
        }
      } catch (e) {
        if (!ativo) return;
        if (e instanceof ApiErro && e.status === 401) salvar(null);
        else setErro(e instanceof Error ? e.message : "Falha ao carregar.");
      }
    }
    void ler();
    return () => { ativo = false; };
  }, [token, perfil, revisao, salvar]);

  async function atualizar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = new FormData(event.currentTarget);
    if (foto) body.set("foto", foto);
    setOcupado(true); setErro(""); setAviso("");
    try {
      const p = await api<Perfil>("/cliente/perfil", { method: "PUT", headers: { Authorization: "Bearer " + token }, body });
      setDados(p); salvar({ ...sessao, nome: p.nome }); setFoto(null); setAviso("Perfil salvo.");
    } catch (e) {
      if (e instanceof ApiErro && e.status === 401) salvar(null);
      else setErro(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally { setOcupado(false); }
  }
  return <div className="panel grid gap-4">
    {erro && <><p role="alert">{erro}</p>{!dados && !lista && <button onClick={() => { setErro(""); setRevisao(v => v + 1); }}>Tentar novamente</button>}</>}
    {!dados && !lista && !erro && <p role="status">Carregando…</p>}
    {dados && <form onSubmit={atualizar}><fieldset disabled={ocupado} className="grid gap-4">
      <Foto atual={dados.foto} escolher={setFoto} />
      <label>Nome<input name="nome" defaultValue={dados.nome} required maxLength={120} /></label>
      <p>E-mail da conta · {dados.email}</p>
      <button>{ocupado ? "Salvando…" : "Salvar perfil"}</button>
    </fieldset></form>}
    {aviso && <p role="status">{aviso}</p>}
    {lista && (lista.length ? <ul>{lista.map(item => <li className="agenda-item grid gap-1" key={item.id}>
      <strong>{item.profissional}</strong><time dateTime={item.dataHora}>{horarioLegivel(item.dataHora)} · Brasília</time>
      <Calendario data={item.dataHora} profissional={item.profissional} /></li>)}</ul> : <p>Você ainda não marcou atendimentos.</p>)}
  </div>;
}
export default function PaginaCliente({ perfil = false }: { perfil?: boolean }) {
  return <section className="grid gap-6"><h1>{perfil ? "Meu perfil" : "Meus atendimentos"}</h1>
    <AreaCliente>{s => <Painel sessao={s} perfil={perfil} />}</AreaCliente></section>;
}
