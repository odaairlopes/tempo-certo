import Reserva from "./reserva";

export default function Home() {
  return (
    <section className="grid items-start gap-8 md:grid-cols-2 md:gap-12">
      <div>
        <h1>Agende seu atendimento</h1>
        <p className="lead">Escolha um profissional e informe o horário da sua preferência.</p>
      </div>
      <Reserva />
    </section>
  );
}
