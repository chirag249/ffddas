const fpsEl = document.getElementById('fpsVal');
const resEl = document.getElementById('resVal');
const filterEl = document.getElementById('filterVal');
const resolution = { w: 640, h: 480 };
const filterType = 'Canny Edge';
let simulatedFps = 0;
let lastSecond = performance.now();
let framesThisSecond = 0;
function tick() {
    framesThisSecond++;
    const now = performance.now();
    if (now - lastSecond >= 1000) {
        simulatedFps = framesThisSecond;
        framesThisSecond = 0;
        lastSecond = now;
        fpsEl.textContent = simulatedFps.toString();
    }
    requestAnimationFrame(tick);
}
function init() {
    resEl.textContent = `${resolution.w}x${resolution.h}`;
    filterEl.textContent = filterType;
    setInterval(() => {
        const dw = (Math.random() > 0.5 ? 640 : 800);
        const dh = (dw === 640 ? 480 : 600);
        resEl.textContent = `${dw}x${dh}`;
    }, 3000);
    const filters = ['Canny Edge', 'Grayscale', 'Threshold', 'Blur'];
    let fi = 0;
    setInterval(() => { fi = (fi + 1) % filters.length; filterEl.textContent = filters[fi]; }, 2000);
    requestAnimationFrame(tick);
}
document.addEventListener('DOMContentLoaded', init);
export {};
//# sourceMappingURL=main.js.map