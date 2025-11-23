export {};
const imgEl = document.getElementById('frame') as HTMLImageElement;
const fpsEl = document.getElementById('fps') as HTMLDivElement;

let framesThisSecond = 0;
let lastSecondMark = performance.now();
let socket: WebSocket | null = null;
let fallbackUsed = false;

function updateFps(){
  const now = performance.now();
  framesThisSecond++;
  if (now - lastSecondMark >= 1000){
    fpsEl.textContent = framesThisSecond + ' FPS';
    framesThisSecond = 0;
    lastSecondMark = now;
  }
}

function setImageFromBase64(b64: string){
  imgEl.src = 'data:image/png;base64,' + b64.trim();
  updateFps();
}

function loadFallback(){
  if (fallbackUsed) return;
  fallbackUsed = true;
  imgEl.src = './sample.png';
  fpsEl.textContent = '0 FPS (static sample)';
}

function startWebSocket(){
  try {
    socket = new WebSocket('ws://localhost:8081');
    socket.onmessage = (ev) => {
      const data = typeof ev.data === 'string' ? ev.data : '';
      if (data.startsWith('iVBOR') || data.startsWith('PNG') || data.length > 20){
        setImageFromBase64(data);
      }
    };
    socket.onerror = loadFallback;
    socket.onclose = loadFallback;
    setTimeout(()=>{ if (!fallbackUsed && framesThisSecond === 0) loadFallback(); }, 2000);
  } catch { loadFallback(); }
}

startWebSocket();
