"use strict";
const imgEl = document.getElementById('frame');
const fpsEl = document.getElementById('fps');
let lastFrameTime = performance.now();
let framesThisSecond = 0;
let lastSecondMark = performance.now();
let socket = null;
let fallbackUsed = false;
function updateFps() {
    const now = performance.now();
    framesThisSecond++;
    if (now - lastSecondMark >= 1000) {
        fpsEl.textContent = framesThisSecond + ' FPS';
        framesThisSecond = 0;
        lastSecondMark = now;
    }
    lastFrameTime = now;
}
function setImageFromBase64(b64) {
    imgEl.src = 'data:image/png;base64,' + b64.trim();
    updateFps();
}
function loadFallback() {
    if (fallbackUsed)
        return;
    fallbackUsed = true;
    imgEl.src = './sample.png';
    fpsEl.textContent = '0 FPS (static sample)';
}
function startWebSocket() {
    try {
        socket = new WebSocket('ws://localhost:8081');
        socket.onopen = () => { console.log('[viewer] WS connected'); };
        socket.onmessage = (ev) => {
            const data = typeof ev.data === 'string' ? ev.data : '';
            if (data.startsWith('iVBOR') || data.startsWith('PNG') || data.length > 20) { // heuristic base64 png
                setImageFromBase64(data);
            }
        };
        socket.onerror = () => { console.warn('[viewer] WS error, using fallback'); loadFallback(); };
        socket.onclose = () => { console.warn('[viewer] WS closed, using fallback'); loadFallback(); };
        // Fallback timeout if no frame in 2s
        setTimeout(() => { if (!fallbackUsed && framesThisSecond === 0)
            loadFallback(); }, 2000);
    }
    catch (e) {
        console.error('[viewer] WS failed:', e);
        loadFallback();
    }
}
startWebSocket();
//# sourceMappingURL=viewer.js.map