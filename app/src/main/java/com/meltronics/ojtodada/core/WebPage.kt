package com.meltronics.ojtodada.core

/** The page computers / iPhones see. Fully self-contained (works with no internet). */
object WebPage {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun denied() = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<body style="font-family:system-ui;padding:40px;text-align:center;background:#fff8f0">
<h2 style="color:#c8102e">OJ-to-Dada</h2><p>This link has expired. Scan the QR code on the phone again.</p></body>"""

    fun html(device: String, token: String): String = TEMPLATE
        .replace("__NAME__", esc(device))
        .replace("__TOKEN__", token)

    private val TEMPLATE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>OJ-to-Dada · __NAME__</title>
<style>
:root{--red:#c8102e;--gold:#ffcd00;--ink:#1d1b1a;--muted:#6b6560;--bg:#fff8f0;--card:#fff;--line:#eadfd3}
@media (prefers-color-scheme:dark){:root{--ink:#f4efe9;--muted:#b3aaa1;--bg:#171412;--card:#221e1b;--line:#3a332d}}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--ink)}
header{background:linear-gradient(135deg,#c8102e,#7a0019);color:#fff;padding:22px 16px 28px}
header h1{margin:0;font-size:26px;letter-spacing:.5px}header h1 b{color:var(--gold)}
header p{margin:6px 0 0;opacity:.9}
main{max-width:860px;margin:-14px auto 40px;padding:0 16px;display:grid;gap:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:18px}
h2{margin:0 0 12px;font-size:18px;display:flex;align-items:center;gap:8px}
.row{display:flex;align-items:center;gap:12px;padding:10px 0;border-top:1px solid var(--line)}
.row:first-child{border-top:0}.ico{width:40px;height:40px;border-radius:10px;background:var(--gold);color:#1d1b1a;display:grid;place-items:center;font-weight:700;font-size:11px;flex:none}
.nm{flex:1;min-width:0}.nm div{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:600}.nm small{color:var(--muted)}
a.btn,button{appearance:none;border:0;border-radius:12px;padding:10px 16px;font-weight:700;font-size:14px;cursor:pointer;text-decoration:none;display:inline-block}
.pri{background:var(--red);color:#fff}.sec{background:var(--gold);color:#1d1b1a}
.drop{border:2px dashed var(--red);border-radius:16px;padding:28px 12px;text-align:center;cursor:pointer}
.drop.over{background:rgba(200,16,46,.08)}.drop p{margin:6px 0;color:var(--muted)}
.bar{height:8px;border-radius:8px;background:var(--line);overflow:hidden;margin-top:6px}.bar i{display:block;height:100%;width:0;background:var(--red);transition:width .2s}
.empty{color:var(--muted);padding:6px 0}.ok{color:#1b8a3a;font-weight:700}.bad{color:var(--red);font-weight:700}
footer{text-align:center;color:var(--muted);font-size:13px;padding-bottom:30px}
</style></head><body>
<header><h1>OJ-to-<b>Dada</b></h1><p>Connected to <b>__NAME__</b> · no internet used</p></header>
<main>
 <section class="card">
  <h2>⬇ Get files from the phone <span style="flex:1"></span><a id="zip" class="btn sec" href="#">Download all (.zip)</a></h2>
  <div id="files"><div class="empty">Loading…</div></div>
 </section>
 <section class="card">
  <h2>⬆ Send files to the phone</h2>
  <label class="drop" id="drop"><input id="pick" type="file" multiple hidden>
   <div style="font-size:34px">📤</div><b>Tap to choose files</b><p>or drag & drop here · any type, any size (.apk .exe .zip videos…)</p></label>
  <div id="ups"></div>
 </section>
 <section class="card"><h2>📥 Already sent to the phone</h2><div id="got"><div class="empty">Nothing yet</div></div></section>
</main>
<footer>Keep this page open until uploads finish · Files are saved on the phone in Downloads/OJ-to-Dada</footer>
<script>
var T='__TOKEN__';
function q(s){return document.querySelector(s)}
function el(tag,cls,txt){var e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e}
function ext(n){var i=n.lastIndexOf('.');return i>0?n.slice(i+1,i+5).toUpperCase():'FILE'}
function hs(b){var u=['B','KB','MB','GB','TB'],i=0;while(b>=1024&&i<4){b/=1024;i++}return (i?b.toFixed(1):b)+' '+u[i]}
function row(name,sub,right){var r=el('div','row'),ic=el('div','ico',ext(name)),nm=el('div','nm');nm.appendChild(el('div','',name));nm.appendChild(el('small','',sub));r.appendChild(ic);r.appendChild(nm);if(right)r.appendChild(right);return r}
q('#zip').href='/zip?t='+T;
function load(){fetch('/api/list?t='+T).then(function(r){return r.json()}).then(function(d){
 var f=q('#files');f.innerHTML='';
 if(!d.files.length){f.appendChild(el('div','empty','The phone is not sharing any files yet. On the phone tap “Add files”.'));q('#zip').style.display='none'}
 else{q('#zip').style.display='';d.files.forEach(function(x){var a=el('a','btn pri','Download');a.href='/d/'+x.i+'/'+encodeURIComponent(x.name)+'?t='+T;a.setAttribute('download',x.name);f.appendChild(row(x.name,x.hsize,a))})}
 var g=q('#got');g.innerHTML='';if(!d.received.length)g.appendChild(el('div','empty','Nothing yet'));
 d.received.forEach(function(x){g.appendChild(row(x.name,x.hsize,el('span','ok','✓')))})
}).catch(function(){})}
load();setInterval(load,4000);
var queue=[],busy=false;
function add(files){for(var i=0;i<files.length;i++)queue.push(files[i]);next()}
function next(){if(busy||!queue.length)return;busy=true;var f=queue.shift();
 var bar=el('div','bar'),fill=el('i');bar.appendChild(fill);var st=el('small','','0%');var box=el('div');box.appendChild(st);box.appendChild(bar);
 var r=row(f.name,hs(f.size),null);r.querySelector('.nm').appendChild(box);q('#ups').prepend(r);
 var x=new XMLHttpRequest();x.open('PUT','/api/up?t='+T+'&name='+encodeURIComponent(f.name));
 var t0=Date.now();
 x.upload.onprogress=function(e){if(e.lengthComputable){var p=e.loaded/e.total*100;fill.style.width=p+'%';var s=e.loaded/Math.max(1,(Date.now()-t0)/1000);st.textContent=p.toFixed(0)+'% · '+hs(s)+'/s'}};
 x.onload=function(){fill.style.width='100%';st.className=x.status==200?'ok':'bad';st.textContent=x.status==200?'Sent ✓':'Failed';busy=false;load();next()};
 x.onerror=function(){st.className='bad';st.textContent='Failed – check Wi-Fi';busy=false;next()};
 x.send(f)}
q('#pick').onchange=function(e){add(e.target.files);e.target.value=''};
var d=q('#drop');
['dragenter','dragover'].forEach(function(n){d.addEventListener(n,function(e){e.preventDefault();d.classList.add('over')})});
['dragleave','drop'].forEach(function(n){d.addEventListener(n,function(e){e.preventDefault();d.classList.remove('over')})});
d.addEventListener('drop',function(e){add(e.dataTransfer.files)});
</script></body></html>"""
}
