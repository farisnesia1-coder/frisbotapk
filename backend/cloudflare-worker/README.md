# FRISS AI Gateway

Backend proxy untuk memusatkan permintaan AI FRISS ASSIST. API key Enrai disimpan sebagai Cloudflare Worker Secret dan tidak dimasukkan ke APK atau repository.

## Endpoint

- `GET /health` — pemeriksaan status backend.
- `POST /v1/chat/completions` — meneruskan payload OpenAI-compatible ke Enrai.

## Deploy

1. Buat akun Cloudflare dan buka **Workers & Pages**.
2. Buat Worker bernama `friss-ai-gateway`.
3. Deploy isi `src/index.js` sebagai Worker (atau deploy folder ini dengan Wrangler).
4. Atur secret `ENRAI_API_KEY` di Worker Settings → Variables and Secrets. Gunakan API key Enrai baru yang sudah dirotasi; jangan gunakan key yang pernah terpapar di chat.
5. Deploy ulang Worker.
6. Uji `GET https://NAMA-WORKER.SUBDOMAIN.workers.dev/health`.
7. Catat URL Worker. URL ini nanti dipasang sebagai alamat backend di FRISS ASSIST, bukan API key.

Dengan Wrangler CLI:

```bash
cd backend/cloudflare-worker
npm install --global wrangler
wrangler login
wrangler secret put ENRAI_API_KEY
wrangler deploy
```

## Contoh request

```http
POST /v1/chat/completions
Content-Type: application/json

{
  "model": "MODEL_YANG_DIDUKUNG_ENRAI",
  "messages": [
    { "role": "user", "content": "Halo!" }
  ]
}
```

## Catatan keamanan dan kompatibilitas

- Jangan pernah menaruh API key di APK, source Android, file konfigurasi publik, atau GitHub Actions variable yang kemudian disisipkan ke APK.
- Worker ini meneruskan format OpenAI-compatible. Dukungan gambar/Vision tetap bergantung pada model dan format multimodal yang benar-benar didukung Enrai; keberadaan proxy saja tidak membuat model menjadi Vision.
- URL upstream dikunci di kode agar request tidak bisa diarahkan ke host sembarang.
- Batasi penyalahgunaan melalui Cloudflare dashboard (rate limiting/WAF) sebelum URL backend dipakai publik. CORS bukan mekanisme autentikasi dan aplikasi Android tidak dibatasi oleh CORS.
- Jangan mencatat request body, prompt, atau header Authorization di log.
