(() => {
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
let latest = null, latestConfig = null, fileCount = 0, findingCount = 0;
const pages = ['dashboard','scanner','reports','settings','about'];
function navigate(page) {
  pages.forEach(p => { const el = $('#' + p + '-page'); if (el) el.hidden = p !== page; });
  $$('.nav').forEach(b => b.classList.toggle('active', b.dataset.page === page));
  $('#page-title').textContent = ({dashboard:'Security overview',scanner:'Project scanner',reports:'Reports & fixes',settings:'Settings',about:'About & support'})[page] || 'SafeBuild';
  window.scrollTo({top:0,behavior:'smooth'});
}
$$('[data-page]').forEach(b => b.addEventListener('click', () => navigate(b.dataset.page)));
$$('[data-goto]').forEach(b => b.addEventListener('click', () => navigate(b.dataset.goto)));
function toggleTheme() {
  document.body.classList.toggle('light');
  const light = document.body.classList.contains('light');
  localStorage.setItem('safebuild-theme', light ? 'light' : 'dark');
  $('#theme-toggle').textContent = light ? '☾' : '☼';
}
if (localStorage.getItem('safebuild-theme') === 'light') { document.body.classList.add('light'); $('#theme-toggle').textContent = '☾'; }
$('#theme-toggle').addEventListener('click', toggleTheme);
$('#theme-setting').addEventListener('click', toggleTheme);
$$('.tab').forEach(b => b.addEventListener('click', () => {
  $$('.tab').forEach(x => x.classList.toggle('active', x === b));
  $('#file-mode').hidden = b.dataset.mode !== 'file';
  $('#url-mode').hidden = b.dataset.mode !== 'url';
  $('#scan-status').hidden = true;
}));
function status(message, error=false) { const el=$('#scan-status'); el.hidden=false; el.textContent=message; el.classList.toggle('error',error); }
function renderFindings(findings) {
  $('#results').hidden=false;
  $('#results-summary').textContent = findings.length ? findings.length + ' finding(s) require review. No result guarantees the project is secure.' : 'No matching heuristic findings. This does not prove the project is secure.';
  $('#findings-list').replaceChildren();
  for (const f of findings) {
    const article=document.createElement('article'); article.className='finding';
    const sev=document.createElement('span'); sev.className='severity '+f.severity; sev.textContent=f.severity;
    const body=document.createElement('div');
    const title=document.createElement('strong'); title.textContent=f.title;
    const desc=document.createElement('p'); desc.textContent=(f.file ? f.file+' — ' : '')+f.description;
    const fix=document.createElement('small'); fix.textContent='Suggested next step: '+f.fix;
    body.append(title,desc,fix); article.append(sev,body); $('#findings-list').append(article);
  }
  findingCount += findings.length; $('#findings-count').textContent=String(findingCount);
}
$('#file-form').addEventListener('submit', async e => {
  e.preventDefault(); const file=$('#project-file').files[0]; if(!file) return;
  const form=new FormData(); form.append('project',file);
  status('Scanning source patterns locally…'); $('#results').hidden=true;
  try {
    const r=await fetch('/api/scan-file',{method:'POST',body:form}); const data=await r.json();
    if(!r.ok) throw new Error(data.error || 'Scan failed');
    latest={type:'file',...data,platform:$('#hosting').value,tier:$('#tier').value,database:$('#database').value,filename:file.name};
    fileCount=data.fileCount; $('#files-count').textContent=String(fileCount);
    status('Scan complete. '+fileCount+' source file(s) inspected. '+data.note);
    renderFindings(data.findings || []);
  } catch(err) { status(err.message || 'Could not scan project.',true); }
});
$('#url-form').addEventListener('submit', async e => {
  e.preventDefault(); status('Requesting a single response from the URL…'); $('#results').hidden=true;
  try {
    const r=await fetch('/api/scan-url',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:$('#url-input').value})});
    const data=await r.json(); if(!r.ok) throw new Error(data.error || 'URL audit failed');
    latest={type:'url',...data}; status('URL check complete. '+data.note); renderFindings(data.findings || []);
  } catch(err) { status(err.message || 'URL audit failed.',true); }
});
$('#make-config').addEventListener('click', async () => {
  if(!latest) { status('Run a scan first to associate this configuration with a report.',true); return; }
  try {
    const r=await fetch('/api/export-config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:latest.platform || $('#hosting').value})});
    latestConfig=await r.json(); if(!r.ok) throw new Error(latestConfig.error);
    const pre=$('#config-output'); pre.hidden=false; pre.textContent=latestConfig.content+'\n\n# '+latestConfig.warning;
    $('#download-config').hidden=false; status('Configuration generated. Review it before deployment.');
  } catch(err) { status(err.message || 'Could not generate configuration.',true); }
});
$('#download-config').addEventListener('click', () => {
  if(!latestConfig) return;
  download(latestConfig.filename,latestConfig.content);
});
$('#export-report').addEventListener('click', () => {
  if(!latest) return;
  const report={product:'SafeBuild v1.0',creator:'RC Empire',generatedAt:new Date().toISOString(),scope:latest.type==='url'?latest.url:latest.filename,summary:'Heuristic findings only; review manually.',findings:latest.findings || [],configuration:latestConfig || null};
  download('SafeBuild-security-report.json',JSON.stringify(report,null,2));
});
function download(name,content) { const a=document.createElement('a'); const url=URL.createObjectURL(new Blob([content],{type:'application/octet-stream'})); a.href=url; a.download=name; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000); }
setTimeout(()=>{ $('#splash').classList.add('done'); setTimeout(()=>{ $('#splash').hidden=true; $('#app').hidden=false; },450); },1300);
if ('serviceWorker' in navigator) window.addEventListener('load',()=>navigator.serviceWorker.register('/sw.js').catch(()=>{}));
})();