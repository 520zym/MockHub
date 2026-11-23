import fs from 'node:fs';
import path from 'node:path';
import {root,navMap} from './ui.mjs';
for(const name of ['01-api-list','02-api-edit','03-logs','04-assistant','05-login']){
 const file=path.join(root,name+'.html');let s=fs.readFileSync(file,'utf8');
 s=s.replaceAll('MockHub Liquid Glass','MockHub Flat');
 if(!s.includes('href="assets/pages.css"'))s=s.replace('</head>','<link rel="stylesheet" href="assets/pages.css">\n</head>');
 if(!s.includes('src="assets/preview.js"'))s=s.replace('</body>','<script src="assets/preview.js"></script>\n</body>');
 s=s.replace(/<a class="(nav__item(?: nav__item--on)?)"[^>]*>([\s\S]*?)<\/a>/g,(_,classes,inner)=>{const label=inner.match(/<\/svg><span>([^<]+)<\/span>/)?.[1];return '<a class="'+classes+'" href="'+(navMap[label]||'index.html')+'">'+inner+'</a>';});
 if(name==='02-api-edit'){
  s=s.replace('<span class="seg__item">匹配规则 <span class="n">2</span></span>','<a class="seg__item" href="22-match-rules.html">匹配规则 <span class="n">2</span></a>');
  s=s.replace('<b>当前返回体的匹配规则</b>','<b>返回体规则概览</b>').replace('按优先级从上到下','按返回体顺序匹配');
 }
 if(name==='03-logs'){
  s=s.replace('<span>团队</span><b>全部</b>','<span>团队</span><b>支付中心</b>');
  if(!s.includes('href="12-operation-logs.html"'))s=s.replace('<div class="page-actions">','<div class="page-actions"><a class="btn" href="12-operation-logs.html">操作日志</a>');
 }
 if(name==='01-api-list')s=s.replace(/<button class="btn btn--primary">([\s\S]*?)新建接口<\/button>/,'<a class="btn btn--primary" href="06-api-new.html">$1新建接口</a>');
 if(name==='02-api-edit'&&!s.includes('href="20-api-soap.html"'))s=s.replace('<div class="page-actions">','<div class="page-actions"><a class="btn btn--sm" href="20-api-soap.html">SOAP 编辑</a>');
 if(name==='02-api-edit'&&!s.includes('data-last-edit'))s=s.replace('<span>最后保存 14:30:12</span><span class="dot"></span><span class="mono">张一鸣</span>','<span data-last-edit>最后修改：王琳 · 2 小时前</span><span class="dot"></span><a class="sub-link" href="permissions/p16-api-history.html">变更记录</a>');
 if(name==='04-assistant')s=s.replace('<button class="btn btn--sm">查看差异</button>','<a class="btn btn--sm" href="13-mcp-confirm.html">查看完整草稿</a>');
 if(name==='05-login'&&!s.includes('href="21-password.html"'))s=s.replace('<span>使用文档</span>','<a href="21-password.html">首次登录状态</a>');
 fs.writeFileSync(file,s);
}
console.log('Linked approved pages without changing their layout.');
