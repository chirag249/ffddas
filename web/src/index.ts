class FrameViewer {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private statusDot: HTMLElement;
  private statusText: HTMLElement;
  private fpsEl: HTMLElement; private frameCountEl: HTMLElement; private errorCountEl: HTMLElement; private resolutionEl: HTMLElement; private filterNameEl: HTMLElement;
  private refreshRateSelect: HTMLSelectElement; private refreshBtn: HTMLButtonElement; private filterButtons: HTMLElement;
  private currentImage: HTMLImageElement | null = null;
  private frameCount = 0; private errorCount = 0; private lastFrameTime = performance.now(); private fps = 0;
  private intervalId: number | null = null; private currentFilter = 'NONE';

  constructor(){
    this.canvas = document.getElementById('canvas') as HTMLCanvasElement;
    this.ctx = this.canvas.getContext('2d')!;
    this.statusDot = document.getElementById('statusDot')!;
    this.statusText = document.getElementById('statusText')!;
    this.fpsEl = document.getElementById('fps')!; this.frameCountEl = document.getElementById('frameCount')!; this.errorCountEl = document.getElementById('errorCount')!; this.resolutionEl = document.getElementById('resolution')!; this.filterNameEl = document.getElementById('filterName')!;
    this.refreshRateSelect = document.getElementById('refreshRate') as HTMLSelectElement;
    this.refreshBtn = document.getElementById('refreshBtn') as HTMLButtonElement;
    this.filterButtons = document.getElementById('filterButtons')!;

    this.bindEvents();
    this.updateRefreshRate();
  }

  private bindEvents(){
    this.refreshBtn.addEventListener('click', ()=>this.fetchFrame());
    this.refreshRateSelect.addEventListener('change', ()=>this.updateRefreshRate());
    this.filterButtons.querySelectorAll('button').forEach(btn => {
      btn.addEventListener('click', ()=>{
        this.filterButtons.querySelectorAll('button').forEach(b=>b.classList.remove('active'));
        btn.classList.add('active');
        this.setFilter(btn.getAttribute('data-filter') || 'NONE');
      });
    });
    window.addEventListener('resize', ()=>this.resizeCanvas());
  }

  private setFilter(mode: string){
    this.currentFilter = mode;
    fetch('/setFilter?mode=' + encodeURIComponent(mode)).then(()=>{
      this.filterNameEl.textContent = this.humanFilter(mode);
    }).catch(()=>{});
  }

  private humanFilter(m: string){
    switch(m){case 'GRAYSCALE': return 'Grayscale'; case 'EDGE_DETECTION': return 'Edge'; default: return 'Normal';}
  }

  private updateRefreshRate(){
    if (this.intervalId) window.clearInterval(this.intervalId);
    const rate = parseInt(this.refreshRateSelect.value, 10);
    this.intervalId = window.setInterval(()=>this.fetchFrame(), rate);
    this.fetchFrame();
  }

  private async fetchFrame(){
    try {
      const controller = new AbortController();
      const timeout = setTimeout(()=>controller.abort(), 5000);
      const res = await fetch('/frame?' + Date.now(), { cache: 'no-store', signal: controller.signal });
      clearTimeout(timeout);
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const blob = await res.blob();
      const img = await this.loadImage(blob);
      this.currentImage = img; this.frameCount++; this.updateStatus(true); this.updateStats(); this.resizeCanvas();
    } catch(e){
      this.errorCount++; this.updateStatus(false); this.updateStats();
    }
  }

  private loadImage(blob: Blob){
    return new Promise<HTMLImageElement>((resolve, reject)=>{
      const img = new Image();
      img.onload = ()=>resolve(img); img.onerror = reject; img.src = URL.createObjectURL(blob);
    });
  }

  private resizeCanvas(){
    const container = this.canvas.parentElement!;
    const maxW = container.clientWidth - 32; const maxH = container.clientHeight - 32;
    if (this.currentImage){
      const ar = this.currentImage.width / this.currentImage.height;
      let w = maxW; let h = w / ar; if (h > maxH){ h = maxH; w = h * ar; }
      this.canvas.width = w; this.canvas.height = h; this.drawImage();
      this.resolutionEl.textContent = this.currentImage.width + 'x' + this.currentImage.height;
    }
  }

  private drawImage(){ if(!this.currentImage) return; this.ctx.clearRect(0,0,this.canvas.width,this.canvas.height); this.ctx.drawImage(this.currentImage,0,0,this.canvas.width,this.canvas.height); }

  private updateStatus(connected: boolean){
    if (connected){ this.statusDot.className='dot connected'; this.statusText.textContent='Connected'; }
    else { this.statusDot.className='dot disconnected'; this.statusText.textContent='Disconnected'; }
  }

  private updateStats(){
    const now = performance.now(); const elapsed = (now - this.lastFrameTime)/1000; if (elapsed>0){ this.fps = Math.round(1/elapsed); this.lastFrameTime = now; }
    this.fpsEl.textContent = String(this.fps); this.frameCountEl.textContent = String(this.frameCount); this.errorCountEl.textContent = String(this.errorCount);
  }
}

window.addEventListener('DOMContentLoaded', ()=> new FrameViewer());
