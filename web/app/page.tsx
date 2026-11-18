import Reserva from "./reserva";

export default function Home() {
  return (
    <section className="grid items-start gap-8 md:grid-cols-2 md:gap-12">
      <div>
        <h1>Agende seu atendimento</h1>
        <p className="lead">Entre na sua conta para escolher a especialidade, o profissional e o horário do atendimento.</p>
      </div>
      <Reserva />
    </section>
  );
}
