"use client";

import { Esqueleto } from "./carregamento";

import { useEffect, useRef, useState } from "react";
import { api, horarioLegivel, type Agendamento, type Profissional } from "../lib/api";
import ContaCliente, { type SessaoCliente } from "./conta-cliente";
import DataHora from "./data-hora";

import Calendario from "./calendario";
import { useSessao } from "./sessao";
import { enderecoFoto } from "../lib/api";

const etapas = ["Conta", "Especialidade", "Profissional", "Data e hora"];
const titulos = ["Acesse sua conta", "Escolha a especialidade", "Escolha o profissional", "Escolha a data e o horário"];
const categoria = (p: Profissional) => (p.categoria || "Outros").trim().toLocaleLowerCase("pt-BR");

function Avatar({ perfil }: { perfil: Profissional }) {
  const [falhou, setFalhou] = useState(false);
  const iniciais = perfil.nome.trim().split(/\s+/).slice(0, 2).map(n => n[0]).join("");
  return perfil.fotoUrl && !falhou ? <img className="avatar" src={enderecoFoto(perfil.fotoUrl)} alt="" referrerPolicy="no-referrer"
    onError={() => setFalhou(true)} /> : <span aria-hidden="true" className="avatar">{iniciais}</span>;
}

export default function Reserva() {
  const { pronta, sessao, salvar } = useSessao();
  if (!pronta) return <Esqueleto texto="Carregando…" />;
  return <Fluxo key={sessao?.token ?? "visitante"} sessao={sessao} setSessao={salvar} />;
}

function Fluxo({ sessao, setSessao }: { sessao: SessaoCliente | null; setSessao: (s: SessaoCliente | null) => void }) {
  const [passo, setPasso] = useState(sessao ? 1 : 0);
  const [lista, setLista] = useState<Profissional[] | null>(null);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  const [especialidade, setEspecialidade] = useState("");
  const [perfil, setPerfil] = useState<Profissional | null>(null);
  const [salvo, setSalvo] = useState<Agendamento | null>(null);
  const titulo = useRef<HTMLHeadingElement>(null);
  const iniciou = useRef(false);
  useEffect(() => {
    if (iniciou.current) titulo.current?.focus();
    iniciou.current = true;
  }, [passo, salvo]);

  useEffect(() => {
    if (!sessao) return;
    let ativo = true;
    api<Profissional[]>("/profissionais").then(dados => { if (ativo) setLista(dados); },
      (falha: Error) => { if (ativo) setErro(falha.message); });
    return () => { ativo = false; };
  }, [sessao, tentativa]);

  function acessar(conta: SessaoCliente) {
    setSessao(conta); setLista(null); setErro(""); setPasso(1);
  }
  function sair() {
    setSessao(null); setPerfil(null); setEspecialidade(""); setSalvo(null); setErro(""); setPasso(0);
  }
  const categorias = new Map((lista ?? []).map(p => [categoria(p), p.categoria || "Outros"]));
  return <section className="panel grid gap-5" aria-label="Agendamento">
    <ol className="etapas" aria-label="Etapas do agendamento">{etapas.map((etapa, i) =>
      <li key={etapa} aria-current={i === passo ? "step" : undefined} data-completo={i < passo || !!salvo}>
        <span aria-hidden="true">{i + 1}</span>{etapa}</li>)}</ol>
    <h2 ref={titulo} tabIndex={-1} className="text-2xl font-bold">{salvo ? "Reserva confirmada" : titulos[passo]}</h2>
    {passo === 0 && <ContaCliente aoEntrar={acessar} />}
    {passo === 1 && <>
      <p>Olá, {sessao?.nome}.</p>
      {erro ? <><p role="alert">{erro}</p><button onClick={() => { setErro(""); setTentativa(v => v + 1); }}>Tentar novamente</button></>
        : !lista ? <Esqueleto texto="Carregando especialidades…" /> : !lista.length && <p>Nenhum profissional cadastrado.</p>}
      {[...categorias].sort((a, b) => a[1].localeCompare(b[1], "pt-BR")).map(([id, nome]) =>
        <button className="opcao" key={id} onClick={() => { setEspecialidade(id); setPerfil(null); setPasso(2); }}>{nome}</button>)}
      <button className="secundario" onClick={sair}>Trocar conta</button></>}
    {passo === 2 && <>
      {lista?.filter(p => categoria(p) === especialidade).map(p =>
        <button className="opcao" key={p.id} onClick={() => { setPerfil(p); setPasso(3); }}>
          <Avatar perfil={p} /><span>{p.nome}<small>{p.categoria}</small></span></button>)}
      <button className="secundario" onClick={() => setPasso(1)}>Voltar</button></>}
    {passo === 3 && perfil && sessao && (salvo ? <>
      <p role="status">{perfil.nome} · {horarioLegivel(salvo.dataHora)}</p>
      <Calendario data={salvo.dataHora} profissional={perfil.nome} />
      <button onClick={() => { setSalvo(null); setPerfil(null); setPasso(1); }}>Agendar outro atendimento</button></>
      : <DataHora perfil={perfil} sessao={sessao} confirmar={setSalvo} voltar={() => setPasso(2)} entrar={sair} />)}
  </section>;
}
