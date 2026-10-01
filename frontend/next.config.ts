import type { NextConfig } from "next";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Set by the Docker build so the image can run with `node server.js`.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  async rewrites() {
    // Lesson Markdown references images as /api/tools/<slug>/assets/<file>
    return [
      {
        source: "/api/tools/:slug/assets/:file",
        destination: `${BACKEND}/api/tools/:slug/assets/:file`,
      },
    ];
  },
};

export default nextConfig;
