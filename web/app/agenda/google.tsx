"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";

type GoogleId = {
  initialize(options: { client_id: string; use_fedcm_for_button: boolean; callback: (resposta: { credential: string }) => void }): void;
  renderButton(element: HTMLElement, options: { theme: string; size: string; locale: string }): void;
};

export default function LoginGoogle({ entrar }: { entrar: (credential: string) => void }) {
  const caixa = useRef<HTMLDivElement>(null);
  const callback = useRef(entrar);
  useEffect(() => { callback.current = entrar; }, [entrar]);
  const [erro, setErro] = useState(false);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) return null;

  return (<div className="mb-4">
    <Script src="https://accounts.google.com/gsi/client" onError={() => setErro(true)} onReady={() => {
      const google = (window as Window & { google?: { accounts: { id: GoogleId } } }).google;
      if (!caixa.current || !google) { setErro(true); return; }
      google.accounts.id.initialize({ client_id: clientId, use_fedcm_for_button: true, callback: ({ credential }) => callback.current(credential) });
      google.accounts.id.renderButton(caixa.current, { theme: "outline", size: "large", locale: "pt_BR" });
    }} />
    <div ref={caixa} />
    {erro && <p role="alert">Não foi possível carregar o login Google. Tente novamente.</p>}
  </div>);
}
