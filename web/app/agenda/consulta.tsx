"use client";

import { Progresso } from "../carregamento";

import { useState, type FormEvent } from "react";
import { api, horarioLegivel, type Agendamento, type Profissional } from "../../lib/api";
import EditarDisponibilidade, { type Configuracao } from "./disponibilidade";
import Foto from "../foto";
import LoginGoogle from "./google";

export default function Consulta() {
  const [agenda, setAgenda] = useState<Agendamento[] | null>(null);
  const [erro, setErro] = useState("");
  const [buscando, setBuscando] = useState(false);
  const [perfil, setPerfil] = useState<Profissional | null>(null);
  const [foto, setFoto] = useState<File | null>(null);
  const [enviandoFoto, setEnviandoFoto] = useState(false);
  const [avisoFoto, setAvisoFoto] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [editor, setEditor] = useState<(Configuracao & { rota: string; chave: string }) | null>(null);

  async function consultar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (buscando) return;
    const dados = new FormData(event.currentTarget);
    const id = String(dados.get("id")).trim();
    const chave = String(dados.get("chave")).trim();
    await abrirAgenda(id, chave);
  }

  async function abrirAgenda(id: string, chave: string) {
    if (!id || !chave) {
      setErro("Informe o identificador profissional e a chave da agenda.");
      return;
    }
    setBuscando(true);
    setAgenda(null);
    setEditor(null);
    setFoto(null); setAvisoFoto("");
    setAviso("");
    setErro("");
    try {
      const identificador = encodeURIComponent(id);
      const rota = `/profissionais/${identificador}/disponibilidade`;
      const opcoes = { headers: { "X-Agenda-Key": chave } };
      const [reservas, configuracao, perfis] = await Promise.all([
        api<Agendamento[]>(`/agendamentos/profissional/${identificador}`, opcoes),
        api<Configuracao>(rota, opcoes),
        api<Profissional[]>("/profissionais"),
      ]);
      setAgenda(reservas);
      setPerfil(perfis.find(item => item.id === id) ?? null);
      setEditor({ ...configuracao, rota, chave });
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível consultar a agenda.");
    } finally {
      setBuscando(false);
    }
  }

  return (
    <div className="panel">
      {!editor && !buscando && <LoginGoogle entrar={async credential => {
        setBuscando(true);
        setErro("");
        try {
          const sessao = await api<{ id: string; chaveAgenda: string }>("/auth/google", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ credential }),
          });
          await abrirAgenda(sessao.id, sessao.chaveAgenda);
        } catch (falha) {
          setErro(falha instanceof Error ? falha.message : "Falha ao entrar com Google.");
          setBuscando(false);
        }
      }} />}
      {editor && <button type="button" onClick={() => { setEditor(null); setAgenda(null); }}>Fechar agenda</button>}
      {!editor && <form onSubmit={consultar} onChange={() => { setAgenda(null); setEditor(null); setErro(""); }}>
        <fieldset disabled={buscando} className="grid gap-4">
          <label>Identificador profissional
            <input name="id" autoComplete="username" required maxLength={128} />
          </label>
          <label>Chave da agenda
            <input name="chave" type="password" autoComplete="current-password" required maxLength={128} />
          </label>
          <button type="submit">{buscando ? <Progresso texto="Consultando agenda…" /> : "Consultar agenda"}</button>
        </fieldset>
      </form>}
      <p role="alert" className="notice mt-4 empty:mt-0">{erro}</p>
      <p role="status" className="result mt-4 empty:mt-0">
        {agenda && (agenda.length ? `${agenda.length} agendamento(s)` : "Nenhum agendamento encontrado.")}
      </p>
      {agenda && agenda.length > 0 && (
        <ul className="mt-4" aria-label="Atendimentos marcados">
          {agenda.map((item) => (
            <li className="agenda-item" key={item.id}>
              <time dateTime={item.dataHora} className="font-bold">{horarioLegivel(item.dataHora)}</time>
              <p>{item.nomeCliente}</p>
              <p>{item.emailCliente}</p>
            </li>
          ))}
        </ul>
      )}
      {editor && <EditarDisponibilidade rota={editor.rota} chave={editor.chave} inicial={editor.disponibilidade} />}
      {editor && perfil && <form className="mt-6 grid gap-3" onSubmit={async event => {
        event.preventDefault();
        const dados = new FormData(event.currentTarget);
        if (salvando) return;
        setSalvando(true);
        const categoria = String(dados.get("categoria")).trim();
        setAviso("");
        try {
          const atualizado = await api<Profissional>(`/profissionais/${encodeURIComponent(perfil.id)}/categoria`, {
            method: "PUT", headers: { "Content-Type": "application/json", "X-Agenda-Key": editor.chave },
            body: JSON.stringify({ categoria }),
          });
          setPerfil(atualizado);
          setAviso("Perfil salvo.");
        } catch (falha) { setAviso(falha instanceof Error ? falha.message : "Falha ao salvar."); }
        finally { setSalvando(false); }
      }}>
        <label>Categoria profissional<input name="categoria" disabled={salvando} required maxLength={60} defaultValue={perfil.categoria} /></label>
        <button disabled={salvando}>{salvando ? <Progresso texto="Salvando perfil…" /> : "Salvar perfil"}</button>
        <p role="status">{aviso}</p>
      </form>}
      {editor && perfil && <form className="mt-6 grid gap-3" onSubmit={async event => {
        event.preventDefault();
        if (!foto || enviandoFoto) return;
        const body = new FormData();
        body.set("foto", foto);
        setEnviandoFoto(true); setAvisoFoto("");
        try {
          const dados = await api<{ fotoUrl: string }>(`/profissionais/${encodeURIComponent(perfil.id)}/foto`, {
            method: "PUT", headers: { "X-Agenda-Key": editor.chave }, body,
          });
          setPerfil(p => p && ({ ...p, fotoUrl: dados.fotoUrl })); setFoto(null); setAvisoFoto("Foto atualizada.");
        } catch (e) { setAvisoFoto(e instanceof Error ? e.message : "Falha ao enviar foto."); }
        finally { setEnviandoFoto(false); }
      }}><fieldset className="grid gap-3" disabled={enviandoFoto}>
        <Foto nome={perfil.nome} key={perfil.fotoUrl} atual={perfil.fotoUrl} escolher={setFoto} />
        <button disabled={!foto}>{enviandoFoto ? <Progresso texto="Enviando foto…" /> : "Salvar foto"}</button>
      </fieldset><p role="status">{avisoFoto}</p></form>}
    </div>
  );
}
