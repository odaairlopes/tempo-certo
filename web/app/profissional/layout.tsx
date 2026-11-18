import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Portal profissional · TempoCerto", robots: { index: false, follow: false } };
export default function LayoutProfissional({ children }: { children: ReactNode }) { return children; }
