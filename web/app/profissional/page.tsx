"use client";

import Consulta from "../agenda/consulta";
import { useSessao } from "../sessao";
import { notFound } from "next/navigation";

export default function Profissional() {
  const { sessao, pronta } = useSessao();
  if (!pronta) return <p role="status">Carregando…</p>;
  if (sessao) notFound();
  return <section className="grid items-start gap-8 md:grid-cols-2">
    <div><h1>Portal profissional</h1><p className="lead">Acesse sua agenda, seu perfil e sua disponibilidade.</p></div>
    <Consulta /></section>;
}
