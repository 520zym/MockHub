import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const [, ,suite='permissions',title='MockHub 权限方案设计稿',note='14 个场景'] = process.argv;
const dir=suite?path.join(root,suite):root;
const pages=JSON.parse(fs.readFileSync(path.join(dir,'pages.json'),'utf8'));
const groups=[...new Set(pages.map(p=>p.group))];
const img=p=>{const f=path.join(dir,'screens',p.id+'.png');return fs.existsSync(f)?'data:image/png;base64,'+fs.readFileSync(f).toString('base64'):'';};
const card=p=>'<figure class="c"><img src="'+img(p)+'" alt="'+p.title+'"><figcaption><span>'+p.id.replace(/\D/g,'').padStart(2,'0')+'</span><b>'+p.title+'</b><small>'+p.description+'</small><em>'+(p.route||'')+'</em></figcaption></figure>';
const html='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+title+'</title><style>'
+'*{box-sizing:border-box}body{margin:0;background:#EEF1F4;color:#17191D;font:13px/1.6 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}'
+'.w{max-width:1600px;margin:0 auto;padding:28px 24px 60px}'
+'header{display:flex;align-items:baseline;gap:14px;padding:0 4px 18px;flex-wrap:wrap}header h1{font-size:22px;margin:0;letter-spacing:-.02em}header p{margin:0;color:#6F757E;font-size:12px}'
+'h2{font-size:13px;margin:30px 4px 12px;color:#3B4048}h2 span{color:#9CA2AA;font-weight:400;margin-left:8px;font-size:11px}'
+'.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(420px,1fr));gap:16px}'
+'.c{margin:0;background:#fff;border-radius:16px;overflow:hidden}'
+'.c img{display:block;width:100%;height:auto;aspect-ratio:1.6;object-fit:cover;object-position:top center}'
+'figcaption{padding:12px 14px 14px;display:grid;grid-template-columns:auto 1fr;gap:5px 10px;align-items:baseline}'
+'figcaption span{font:10px ui-monospace,Menlo,monospace;color:#3568D4;background:#EDF2FC;border-radius:6px;padding:3px 6px}'
+'figcaption b{font-size:12.5px;font-weight:600}figcaption small{grid-column:1/-1;color:#6F757E;font-size:11px;line-height:1.55}'
+'figcaption em{grid-column:1/-1;font-style:normal;font-size:10px;color:#9CA2AA;font-family:ui-monospace,Menlo,monospace}'
+'footer{margin-top:34px;padding-top:16px;border-top:1px solid #E3E7EC;color:#9CA2AA;font-size:11px}'
+'</style></head><body><div class="w"><header><h1>'+title+'</h1><p>'+note+' · 单文件自包含，不依赖任何外部资源</p></header>'
+groups.map(g=>'<h2>'+g+'<span>'+pages.filter(p=>p.group===g).length+' SCENES</span></h2><div class="grid">'+pages.filter(p=>p.group===g).map(card).join('')+'</div>').join('')
+'<footer>静态演示数据 · 权限方案提案，非业务实现 · 2026-10-29</footer></div></body></html>';
fs.writeFileSync(path.join(dir,'index-standalone.html'),html);
console.log('built',suite,'scenes',pages.length,'size',(html.length/1024/1024).toFixed(2)+'MB');
