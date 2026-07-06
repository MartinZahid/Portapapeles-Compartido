const express = require('express');
const clipboardy = require('clipboardy').default;
const qrcode = require('qrcode-terminal');
const os = require('os');
const path = require('path');

const app = express();
app.use(express.static(path.join(__dirname, 'public')));
app.use(express.text({ type: '*/*' }));

function getLocalIP() {
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) return net.address;
    }
  }
  return '127.0.0.1';
}

const PORT = 3737;
const ip = getLocalIP();
const url = `http://${ip}:${PORT}`;

console.log('Escaneá este QR desde tu celular (misma red WiFi):');
qrcode.generate(url, { small: true });
console.log(`\nO abrí: ${url}`);

app.get('/clipboard', async (req, res) => {
  try {
    const text = await clipboardy.read();
    res.send(text || '');
  } catch {
    res.send('');
  }
});

app.post('/clipboard', async (req, res) => {
  try {
    await clipboardy.write(req.body || '');
    res.send('ok');
  } catch {
    res.status(500).send('error');
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Servidor corriendo en ${url}`);
});
