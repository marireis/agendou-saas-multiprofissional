import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  distDir: process.env.AGENDOU_E2E === "1" ? ".next-e2e" : ".next",
  poweredByHeader: false,
  async headers() {
    // HTML pages only: preserve the API's private-file sandbox policy.
    const pages = ["/", "/entrar", "/cadastro", "/recuperar", "/verificar", "/painel/:path*", "/plataforma/:path*", "/a/:path*"];
    return pages.map(source => ({source, headers: [
      {key: "Referrer-Policy", value: "no-referrer"},
      {key: "X-Content-Type-Options", value: "nosniff"},
      {key: "X-Frame-Options", value: "DENY"},
      {key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()"},
      {key: "Content-Security-Policy", value: "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'"},
    ]}));
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${process.env.AGENDOU_API_URL || "http://localhost:8080"}/api/:path*` }];
  }
};

export default nextConfig;
