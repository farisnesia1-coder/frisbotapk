const UPSTREAM_URL = "https://enrai.web.id/v1/chat/completions";
const MAX_BODY_BYTES = 8 * 1024 * 1024;

function corsHeaders(origin) {
  return {
    "Access-Control-Allow-Origin": origin || "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS, GET",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  };
}

function json(data, status, origin) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders(origin),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "*";
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "FRISS AI Gateway" }, 200, origin);
    }

    if (request.method !== "POST" || url.pathname !== "/v1/chat/completions") {
      return json({ error: { message: "Endpoint tidak ditemukan." } }, 404, origin);
    }

    if (!env.ENRAI_API_KEY) {
      return json({ error: { message: "Backend belum dikonfigurasi." } }, 503, origin);
    }

    const contentLength = Number(request.headers.get("Content-Length") || 0);
    if (contentLength > MAX_BODY_BYTES) {
      return json({ error: { message: "Ukuran permintaan terlalu besar." } }, 413, origin);
    }

    let payload;
    try {
      const raw = await request.text();
      if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) {
        return json({ error: { message: "Ukuran permintaan terlalu besar." } }, 413, origin);
      }
      payload = JSON.parse(raw);
    } catch {
      return json({ error: { message: "Body harus berupa JSON yang valid." } }, 400, origin);
    }

    if (!payload || typeof payload !== "object" || !Array.isArray(payload.messages) || payload.messages.length === 0) {
      return json({ error: { message: "Field messages wajib berupa array dan tidak boleh kosong." } }, 400, origin);
    }

    // Do not accept caller-supplied URLs or headers. The upstream is fixed to prevent SSRF.
    const upstreamPayload = {
      ...payload,
      model: typeof payload.model === "string" && payload.model.trim()
        ? payload.model.trim()
        : (env.ENRAI_MODEL || "")
    };

    if (!upstreamPayload.model) {
      return json({ error: { message: "Model AI belum ditentukan." } }, 400, origin);
    }

    try {
      const upstream = await fetch(UPSTREAM_URL, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.ENRAI_API_KEY}`,
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify(upstreamPayload)
      });

      const responseBody = await upstream.text();
      return new Response(responseBody, {
        status: upstream.status,
        headers: {
          ...corsHeaders(origin),
          "Content-Type": upstream.headers.get("Content-Type") || "application/json; charset=utf-8",
          "Cache-Control": "no-store"
        }
      });
    } catch {
      // Intentionally do not expose provider credentials or internal exception details.
      return json({ error: { message: "Tidak dapat menghubungi penyedia AI. Coba lagi nanti." } }, 502, origin);
    }
  }
};
