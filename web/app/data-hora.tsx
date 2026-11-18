"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, ApiErro, type Agendamento, type Profissional } from "../lib/api";
import type { SessaoCliente } from "./conta-cliente";

const fuso = "America/Sao_Paulo";
const diaLocal = new Intl.DateTimeFormat("sv-SE", { timeZone: fuso, year: "numeric", month: "2-digit", day: "2-digit" });
const horaLocal = new Intl.DateTimeFormat("pt-BR", { timeZone: fuso, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
type Horarios = { configurada: boolean; horarios: string[] };

export default function DataHora({ perfil, sessao, confirmar, voltar, entrar }: {
  perfil: Profissional; sessao: SessaoCliente; confirmar: (reserva: Agendamento) => void;
  voltar: () => void; entrar: () => void;
}) {
  const [texto, setTexto] = useState("");
  const [data, setData] = useState("");
  const [hora, setHora] = useState("");
  const [consulta, setConsulta] = useState<Horarios | null>(null);
  const [revisao, setRevisao] = useState(0);
  const [erro, setErro] = useState("");
  const [falhaConsulta, setFalhaConsulta] = useState(false);
  const [enviando, setEnviando] = useState(false);

  function alterar(valor: string) {
    const numeros = valor.replace(/\D/g, "").slice(0, 8);
    const dia = numeros.slice(0, 2), mes = numeros.slice(2, 4), ano = numeros.slice(4);
    setTexto([dia, mes, ano].filter(Boolean).join("/"));
    setData(""); setHora(""); setConsulta(null); setErro(""); setFalhaConsulta(false);
    if (ano.length !== 4) return;
    const iso = `${ano}-${mes}-${dia}`;
    const instante = new Date(iso + "T12:00:00Z");
    if (Number.isNaN(instante.getTime()) || instante.toISOString().slice(0, 10) !== iso || iso < diaLocal.format(new Date())) {
      setTexto("");
      setErro("Informe uma data válida a partir de hoje.");
      return;
    }
    setData(iso);
    setRevisao(v => v + 1);
  }

  useEffect(() => {
    if (!data) return;
    let ativo = true;
    api<Horarios>(`/profissionais/${encodeURIComponent(perfil.id)}/horarios?data=${data}`).then(
      dados => { if (ativo) setConsulta(dados); },
      (falha: Error) => { if (ativo) { setErro(falha.message); setFalhaConsulta(true); } },
    );
    return () => { ativo = false; };
  }, [data, perfil.id, revisao]);

  async function reservar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (enviando || !consulta || !hora) return;
    const agora = new Date();
    if (data < diaLocal.format(agora)) { alterar(""); setErro("A data passou. Escolha outra data."); return; }
    if (data === diaLocal.format(agora) && hora.slice(0, 5) <= horaLocal.format(agora)) {
      setHora(""); setErro("Escolha um horário futuro."); return;
    }
    setEnviando(true); setErro("");
    try {
      confirmar(await api<Agendamento>("/agendamentos", {
        method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${sessao.token}` },
        body: JSON.stringify({ profissionalId: perfil.id, dataHora: `${data}T${hora}` }),
      }));
    } catch (falha) {
      if (falha instanceof ApiErro && falha.status === 401) { entrar(); return; }
      setErro(falha instanceof Error ? falha.message : "Não foi possível reservar.");
      setConsulta(null); setHora(""); setRevisao(v => v + 1);
    } finally { setEnviando(false); }
  }

  return <form onSubmit={reservar}><fieldset className="grid gap-4" disabled={enviando}>
    <p>{perfil.nome} · {perfil.categoria}</p>
    <label>Data (dia/mês/ano)<input inputMode="numeric" placeholder="dd/mm/aaaa" maxLength={10}
      value={texto} onChange={e => alterar(e.target.value)} required /></label>
    {data && !consulta && !falhaConsulta && <p role="status">Consultando horários…</p>}
    {consulta && (consulta.configurada ?
      <label>Horários disponíveis<select required value={hora} onChange={e => setHora(e.target.value)}>
        <option value="">{consulta.horarios.length ? "Selecione um horário" : "Nenhum horário disponível"}</option>
        {consulta.horarios.map(h => <option key={h} value={h}>{h.slice(0, 5)}</option>)}
      </select></label> :
      <label>Horário<input type="time" required value={hora} onChange={e => setHora(e.target.value)} /></label>)}
    <p className="text-sm">Horário de Brasília</p>
    {erro && <p role="alert" className="notice">{erro}</p>}
    {falhaConsulta && <button type="button" onClick={() => { setFalhaConsulta(false); setErro(""); setRevisao(v => v + 1); }}>Consultar novamente</button>}
    <button disabled={!consulta || !hora}>{enviando ? "Reservando…" : "Confirmar agendamento"}</button>
    <button type="button" className="secundario" onClick={voltar}>Voltar</button>
  </fieldset></form>;
}
