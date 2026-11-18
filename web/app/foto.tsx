"use client";

import { useEffect, useId, useState } from "react";
import { enderecoFoto } from "../lib/api";

export default function Foto({ atual, escolher, nome }: { nome?: string; atual?: string | null; escolher: (arquivo: File | null) => void }) {
  const ajuda = useId();
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [erro, setErro] = useState("");
  useEffect(() => {
    if (!arquivo) { setPreview(""); return; }
    const url = URL.createObjectURL(arquivo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [arquivo]);
  const src = preview || enderecoFoto(atual);
  const iniciais = nome?.trim().split(/\s+/).slice(0, 2).map(n => n[0]).join("").toUpperCase();
  return <div className="foto-editor">
    {src ? <img className="avatar" src={src} alt="Foto de perfil" /> : <span className="avatar" aria-hidden="true">{iniciais || "＋"}</span>}
    <div><strong>Foto de perfil</strong><p id={ajuda} className="foto-ajuda">JPEG ou PNG, até 1 MB</p>
    <label className="foto-acao">{src ? "Trocar foto" : "Escolher foto"}
      <input className="sr-only" aria-describedby={ajuda} type="file" accept="image/jpeg,image/png" onChange={evento => {
        const entrada = evento.currentTarget;
        const item = entrada.files?.[0] ?? null;
        const invalido = item && (item.size > 1024 * 1024 || !["image/jpeg","image/png"].includes(item.type));
        if (invalido) entrada.value = "";
        setErro(invalido ? "Escolha uma foto JPEG ou PNG de até 1 MB." : "");
        setArquivo(invalido ? null : item);
        escolher(invalido ? null : item);
      }} /></label>{arquivo && <p className="foto-ajuda" role="status">{arquivo.name}</p>}</div>
    {erro && <p role="alert" className="notice">{erro}</p>}
  </div>;
}
