"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, horarioLegivel, type Agendamento, type Profissional } from "../lib/api";

type Horarios = { configurada: boolean; horarios: string[] };

export default function Reserva() {
  const [lista, setLista] = useState<Profissional[] | null>(null);
  const [falhaLista, setFalhaLista] = useState("");
  const [tentativa, setTentativa] = useState(0);
  const [erro, setErro] = useState("");
  const [salvo, setSalvo] = useState<Agendamento | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [profissional, setProfissional] = useState("");
  const [categoria, setCategoria] = useState("");
  const categorias = new Map<string, string>();
  for (const item of lista ?? []) {
    const nome = item.categoria || "Outros";
    categorias.set(nome.toLocaleLowerCase("pt-BR"), nome);
  }
  const [data, setData] = useState("");
  const [revisao, setRevisao] = useState(0);
  const [consulta, setConsulta] = useState<{ chave: string; horarios?: Horarios; erro?: string }>({ chave: "" });
  const chaveConsulta = `${profissional}/${data}/${revisao}`;
  const resultado = consulta.chave === chaveConsulta ? consulta : null;
  const horarios = resultado?.horarios;

  useEffect(() => {
    if (!profissional || !data) return;
    let ativo = true;
    api<Horarios>(`/profissionais/${encodeURIComponent(profissional)}/horarios?data=${encodeURIComponent(data)}`).then(
      (dados) => { if (ativo) setConsulta({ chave: chaveConsulta, horarios: dados }); },
      (falha: Error) => { if (ativo) setConsulta({ chave: chaveConsulta, erro: falha.message }); },
    );
    return () => { ativo = false; };
  }, [profissional, data, chaveConsulta]);

  useEffect(() => {
    let ativo = true;
    api<Profissional[]>("/profissionais").then(
      (dados) => { if (ativo) setLista(dados); },
      (falha: Error) => { if (ativo) setFalhaLista(falha.message); },
    );
    return () => { ativo = false; };
  }, [tentativa]);

  async function reservar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (enviando || !horarios) return;
    const form = event.currentTarget;
    const dados = Object.fromEntries(new FormData(form));
    setEnviando(true);
    setErro("");
    setSalvo(null);
    try {
      setSalvo(await api<Agendamento>("/agendamentos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          profissionalId: profissional,
          dataHora: `${data}T${dados.hora}`,
          nomeCliente: dados.nomeCliente,
          emailCliente: dados.emailCliente,
        }),
      }));
      form.reset();
      setProfissional("");
      setData("");
      setRevisao((valor) => valor + 1);
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível reservar.");
      setRevisao((valor) => valor + 1);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="panel grid gap-4" onSubmit={reservar} aria-label="Nova reserva">
      {falhaLista && (
        <div className="grid gap-2">
          <p role="alert" className="notice">{falhaLista}</p>
          <button type="button" onClick={() => { setFalhaLista(""); setTentativa(tentativa + 1); }}>Recarregar profissionais</button>
        </div>
      )}
      <fieldset className="grid gap-4" disabled={enviando || !lista?.length}>
        <div className="grid gap-1.5">
          <label htmlFor="categoria">Categoria profissional</label>
          <select id="categoria" value={categoria} onChange={event => {
            setCategoria(event.target.value);
            setProfissional("");
          }}>
            <option value="">Todas as categorias</option>
            {[...categorias.entries()].sort((a, b) => a[1].localeCompare(b[1], "pt-BR"))
              .map(([valor, nome]) => <option key={valor} value={valor}>{nome}</option>)}
          </select>
        </div>
        <div className="grid gap-1.5">
          <label htmlFor="profissional">Profissional</label>
          <select id="profissional" name="profissionalId" value={profissional}
            onChange={(event) => {
              setProfissional(event.target.value);
              setRevisao((valor) => valor + 1);
            }} required>
            <option value="" disabled>{lista ? "Selecione" : "Carregando…"}</option>
            {lista?.filter(item => !categoria || (item.categoria || "Outros").toLocaleLowerCase("pt-BR") === categoria)
              .map(item => <option key={item.id} value={item.id}>{item.nome}</option>)}
          </select>
        </div>
        <label>Data
          <input type="date" value={data} onChange={(event) => {
            setData(event.target.value);
            setRevisao((valor) => valor + 1);
          }} required />
        </label>
        {horarios && (horarios.configurada ? (
          <div className="grid gap-1.5">
            <label htmlFor="hora">Horário</label>
            <select id="hora" name="hora" required key={chaveConsulta} aria-describedby="fuso">
              <option value="">Selecione</option>
              {horarios.horarios.map((hora) => <option key={hora} value={hora}>{hora.slice(0, 5)}</option>)}
            </select>
          </div>
        ) : (
          <label>Horário
            <input name="hora" type="time" required aria-describedby="fuso" />
          </label>
        ))}
        <p role="status">
          {profissional && data && !resultado && "Consultando horários…"}
          {horarios?.configurada && !horarios.horarios.length && "Nenhum horário disponível nesta data."}
        </p>
        {resultado?.erro && (
          <div className="grid gap-2">
            <p role="alert" className="notice">{resultado.erro}</p>
            <button type="button" onClick={() => setRevisao((valor) => valor + 1)}>Consultar horários novamente</button>
          </div>
        )}
        <p id="fuso" className="text-sm">Horário de Brasília</p>
        <label>Seu nome
          <input name="nomeCliente" autoComplete="name" maxLength={120} required />
        </label>
        <label>Seu e-mail
          <input name="emailCliente" type="email" autoComplete="email" maxLength={254} required />
        </label>
        <button type="submit" disabled={!horarios || (horarios.configurada && !horarios.horarios.length)}>
          {enviando ? "Reservando…" : "Confirmar agendamento"}
        </button>
      </fieldset>
      {lista?.length === 0 && <p role="status">Nenhum profissional cadastrado.</p>}
      <div>
        <p role="alert" className="notice">{erro}</p>
        <p role="status" className="result">{salvo && `Reserva confirmada para ${horarioLegivel(salvo.dataHora)}.`}</p>
      </div>
    </form>
  );
}
