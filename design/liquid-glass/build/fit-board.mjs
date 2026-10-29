import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';
const base=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const MARK='<!-- board-fit -->';
const STYLE='<style id="board-fit">html,body{overflow:hidden!important;margin:0}.app{width:1440px!important;height:900px!important;min-height:0!important}</style>';
const SCRIPT='<script id="board-fit-js">(function(){if(window.self!==window.top)return;var app=document.querySelector(".app");if(!app)return;function fit(){var s=Math.min(window.innerWidth/1440,window.innerHeight/900,1);app.style.transformOrigin="top left";app.style.transform="scale("+s+")";document.body.style.width=(1440*s)+"px";document.body.style.height=(900*s)+"px";}fit();window.addEventListener("resize",fit);window.addEventListener("load",fit);})();<\/script>';
const skip=new Set(['index.html','preview.html']);
const dirs=[base,path.join(base,'permissions')];
let n=0;
for(const dir of dirs){
 for(const f of fs.readdirSync(dir)){
  if(!f.endsWith('.html')||skip.has(f))continue;
  const p=path.join(dir,f);let s=fs.readFileSync(p,'utf8');
  s=s.replace(/<!-- board-fit -->[\s\S]*?<\/script>/,'').replace(/<style id="board-fit">[\s\S]*?<\/style>/,'').replace(/<script id="board-fit-js">[\s\S]*?<\/script>/,'');
  if(!s.includes('</head>')||!s.includes('</body>'))continue;
  s=s.replace('</head>',MARK+STYLE+'</head>').replace('</body>',SCRIPT+'</body>');
  fs.writeFileSync(p,s);n++;
 }
}
console.log('board-fit applied to',n,'pages');
