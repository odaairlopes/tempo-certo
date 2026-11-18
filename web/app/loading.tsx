import { Esqueleto } from "./carregamento";

export default function Loading() {
  return <section className="panel"><Esqueleto texto="Carregando página…" /></section>;
}
