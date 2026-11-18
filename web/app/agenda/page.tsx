import Consulta from "./consulta";

export default function Agenda() {
  return (
    <section className="grid items-start gap-8 md:grid-cols-2 md:gap-12">
      <div>
        <h1>Agenda profissional</h1>
        <p className="lead">Entre para consultar seus atendimentos e atualizar seu perfil e sua disponibilidade.</p>
      </div>
      <Consulta />
    </section>
  );
}
