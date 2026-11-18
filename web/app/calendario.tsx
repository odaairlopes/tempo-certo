export default function Calendario({ data, profissional }: { data: string; profissional: string }) {
  // A reserva é armazenada como horário civil de Brasília. O Google interpreta as datas pelo ctz.
  const inicio = new Date(data.slice(0, 19) + "Z");
  const fim = new Date(inicio.getTime() + 30 * 60_000);
  const formato = (d: Date) => d.toISOString().slice(0, 19).replace(/[-:]/g, "");
  const parametros = new URLSearchParams({ action: "TEMPLATE", text: "Atendimento com " + profissional,
    dates: formato(inicio) + "/" + formato(fim), ctz: "America/Sao_Paulo" });
  return <a className="underline" href={"https://calendar.google.com/calendar/render?" + parametros}
    target="_blank" rel="noopener noreferrer">Adicionar ao Google Agenda</a>;
}
