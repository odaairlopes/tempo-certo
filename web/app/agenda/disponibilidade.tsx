"use client";

import { useState, type FormEvent } from "react";
import { api } from "../../lib/api";

const dias = [
  ["MONDAY", "Segunda-feira"], ["TUESDAY", "Terça-feira"], ["WEDNESDAY", "Quarta-feira"],
  ["THURSDAY", "Quinta-feira"], ["FRIDAY", "Sexta-feira"], ["SATURDAY", "Sábado"], ["SUNDAY", "Domingo"],
] as const;

type Disponibilidade = {
  expedientes: { dia: typeof dias[number][0]; inicio: string; fim: string }[];
  folgas: string[];
};

export type Configuracao = { disponibilidade: Disponibilidade | null };

export default function EditarDisponibilidade({ rota, chave, inicial }: {
  rota: string; chave: string; inicial: Disponibilidade | null;
}) {
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState("");
  const [status, setStatus] = useState("");

  async function salvar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const dados = new FormData(event.currentTarget);
    const expedientes = dias.flatMap(([dia]) => {
      const inicio = String(dados.get(dia + "-inicio") ?? "");
      const fim = String(dados.get(dia + "-fim") ?? "");
      return inicio || fim ? [{ dia, inicio, fim }] : [];
    });
    setStatus("");
    if (expedientes.some(({ inicio, fim }) => !inicio || !fim)) {
      setErro("Preencha o início e o fim de cada dia de atendimento.");
      return;
    }
    setErro("");
    setOcupado(true);
    try {
      await api<Configuracao>(rota, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Agenda-Key": chave },
        body: JSON.stringify({
          expedientes,
          folgas: String(dados.get("folgas")).split(/\s+/).filter(Boolean),
        }),
      });
      setStatus("Disponibilidade salva.");
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível salvar.");
    } finally {
      setOcupado(false);
    }
  }

  return (
    <form className="mt-6" onSubmit={salvar} aria-labelledby="titulo-disponibilidade">
      <h2 id="titulo-disponibilidade" className="text-xl font-bold">Disponibilidade</h2>
      <p className="my-3">Use horários inteiros ou de meia em meia hora. Dias em branco ficam fechados após salvar.</p>
      <fieldset className="grid gap-4" disabled={ocupado}>
        {dias.map(([dia, nome]) => {
          const expediente = inicial?.expedientes.find((item) => item.dia === dia);
          return (
            <fieldset key={dia} className="grid grid-cols-2 gap-2">
              <legend className="font-semibold">{nome}</legend>
              <label>Início
                <input type="time" step={1800} name={dia + "-inicio"} defaultValue={expediente?.inicio.slice(0, 5)} />
              </label>
              <label>Fim
                <input type="time" step={1800} name={dia + "-fim"} defaultValue={expediente?.fim.slice(0, 5)} />
              </label>
            </fieldset>
          );
        })}
        <label>Folgas
          <span className="text-sm font-normal">Uma data por linha no formato AAAA-MM-DD.</span>
          <textarea name="folgas" rows={3} className="rounded-md border border-[#71877d] p-2"
            maxLength={4026} defaultValue={inicial?.folgas.join("\n")} />
        </label>
        <button type="submit">{ocupado ? "Salvando…" : "Salvar disponibilidade"}</button>
      </fieldset>
      <p role="alert" className="notice">{erro}</p>
      <p role="status" className="result">{status}</p>
    </form>
  );
}
