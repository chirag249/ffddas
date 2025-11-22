"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
class FrameViewer {
    constructor() {
        this.currentImage = null;
        this.frameCount = 0;
        this.errorCount = 0;
        this.lastFrameTime = performance.now();
        this.fps = 0;
        this.intervalId = null;
        this.currentFilter = 'NONE';
        this.canvas = document.getElementById('canvas');
        this.ctx = this.canvas.getContext('2d');
        this.statusDot = document.getElementById('statusDot');
        this.statusText = document.getElementById('statusText');
        this.fpsEl = document.getElementById('fps');
        this.frameCountEl = document.getElementById('frameCount');
        this.errorCountEl = document.getElementById('errorCount');
        this.resolutionEl = document.getElementById('resolution');
        this.filterNameEl = document.getElementById('filterName');
        this.refreshRateSelect = document.getElementById('refreshRate');
        this.refreshBtn = document.getElementById('refreshBtn');
        this.filterButtons = document.getElementById('filterButtons');
        this.bindEvents();
        this.updateRefreshRate();
    }
    bindEvents() {
        this.refreshBtn.addEventListener('click', () => this.fetchFrame());
        this.refreshRateSelect.addEventListener('change', () => this.updateRefreshRate());
        this.filterButtons.querySelectorAll('button').forEach(btn => {
            btn.addEventListener('click', () => {
                this.filterButtons.querySelectorAll('button').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.setFilter(btn.getAttribute('data-filter') || 'NONE');
            });
        });
        window.addEventListener('resize', () => this.resizeCanvas());
    }
    setFilter(mode) {
        this.currentFilter = mode;
        fetch('/setFilter?mode=' + encodeURIComponent(mode)).then(() => {
            this.filterNameEl.textContent = this.humanFilter(mode);
        }).catch(() => { });
    }
    humanFilter(m) {
        switch (m) {
            case 'GRAYSCALE': return 'Grayscale';
            case 'EDGE_DETECTION': return 'Edge';
            default: return 'Normal';
        }
    }
    updateRefreshRate() {
        if (this.intervalId)
            window.clearInterval(this.intervalId);
        const rate = parseInt(this.refreshRateSelect.value, 10);
        this.intervalId = window.setInterval(() => this.fetchFrame(), rate);
        this.fetchFrame();
    }
    fetchFrame() {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                const controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), 5000);
                const res = yield fetch('/frame?' + Date.now(), { cache: 'no-store', signal: controller.signal });
                clearTimeout(timeout);
                if (!res.ok)
                    throw new Error('HTTP ' + res.status);
                const blob = yield res.blob();
                const img = yield this.loadImage(blob);
                this.currentImage = img;
                this.frameCount++;
                this.updateStatus(true);
                this.updateStats();
                this.resizeCanvas();
            }
            catch (e) {
                this.errorCount++;
                this.updateStatus(false);
                this.updateStats();
            }
        });
    }
    loadImage(blob) {
        return new Promise((resolve, reject) => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = reject;
            img.src = URL.createObjectURL(blob);
        });
    }
    resizeCanvas() {
        const container = this.canvas.parentElement;
        const maxW = container.clientWidth - 32;
        const maxH = container.clientHeight - 32;
        if (this.currentImage) {
            const ar = this.currentImage.width / this.currentImage.height;
            let w = maxW;
            let h = w / ar;
            if (h > maxH) {
                h = maxH;
                w = h * ar;
            }
            this.canvas.width = w;
            this.canvas.height = h;
            this.drawImage();
            this.resolutionEl.textContent = this.currentImage.width + 'x' + this.currentImage.height;
        }
    }
    drawImage() { if (!this.currentImage)
        return; this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height); this.ctx.drawImage(this.currentImage, 0, 0, this.canvas.width, this.canvas.height); }
    updateStatus(connected) {
        if (connected) {
            this.statusDot.className = 'dot connected';
            this.statusText.textContent = 'Connected';
        }
        else {
            this.statusDot.className = 'dot disconnected';
            this.statusText.textContent = 'Disconnected';
        }
    }
    updateStats() {
        const now = performance.now();
        const elapsed = (now - this.lastFrameTime) / 1000;
        if (elapsed > 0) {
            this.fps = Math.round(1 / elapsed);
            this.lastFrameTime = now;
        }
        this.fpsEl.textContent = String(this.fps);
        this.frameCountEl.textContent = String(this.frameCount);
        this.errorCountEl.textContent = String(this.errorCount);
    }
}
window.addEventListener('DOMContentLoaded', () => new FrameViewer());
//# sourceMappingURL=index.js.map