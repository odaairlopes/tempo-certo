"use client";

import { useEffect, useState } from "react";
import { enderecoFoto } from "../lib/api";

export default function Foto({ atual, escolher }: { atual?: string | null; escolher: (arquivo: File | null) => void }) {
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
  return <div className="grid gap-2">
    {src && <img className="avatar" src={src} alt="Foto de perfil" />}
    <label>Foto de perfil (JPEG ou PNG, até 1 MB)
      <input type="file" accept="image/jpeg,image/png" onChange={evento => {
        const entrada = evento.currentTarget;
        const item = entrada.files?.[0] ?? null;
        const invalido = item && (item.size > 1024 * 1024 || !["image/jpeg","image/png"].includes(item.type));
        if (invalido) entrada.value = "";
        setErro(invalido ? "Escolha uma foto JPEG ou PNG de até 1 MB." : "");
        setArquivo(invalido ? null : item);
        escolher(invalido ? null : item);
      }} /></label>
    {erro && <p role="alert" className="notice">{erro}</p>}
  </div>;
}
