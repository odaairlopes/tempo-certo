export function Progresso({ texto }: { texto: string }) {
  return <span role="status" className="progresso"><span aria-hidden="true" className="spinner" />{texto}</span>;
}

export function Esqueleto({ texto }: { texto: string }) {
  return <div role="status" className="carregamento">
    <span>{texto}</span>
    <div aria-hidden="true" className="barras"><i /><i /><i /></div>
  </div>;
}
