import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const esc = (s='') => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const paths = {
 add:'M5 12h14M12 5v14', down:'m6 9 6 6 6-6', arrow:'M5 12h14m-6-6 6 6-6 6', search:'M21 21l-4.3-4.3M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0', copy:'M9 9h12v12H9zM5 15H3V3h12v2', upload:'M12 16V3m-5 5 5-5 5 5M4 16v5h16v-5', download:'M12 3v13m-5-5 5 5 5-5M4 17v4h16v-4', more:'M5 12h.01M12 12h.01M19 12h.01', check:'m5 12 4 4L19 6', close:'m6 6 12 12M18 6 6 18', eye:'M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7ZM15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0', play:'m8 4 12 8-12 8V4Z', code:'m8 6-5 6 5 6m8-12 5 6-5 6m-3-16-2 20', refresh:'M20 8a8 8 0 1 0 0 8M20 3v5h-5', file:'M14 3H5v18h14V8l-5-5Zm0 0v5h5M8 12h8M8 16h5', folder:'M3 7V4h6l3 3h9v13H3V7Z', lock:'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5V10Z', external:'M14 3h7v7M21 3 10 14M10 5H3v16h16v-7', clock:'M12 8v5l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', edit:'m16 3 5 5-12 12-6 1 1-6L16 3Z', trash:'M3 6h18M8 6V3h8v3M6 6l1 15h10l1-15', info:'M12 11v6M12 7h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0'
};
export function icon(name='more'){return '<svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="'+(paths[name]||paths.more)+'"/></svg>';}
export function btn(label,{primary=false,href='',small=false,icon:ic='',danger=false}={}){const c='btn'+(primary?' btn--primary':'')+(small?' btn--sm':'')+(danger?' btn--danger':'');return href?'<a class="'+c+'" href="'+href+'">'+(ic?icon(ic):'')+label+'</a>':'<button class="'+c+'" type="button">'+(ic?icon(ic):'')+label+'</button>';}
export function badge(label,tone='gray'){return '<span class="p-badge p-badge--'+tone+'">'+label+'</span>';}
export function field(label,value,{mono=false,hint='',required=false}={}){return '<label class="p-field-group"><span>'+label+(required?' <em>*</em>':'')+'</span><span class="p-field'+(mono?' mono':'')+'">'+value+'</span>'+(hint?'<small>'+hint+'</small>':'')+'</label>';}
export function toggle(on=true){return '<button class="sw'+(on?' sw--on':'')+'" role="switch" aria-checked="'+on+'" aria-label="切换设置"></button>';}
export function footer(count='共 8 条'){return '<div class="p-footer"><span>'+count+'</span><span class="p-grow"></span><span>每页 20 条</span><span class="mono">1—8</span><button class="pgbtn pgbtn--on">1</button><button class="pgbtn">2</button>'+icon('down')+'</div>';}
export const navMap={'接口管理':'01-api-list.html','文件服务器':'07-files.html','动态变量':'08-variables.html','使用统计':'09-stats.html','团队管理':'10-teams.html','用户管理':'11-users.html','请求日志':'03-logs.html','全局设置':'14-settings-providers.html'};
export const settingsItems=[['14-settings-providers.html','AI 供应商','连接与模型'],['15-settings-assistant.html','助手与能力','对话与工具'],['16-settings-mcp.html','MCP Server','接入与凭证'],['17-settings-general.html','基础设置','服务与安全'],['18-settings-logs.html','日志与保留','记录与清理'],['19-settings-storage.html','文件存储','存储与孤儿清理']];
export function settingsNav(current){return '<aside class="p-settings-nav p-card"><div class="p-eyebrow">系统配置</div>'+settingsItems.map(([href,label,note])=>'<a href="'+href+'" class="p-settings-link'+(href===current?' is-active':'')+'"><b>'+label+'</b><small>'+note+'</small></a>').join('')+'<div class="p-settings-note">仅超级管理员可修改<br>配置保存在当前实例</div></aside>';}
export function renderPage({file,title,active='接口管理',subtitle='',badge:count='',actions='',body='',styles=[]}){
 const base=fs.readFileSync(path.join(root,'01-api-list.html'),'utf8');
 let aside=base.match(/<aside class="rail">[\s\S]*?<\/aside>/)[0];
 aside=aside.replace(/<a class="nav__item(?: nav__item--on)?"[^>]*>([\s\S]*?)<\/a>/g,(_,inner)=>{const label=inner.match(/<\/svg><span>([^<]+)<\/span>/)?.[1];return '<a class="nav__item'+(label===active?' nav__item--on':'')+'" href="'+(navMap[label]||'index.html')+'"'+(label===active?' aria-current="page"':'')+'>'+inner+'</a>';});
 let topbar=base.match(/<header class="topbar">[\s\S]*?<\/header>/)[0].replace('<b>接口管理</b>','<b>'+esc(title)+'</b>');
 const html='<!doctype html>\n<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=1440, initial-scale=1"><title>'+esc(title)+' · MockHub Flat</title><link rel="stylesheet" href="assets/glass.css"><link rel="stylesheet" href="assets/pages.css">'+styles.map(x=>'<link rel="stylesheet" href="assets/'+x+'">').join('')+'</head><body><div class="app">'+aside+'<div class="work">'+topbar+'<main class="main full-page"><div class="page-head"><div><div class="page-title"><h1>'+title+'</h1>'+(count?'<span class="countpill">'+count+'</span>':'')+'</div><div class="page-sub">'+subtitle+'</div></div><div class="page-actions">'+actions+'</div></div><div class="p-body">'+body+'</div></main></div></div><script src="assets/preview.js"></script></body></html>';
 const fitStyle='<style id="board-fit">html,body{overflow:hidden!important;margin:0}.app{width:1440px!important;height:900px!important;min-height:0!important}</style>';
 const fitScript='<script id="board-fit-js">(function(){if(window.self!==window.top)return;var app=document.querySelector(".app");if(!app)return;function fit(){var s=Math.min(window.innerWidth/1440,window.innerHeight/900,1);app.style.transformOrigin="top left";app.style.transform="scale("+s+")";document.body.style.width=(1440*s)+"px";document.body.style.height=(900*s)+"px";}fit();window.addEventListener("resize",fit);window.addEventListener("load",fit);})();<\/script>';
 let out=html;
 if(!out.includes('id="board-fit"')){out=out.replace('</head>',fitStyle+'</head>').replace('</body>',fitScript+'</body>');}
 fs.writeFileSync(path.join(root,file),out);
 return file;
}
