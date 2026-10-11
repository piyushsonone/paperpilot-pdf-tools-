(() => {
 const $=id=>document.getElementById(id); const input=$('fileInput'), drop=$('dropzone'), fileList=$('fileList'), preview=$('previewArea'), options=$('options'), run=$('runBtn'), status=$('status');
 const tools={
  compress:{title:'Compress PDF',desc:'Choose compression quality and optionally set a target file size.',button:'Compress PDF',multi:false,options:'compress'},
  merge:{title:'Merge PDF',desc:'Combine multiple PDF files into one document. Files are merged in the order shown.',button:'Merge PDF',multi:true,options:'merge'},
  split:{title:'Split PDF',desc:'Split your PDF into one PDF per page and download the pages as a ZIP.',button:'Split PDF',options:'none'},
  extract:{title:'Extract pages',desc:'Save only the pages you need into a new PDF.',button:'Extract pages',options:'extract'},
  rotate:{title:'Rotate PDF',desc:'Rotate all pages to the correct orientation.',button:'Rotate PDF',options:'rotate'},
  organize:{title:'Organize PDF',desc:'Remove pages or provide a new page order, for example 3,1,2.',button:'Organize PDF',options:'organize'},
  watermark:{title:'Watermark PDF',desc:'Add a text watermark across each page.',button:'Add watermark',options:'watermark'},
  numbers:{title:'Page numbers',desc:'Add page numbers to your PDF.',button:'Add page numbers',options:'numbers'},
  protect:{title:'Protect PDF',desc:'Encrypt your PDF with a password.',button:'Protect PDF',options:'protect'},
  edit:{title:'Remove pages',desc:'Delete page numbers or ranges, for example 2,4-6.',button:'Remove pages',options:'edit'}
 };
 let tool='compress', files=[], pdfDoc=null, page=1, previewUrl=null;
 if(window.pdfjsLib) pdfjsLib.GlobalWorkerOptions.workerSrc='https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
 const fmt=n=>n<1024*1024?(n/1024).toFixed(0)+' KB':(n/1024/1024).toFixed(2)+' MB';
 function showStatus(msg,error=false){status.textContent=msg;status.classList.remove('hidden','error');if(error)status.classList.add('error')}
 function hideStatus(){status.classList.add('hidden')}
 function setTool(name){tool=name;document.querySelectorAll('.tool-card').forEach(b=>b.classList.toggle('active',b.dataset.tool===name));const t=tools[name];$('toolTitle').textContent=t.title;$('toolDesc').textContent=t.desc;run.innerHTML=t.button+' <span>→</span>';input.multiple=!!t.multi;renderOptions();renderFiles();updateRun();hideStatus()}
 function renderOptions(){
  const type=tools[tool].options;
  if(type==='compress') options.innerHTML=`<div class="option-label">Compression level</div><div class="quality-options"><label class="quality-card"><input type="radio" name="level" value="light"><span><b>Light</b><small>Better quality</small></span></label><label class="quality-card selected"><input type="radio" name="level" value="recommended" checked><span><b>Recommended</b><small>Balanced</small></span></label><label class="quality-card"><input type="radio" name="level" value="strong"><span><b>Extreme</b><small>Smaller file</small></span></label></div><label class="field-label" for="targetMb">Target size <span>optional</span></label><div class="input-suffix"><input id="targetMb" type="number" min="0.1" max="30" step="0.1" placeholder="e.g. 2.0"><span>MB</span></div><p class="hint">Very strong compression may turn pages into images, reducing text selectability. Target size is an estimate, not a guarantee.</p>`;
  else if(type==='extract') options.innerHTML=field('Pages to extract','pages','e.g. 1,3,5-7','Comma-separated page numbers and ranges.');
  else if(type==='rotate') options.innerHTML=`<div class="field"><label for="degrees">Rotate all pages</label><select id="degrees"><option value="90">90° clockwise</option><option value="180">180°</option><option value="270">270° clockwise</option></select></div>`;
  else if(type==='organize') options.innerHTML=field('Pages to remove (optional)','deletePages','e.g. 2,4-6','Leave blank to keep all pages.')+field('New page order (optional)','pageOrder','e.g. 3,1,2','Use each remaining page number once. Blank keeps the existing order.');
  else if(type==='edit') options.innerHTML=field('Pages to remove','deletePages','e.g. 2,4-6','Use page numbers starting at 1.');
  else if(type==='watermark') options.innerHTML=field('Watermark text','watermarkText','CONFIDENTIAL','ASCII text is supported.')+`<div class="field"><label for="fontSize">Text size</label><select id="fontSize"><option value="24">Small</option><option value="45" selected>Medium</option><option value="72">Large</option></select></div>`;
  else if(type==='numbers') options.innerHTML=`<div class="field"><label for="position">Number position</label><select id="position"><option value="bottom-center">Bottom center</option><option value="bottom-left">Bottom left</option><option value="bottom-right">Bottom right</option></select></div>`;
  else if(type==='protect') options.innerHTML=field('Password','password','Enter a password','Use at least 4 characters.');
  else options.innerHTML='<p class="hint">Choose two or more PDF files to merge them.</p>';
 }
 function field(label,id,placeholder,hint){return `<div class="field"><label for="${id}">${label}</label><input id="${id}" type="text" placeholder="${placeholder}"><div class="subhint">${hint}</div></div>`}
 document.querySelectorAll('.tool-card').forEach(b=>b.addEventListener('click',()=>setTool(b.dataset.tool)));
 $('browseBtn').addEventListener('click',()=>input.click());
 input.addEventListener('change',e=>addFiles([...e.target.files]));
 ['dragenter','dragover'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.add('drag')}));
 ['dragleave','drop'].forEach(ev=>drop.addEventListener(ev,e=>{e.preventDefault();drop.classList.remove('drag')}));
 drop.addEventListener('drop',e=>addFiles([...e.dataTransfer.files]));
 function addFiles(arr){const pdfs=arr.filter(f=>f.type==='application/pdf'||f.name.toLowerCase().endsWith('.pdf'));if(!pdfs.length){showStatus('Choose a PDF file to continue.',true);return}if(!tools[tool].multi) files=[];files.push(...pdfs);files=files.slice(0,tools[tool].multi?20:1);if(pdfs.length!==arr.length)showStatus('Only PDF files are accepted; non-PDF files were skipped.',true);else hideStatus();renderFiles();updateRun();if(files.length)loadPreview(files[0]);}
 function renderFiles(){fileList.innerHTML='';fileList.classList.toggle('hidden',!files.length);files.forEach((f,i)=>{const row=document.createElement('div');row.className='file-row';row.innerHTML=`<div class="file-symbol">PDF</div><div class="file-info"><strong title="${escapeHtml(f.name)}">${escapeHtml(f.name)}</strong><small>${fmt(f.size)}</small></div><button class="remove-file" title="Remove file">×</button>`;row.querySelector('button').onclick=()=>{files.splice(i,1);renderFiles();updateRun();if(files[0])loadPreview(files[0]);else{preview.classList.add('hidden');pdfDoc=null}};fileList.appendChild(row)});}
 function escapeHtml(s){return s.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
 async function loadPreview(file){if(!window.pdfjsLib){preview.classList.add('hidden');return}try{if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=URL.createObjectURL(file);pdfDoc=await pdfjsLib.getDocument(previewUrl).promise;page=1;$('previewMeta').textContent=`${pdfDoc.numPages} page${pdfDoc.numPages===1?'':'s'} · local preview`;$('pageCounter').textContent=`1 / ${pdfDoc.numPages}`;preview.classList.remove('hidden');await renderPage();await renderThumbs()}catch(e){preview.classList.add('hidden');showStatus('This PDF could not be previewed. It may be password-protected or damaged.',true)}}
 async function renderPage(){if(!pdfDoc)return;const p=await pdfDoc.getPage(page);const wrap=document.querySelector('.pdf-canvas-wrap');const v0=p.getViewport({scale:1});const scale=Math.min(1.7,(wrap.clientWidth-28)/v0.width);const viewport=p.getViewport({scale});const canvas=$('pdfCanvas');const ctx=canvas.getContext('2d');canvas.width=viewport.width;canvas.height=viewport.height;await p.render({canvasContext:ctx,viewport}).promise;$('pageCounter').textContent=`${page} / ${pdfDoc.numPages}`;$('prevPage').disabled=page<=1;$('nextPage').disabled=page>=pdfDoc.numPages;document.querySelectorAll('.thumb').forEach((x,i)=>x.classList.toggle('active',i===page-1))}
 async function renderThumbs(){const box=$('thumbnails');box.innerHTML='';if(!pdfDoc)return;const count=Math.min(pdfDoc.numPages,40);for(let i=1;i<=count;i++){const p=await pdfDoc.getPage(i);const v=p.getViewport({scale:.11});const c=document.createElement('canvas');c.width=v.width;c.height=v.height;await p.render({canvasContext:c.getContext('2d'),viewport:v}).promise;const b=document.createElement('button');b.className='thumb'+(i===page?' active':'');b.append(c);const s=document.createElement('span');s.textContent=i;b.append(s);b.onclick=()=>{page=i;renderPage()};box.append(b)}}
 $('prevPage').onclick=()=>{if(page>1){page--;renderPage()}};$('nextPage').onclick=()=>{if(pdfDoc&&page<pdfDoc.numPages){page++;renderPage()}};
 function updateRun(){run.disabled=files.length===0||(tool==='merge'&&files.length<2)}
 run.addEventListener('click',async()=>{if(!files.length)return;hideStatus();run.disabled=true;run.textContent='Processing…';try{const fd=new FormData();let endpoint='';let filename='result.pdf';
  if(tool==='merge'){files.forEach(f=>fd.append('files',f));endpoint='/merge';filename='merged.pdf'}
  else {fd.append('file',files[0]);
   if(tool==='compress'){fd.append('level',document.querySelector('input[name="level"]:checked')?.value||'recommended');const t=$('targetMb')?.value;if(t){if(Number(t)<=0)throw Error('Target size must be greater than zero.');fd.append('targetMb',t)}endpoint='/compress';filename='compressed.pdf'}
   if(tool==='split'){endpoint='/split';filename='split-pages.zip'}
   if(tool==='extract'){const p=$('pages').value.trim();if(!p)throw Error('Enter pages to extract.');fd.append('pages',p);endpoint='/extract';filename='extracted-pages.pdf'}
   if(tool==='rotate'){fd.append('rotateDegrees',$('degrees').value);endpoint='/edit';filename='rotated.pdf'}
   if(tool==='edit'||tool==='organize'){fd.append('deletePages',$('deletePages')?.value||'');if(tool==='organize')fd.append('pageOrder',$('pageOrder')?.value||'');endpoint='/edit';filename=tool==='edit'?'pages-removed.pdf':'organized.pdf'}
   if(tool==='watermark'){const t=$('watermarkText').value.trim();if(!t)throw Error('Enter watermark text.');fd.append('text',t);fd.append('fontSize',$('fontSize').value);endpoint='/watermark';filename='watermarked.pdf'}
   if(tool==='numbers'){fd.append('position',$('position').value);endpoint='/page-numbers';filename='numbered.pdf'}
   if(tool==='protect'){const p=$('password').value;if(p.length<4)throw Error('Password must contain at least 4 characters.');fd.append('password',p);endpoint='/protect';filename='protected.pdf'}
  }
  showStatus('Processing your PDF…');const response=await fetch('/api/pdf'+endpoint,{method:'POST',body:fd});if(!response.ok){let msg='Could not process this file.';try{const j=await response.json();msg=j.error||msg}catch{}throw Error(msg)}const blob=await response.blob();const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=filename;a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);showStatus(`Done! Download started (${fmt(blob.size)}).`)
 }catch(e){showStatus(e.message||'Something went wrong.',true)}finally{run.disabled=false;run.innerHTML=tools[tool].button+' <span>→</span>';updateRun()}});
 document.addEventListener('change',e=>{if(e.target.name==='level')document.querySelectorAll('.quality-card').forEach(c=>c.classList.toggle('selected',c.querySelector('input').checked))});
 window.addEventListener('resize',()=>{if(pdfDoc)renderPage()});setTool('compress');
})();
