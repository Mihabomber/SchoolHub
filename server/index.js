// SchoolHub sync server — без внешних зависимостей, Node 18+.
// GET  /api/classes/:code/:collection?since=<ms>  -> { serverTime, items }
// POST /api/classes/:code/:collection  { items }  -> { accepted, serverTime }
// Слияние: Last-Writer-Wins по updatedAt. Данные хранятся в data.json.
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
const MAX_BODY = 60 * 1024 * 1024;

let db = { classes: {} };
try { db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch (_) {}

let saveTimer = null;
function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    fs.writeFile(DATA_FILE + '.tmp', JSON.stringify(db), (err) => {
      if (!err) fs.rename(DATA_FILE + '.tmp', DATA_FILE, () => {});
    });
  }, 500);
}

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/' || url.pathname === '/health') return send(res, 200, { ok: true, name: 'SchoolHub sync' });

  const m = url.pathname.match(/^\/api\/classes\/([a-z0-9_-]{1,32})\/(cheats|lessons|bells|homework)\/?$/);
  if (!m) return send(res, 404, { error: 'not found' });
  const code = m[1];
  const key = m[2] === 'cheats' ? code : `${code}:${m[2]}`; // шпоры — старый формат ключа
  const cls = (db.classes[key] ||= { items: {} });

  if (req.method === 'GET') {
    const since = Number(url.searchParams.get('since') || 0);
    const items = Object.values(cls.items).filter((x) => x.receivedAt > since).map(({ receivedAt, ...rest }) => rest);
    return send(res, 200, { serverTime: Date.now(), items });
  }

  if (req.method === 'POST') {
    let size = 0; const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) { send(res, 413, { error: 'too large' }); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => {
      let payload;
      try { payload = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch (_) { return send(res, 400, { error: 'bad json' }); }
      const now = Date.now();
      let accepted = 0;
      for (const it of payload.items || []) {
        if (!it || typeof it.uuid !== 'string' || typeof it.updatedAt !== 'number') continue;
        const cur = cls.items[it.uuid];
        if (!cur || it.updatedAt > cur.updatedAt) {
          cls.items[it.uuid] = { ...it, receivedAt: now };
          accepted++;
        }
      }
      if (accepted) scheduleSave();
      send(res, 200, { accepted, serverTime: now });
    });
    return;
  }
  send(res, 405, { error: 'method not allowed' });
});

server.listen(PORT, () => console.log(`SchoolHub sync server on :${PORT}`));
