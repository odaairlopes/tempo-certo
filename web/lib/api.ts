export type Profissional = { id: string; nome: string; categoria: string; fotoUrl?: string | null };
export type Agendamento = {
  id: string;
  profissionalId: string;
  nomeCliente: string;
  emailCliente: string;
  dataHora: string;
};

export class ApiErro extends Error {
  constructor(public readonly status: number, mensagem: string) { super(mensagem); }
}

const base = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
export function enderecoFoto(url?: string | null) {
  return url?.startsWith("/profissionais/") ? base + url : url || undefined;
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const tempoLimite = base.startsWith("https://") && (!options?.method || options.method === "GET") ? 75_000 : 12_000;
  let response: Response;
  try {
    response = await fetch(base + path, {
      ...options,
      cache: "no-store",
      signal: options?.signal ?? AbortSignal.timeout(tempoLimite),
    });
  } catch {
    throw new Error("O serviço está indisponível. Tente novamente em instantes.");
  }
  if (!response.ok) {
    const problem: { detail?: unknown } | null = await response.json().catch(() => null);
    throw new ApiErro(response.status, typeof problem?.detail === "string" ? problem.detail : "Não foi possível concluir a operação.");
  }
  return response.json() as Promise<T>;
}

export function horarioLegivel(iso: string): string {
  const [data, hora] = iso.split("T");
  return data.split("-").reverse().join("/") + " às " + hora.slice(0, 5);
}
