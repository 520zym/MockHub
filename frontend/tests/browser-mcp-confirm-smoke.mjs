// Real MCP-issued drafts + real JWT Web confirmation, no AI provider/API stubs.
import { readFile,writeFile,mkdir } from 'node:fs/promises'
import { randomBytes } from 'node:crypto'
import path from 'node:path'
import { browserDriver } from './helpers/browser-driver.mjs'
if(!process.argv.includes('--isolated'))throw new Error('Requires --isolated')
const state=JSON.parse(await readFile(process.argv.find(a=>a.startsWith('--state='))?.slice(8)||'','utf8')),credentials=JSON.parse(await readFile('target/browser-shell-smoke/local-credentials.json','utf8'))
if(!['localhost','127.0.0.1'].includes(new URL(state.url).hostname)||!state.dataDirectory.includes('/target/ui-smoke.')||credentials.dataDirectory!==state.dataDirectory)throw new Error('Refusing non-isolated state')
let admin,policy,rpcId=0;const report={backendIntegration:true,apiStub:false,checks:[]},output='target/browser-mcp-confirm-smoke',browser=await browserDriver()
const check=(name,passed)=>{report.checks.push({name,passed:!!passed});if(!passed)throw new Error('Failed: '+name)}
async function api(route,method='GET',data,token=admin?.token){const response=await fetch(state.url+'/api'+route,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(data===undefined?{}:{body:JSON.stringify(data)})});const result=await response.json();if(!response.ok||result.code!==0)throw new Error(method+' '+route+': '+result.msg);return result.data}
const login=(username,password)=>api('/auth/login','POST',{username,password},'')
async function draft(token,teamId,path,name){const response=await fetch(state.url+'/mcp',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({jsonrpc:'2.0',id:++rpcId,method:'tools/call',params:{name:'mockhub.create_api_draft',arguments:{teamId,name,method:'GET',path,responseBody:'{"approved":true}',contentType:'application/json'}}})});const result=await response.json();if(result.error||result.result?.isError)throw new Error('MCP draft creation rejected: '+JSON.stringify(result.error||result.result));return result.result.structuredContent||JSON.parse(result.result.content.find(c=>c.type==='text').text)}
const mint=(owner,teamId)=>api('/me/mcp-tokens','POST',{name:'human-confirm',expiresInDays:1,teamIds:[teamId],scopes:['API_READ','DRAFT_CREATE'],teamScopes:{[teamId]:['API_READ','DRAFT_CREATE']}},owner.token)
async function identity(login){await browser.cdp('Page.navigate',{url:state.url+'/#/login'});await browser.waitFor('!!document.querySelector(".login-card")','login');await browser.evaluate('localStorage.setItem("token",'+JSON.stringify(login.token)+');localStorage.setItem("user",'+JSON.stringify(JSON.stringify(login.user))+');location.hash="/account"');await browser.cdp('Page.reload',{ignoreCache:true});await browser.waitFor('!!document.querySelector(".app-layout")','real app shell')}
async function navigate(id){await browser.evaluate('void document.querySelector("#app").__vue_app__.config.globalProperties.$router.push('+JSON.stringify('/mcp-confirm/'+id)+')');await browser.waitFor('!!document.querySelector(".mcp-card__body")','real draft detail')}
async function confirm(){await browser.click('确认创建');await browser.waitFor('!!document.querySelector(".el-message-box .el-button--primary")','human approval');await browser.evaluate('document.querySelector(".el-message-box .el-button--primary").click()')}
try{
  admin=await login(credentials.username,credentials.password);policy=await api('/mcp/settings');const suffix=randomBytes(3).toString('hex')
  const a=await api('/teams','POST',{name:'MCP A '+suffix,identifier:('MA'+suffix).toUpperCase(),initialAdminUserId:admin.user.id}),b=await api('/teams','POST',{name:'MCP B '+suffix,identifier:('MB'+suffix).toUpperCase(),initialAdminUserId:admin.user.id})
  await api('/mcp/settings','PUT',{...policy,enabled:true,writeMode:'DRAFT_ONLY',toolCreateApiDraft:true,toolApplyApiDraft:false})
  const source=await mint(admin,a.id),first=await draft(source.token,a.id,'/human-approved','human-'+suffix)
  check('DRAFT_ONLY creates a pending draft without APPLY scope',typeof first.actionId==='string'&&(await api('/apis?teamId='+a.id)).total===0)
  await identity(admin);await browser.evaluate('document.querySelector("#app").__vue_app__.config.globalProperties.$pinia._s.get("app").setFilter('+JSON.stringify(b.id)+')');await navigate(first.actionId)
  check('different current team cannot silently confirm target-team draft',await browser.evaluate('[...document.querySelectorAll(".mcp-confirm-page button")].find(b=>b.textContent.trim()==="确认创建").disabled'))
  await browser.evaluate('[...document.querySelectorAll(".mcp-confirm-page button")].find(b=>b.textContent.includes("切换到")).click()')
  await browser.waitFor('[...document.querySelectorAll(".mcp-confirm-page button")].some(b=>b.textContent.trim()==="确认创建"&&!b.disabled)','deliberately selected target team')
  for(const width of [1440,768,375]){await browser.screenshot(path.join(output,'editor-'+width+'.png'),width,width<600?850:900);check('approval '+width+' has no document overflow',await browser.evaluate('document.documentElement.scrollWidth<=innerWidth'))}
  await confirm();await browser.waitFor('document.querySelector(".mcp-status")?.textContent.includes("已创建")','server confirmation')
  const created=await api('/apis?teamId='+a.id);check('explicit Web confirmation works in DRAFT_ONLY with no APPLY grant',created.total===1&&created.items[0].name==='human-'+suffix)
  check('confirmed API serves real Mock response',(await (await fetch(state.url+'/mock/'+a.identifier+'/human-approved')).json()).approved===true)
  const second=await draft(source.token,a.id,'/must-not-apply','revoked-'+suffix);await api('/me/mcp-tokens/'+source.id,'DELETE');await navigate(second.actionId);await confirm()
  await browser.waitFor('!!document.querySelector(".mcp-confirm-page [role=alert]")','revoked source error')
  check('revoked source remains PENDING and cannot create API',(await api('/ai/actions/'+second.actionId)).status==='PENDING'&&(await api('/apis?teamId='+a.id)).total===1)
  check('source rejection blocks client re-confirm without rebasing',await browser.evaluate('[...document.querySelectorAll(".mcp-confirm-page button")].find(b=>b.textContent.trim()==="确认创建").disabled'))
  const password='Owner#'+suffix,user=await api('/users','POST',{username:'mcp_owner_'+suffix,displayName:'MCP草稿持有人',password,globalRole:'USER'});await api('/users/'+user.id+'/teams','POST',{teamRoles:[{teamId:a.id,role:'EDITOR'}]});let owner=await login(user.username,password);await api('/auth/change-password','POST',{oldPassword:password,newPassword:password+'new'},owner.token);owner=await login(user.username,password+'new')
  const ownerSource=await mint(owner,a.id),owned=await draft(ownerSource.token,a.id,'/viewer-cancel','owned-'+suffix);await api('/users/'+user.id+'/teams','POST',{teamRoles:[{teamId:a.id,role:'VIEWER'}]});owner=await login(user.username,password+'new');await identity(owner);await navigate(owned.actionId)
  check('viewer is actually readonly but may cancel their own pending draft',await browser.evaluate('[...document.querySelectorAll(".mcp-confirm-page button")].find(b=>b.textContent.trim()==="确认创建").disabled&&![...document.querySelectorAll(".mcp-confirm-page button")].find(b=>b.textContent.trim()==="取消我的草稿").disabled'))
  await browser.click('取消我的草稿');await browser.waitFor('document.querySelector(".mcp-status")?.textContent.includes("已取消")','owner cancellation')
  check('owner cancellation reaches real backend without API mutation',(await api('/ai/actions/'+owned.actionId,'GET',undefined,owner.token)).status==='CANCELLED'&&(await api('/apis?teamId='+a.id)).total===1)
  check('no real browser exceptions',browser.exceptions.length===0);report.passed=true;console.log(JSON.stringify({passed:true,checks:report.checks.length,output},null,2))
}catch(error){report.passed=false;report.error=error.message;report.exceptions=browser.exceptions;report.consoleErrors=browser.consoleErrors;try{await browser.screenshot(path.join(output,'failure.png'),1440);report.pageText=(await browser.evaluate('document.body.innerText')).slice(-5000)}catch{}console.error(JSON.stringify({passed:false,error:report.error,output},null,2));process.exitCode=1}
finally{if(policy)try{await api('/mcp/settings','PUT',policy)}catch{report.restoreFailed=true;process.exitCode=1}await mkdir(output,{recursive:true});await writeFile(path.join(output,'report.json'),JSON.stringify(report,null,2));await browser.close()}
