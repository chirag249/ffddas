class Viewer {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private frames = 0;
  private last = performance.now();
  private fpsEl: HTMLElement;
  private statusEl: HTMLElement;
  constructor(){
    this.canvas = document.getElementById('canvas') as HTMLCanvasElement;
    this.ctx = this.canvas.getContext('2d')!;
    this.fpsEl = document.getElementById('fps')!;
    this.statusEl = document.getElementById('status')!;
    setInterval(()=>this.fetchFrame(), 250);
  }
  async fetchFrame(){
    try{
      const res = await fetch('/frame?' + Date.now(), { cache: 'no-store' });
      const blob = await res.blob();
      const img = new Image();
      img.onload = () => {
        this.canvas.width = img.width; this.canvas.height = img.height;
        this.ctx.drawImage(img, 0, 0, this.canvas.width, this.canvas.height);
        this.frames++; const now = performance.now();
        if (now - this.last >= 1000){ this.fpsEl.textContent = 'FPS: ' + this.frames; this.frames = 0; this.last = now; }
        this.statusEl.textContent = 'Connected';
      };
      img.src = URL.createObjectURL(blob);
    } catch(e){ this.statusEl.textContent = 'Error'; }
  }
}
export default Viewer;
