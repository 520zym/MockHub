// Actual isolated MockHub HTTP/MCP endpoints. No API/provider stubs or real-business data.
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { randomBytes } from 'node:crypto'
if (!process.argv.includes('--isolated')) throw new Error('Requires --isolated')
const statePath = process.argv.find(a=>a.startsWith('--state='))?.slice(8)
if (!statePath) throw new Error('Pass --state=...')
const state = JSON.parse(await readFile(statePath,'utf8')), credentials = JSON.parse(await readFile('target/browser-shell-smoke/local-credentials.json','utf8'))
if (!['127.0.0.1','localhost'].includes(new URL(state.url).hostname) || !state.dataDirectory.includes('/target/ui-smoke.') || credentials.dataDirectory !== state.dataDirectory) throw new Error('Refusing unverified isolated instance')
const report = { backendIntegration:true, apiStub:false, checks:[] }; let admin, savedPolicy, rpcId = 0
function check(name, yes) { report.checks.push({name,passed:!!yes}); if(!yes)throw new Error('Failed: '+name) }
async function api(route,method='GET',data,token=admin?.token,allow=false) {
  const response = await fetch(state.url+'/api'+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(data instanceof FormData?{}:{'Content-Type':'application/json'})},...(data===undefined?{}:{body:data instanceof FormData?data:JSON.stringify(data)})})
  const body=await response.json(); if(!allow && (!response.ok||body.code!==0))throw new Error(method+' '+route+': '+(body.msg||response.status)); return allow?{...body,httpStatus:response.status}:body.data
}
async function login(username,password){return api('/auth/login','POST',{username,password},'')}
async function rpc(token,method,params={}) { const response=await fetch(state.url+'/mcp',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({jsonrpc:'2.0',id:++rpcId,method,params})});return {httpStatus:response.status,...await response.json()} }
const tool = (token,name,args) => rpc(token,'tools/call',{name,arguments:args})
const denied = response => !!response.error || response.result?.isError === true || response.httpStatus >= 400
const payload = (teamId,name,path='/live-scope')=>({teamId,name,path,method:'GET',type:'REST',enabled:true,responseMode:'CONDITION',responses:[{name:'Default',active:true,responseCode:200,contentType:'application/json',responseBody:'{"ok":true}',bodyType:'TEXT',sortOrder:0}]})
try {
  admin=await login(credentials.username,credentials.password)
  const suffix=randomBytes(3).toString('hex'), password='Local#'+suffix, changed=password+'new'
  const a=await api('/teams','POST',{name:'生命周期 A '+suffix,identifier:('LA'+suffix).toUpperCase(),initialAdminUserId:admin.user.id})
  const b=await api('/teams','POST',{name:'生命周期 B '+suffix,identifier:('LB'+suffix).toUpperCase(),initialAdminUserId:admin.user.id})
  const config=payload(a.id,'scope-allowed-'+suffix), first=await api('/apis','POST',config), other=await api('/apis','POST',payload(b.id,'scope-forbidden-'+suffix))
  const user=await api('/users','POST',{username:'live_'+suffix,displayName:'生命周期成员',password,globalRole:'USER'})
  await api('/users/'+user.id+'/teams','POST',{teamRoles:[{teamId:a.id,role:'EDITOR'}]})
  let member=await login(user.username,password);await api('/auth/change-password','POST',{oldPassword:password,newPassword:changed},member.token);member=await login(user.username,changed)
  savedPolicy=await api('/mcp/settings')
  const policy=Object.fromEntries(Object.entries(savedPolicy).filter(([key])=>key==='enabled'||key==='writeMode'||key.startsWith('tool')))
  await api('/mcp/settings','PUT',{...policy,enabled:true,writeMode:'DRAFT_ONLY',toolSearchApis:true,toolGetApiDetail:true,toolGetApiStats:true})
  const mint=(token,name)=>api('/me/mcp-tokens','POST',{name,expiresInDays:1,teamIds:[a.id],scopes:['API_READ','STATS_READ'],teamScopes:{[a.id]:['API_READ','STATS_READ']}},token)
  const personal=await mint(admin.token,'explicit-root-read')
  check('personal token performs a real MCP initialize', (await rpc(personal.token,'initialize',{protocolVersion:'2024-11-05',clientInfo:{name:'isolated-test',version:'1'},capabilities:{}})).result?.serverInfo?.name==='mockhub')
  const tools=(await rpc(personal.token,'tools/list')).result?.tools||[]
  check('readonly scope exposes search/detail but no write tools',tools.some(t=>t.name==='mockhub.search_apis')&&tools.some(t=>t.name==='mockhub.get_api_detail')&&!tools.some(t=>/draft|generate/i.test(t.name)))
  const search=await tool(personal.token,'mockhub.search_apis',{keyword:'scope-'})
  check('no-team MCP search only searches explicitly allowed teams',!denied(search)&&JSON.stringify(search.result).includes(config.name)&&!JSON.stringify(search.result).includes('scope-forbidden-'+suffix))
  check('root ownership cannot bypass explicit token team scope',denied(await tool(personal.token,'mockhub.get_api_detail',{apiId:other.id})))
  check('explicit forbidden team cannot widen MCP search',denied(await tool(personal.token,'mockhub.search_apis',{teamId:b.id})))
  check('allowed resource metadata is accessible',!denied(await tool(personal.token,'mockhub.get_api_detail',{apiId:first.id})))
  await api('/mcp/settings','PUT',{...policy,enabled:true,writeMode:'DRAFT_ONLY',toolSearchApis:true,toolGetApiDetail:false})
  check('instance-disabled tool is denied despite token scope',denied(await tool(personal.token,'mockhub.get_api_detail',{apiId:first.id})))
  await api('/mcp/settings','PUT',{...policy,enabled:true,writeMode:'DRAFT_ONLY',toolSearchApis:true,toolGetApiDetail:true})
  check('web JWT cannot be used as MCP credential',(await rpc(admin.token,'tools/list')).httpStatus===401)
  await api('/auth/logout','POST')
  check('ordinary web logout does not revoke personal MCP',(await rpc(personal.token,'tools/list')).httpStatus===200)
  admin=await login(credentials.username,credentials.password)
  await api('/me/mcp-tokens/'+personal.id,'DELETE')
  check('revoked credential fails at real MCP transport',(await rpc(personal.token,'tools/list')).httpStatus===401)
  const memberToken=await mint(member.token,'owner-password-revocation')
  check('active editor personal token works',(await rpc(memberToken.token,'tools/list')).httpStatus===200)
  await api('/auth/change-password','POST',{oldPassword:changed,newPassword:changed+'again'},member.token)
  check('password security event revokes MCP',(await rpc(memberToken.token,'tools/list')).httpStatus===401)
  check('same event revokes the old web sid',(await api('/auth/me','GET',undefined,member.token,true)).httpStatus===401)
  member=await login(user.username,changed+'again')
  const updated=await api('/apis/'+first.id,'PUT',{...config,name:'teammate-edit-'+suffix,expectedRevision:first.revision},member.token)
  check('editor can update a teammates API',updated.revision===2)
  check('stale writes are rejected without overwriting',(await api('/apis/'+first.id,'PUT',{...config,name:'stale',expectedRevision:1},admin.token,true)).code===40921)
  const history=await api('/apis/'+first.id+'/revisions','GET',undefined,member.token), initial=history.find(item=>item.revision===1)
  const rolled=await api('/apis/'+first.id+'/revisions/'+initial.id+'/rollback','POST',{expectedRevision:2},member.token)
  check('real rollback creates a new revision and preserves creator',rolled.revision===3&&(await api('/apis/'+first.id)).createdBy===admin.user.id)
  await api('/apis/'+first.id+'?expectedRevision=3','DELETE',undefined,member.token)
  check('trash is excluded from public Mock',!(await fetch(state.url+'/mock/'+a.identifier+'/live-scope')).ok)
  const trashed=(await api('/apis/trash?teamId='+a.id)).items.find(row=>row.id===first.id)
  check('trash keeps original data and CAS revision',trashed?.revision===4)
  await api('/apis','POST',{...config,name:'replacement-'+suffix})
  check('restore refuses to overwrite active replacement',(await api('/apis/'+first.id+'/restore','POST',{expectedRevision:4},member.token,true)).code===40922)
  const restored=await api('/apis/'+first.id+'/restore','POST',{expectedRevision:4,newPath:'/live-restored'},member.token)
  check('restore uses original trash revision and defaults to disabled',restored.revision===5&&(await api('/apis/'+first.id)).enabled===false)
  await api('/apis/'+first.id+'/toggle?expectedRevision=5','PUT',undefined,member.token)
  await api('/teams/'+a.id+'/status','PATCH',{status:'ARCHIVED'})
  check('team archive does not stop already-enabled Mock',(await fetch(state.url+'/mock/'+a.identifier+'/live-restored')).ok)
  check('team archive freezes editor writes',(await api('/apis/'+first.id,'PUT',{...config,path:'/live-restored',expectedRevision:6},member.token,true)).code===40903)
  await api('/teams/'+a.id+'/status','PATCH',{status:'ACTIVE'})
  const form=new FormData();form.append('teamId',a.id);form.append('file',new Blob(['owned-by-'+suffix],{type:'text/plain'}),'source.txt')
  const upload=await api('/apis/files/upload','POST',form,member.token)
  const fileConfig={...payload(a.id,'owned-file-'+suffix,'/live-file'),responses:[{name:'File',active:true,responseCode:200,bodyType:'FILE',filePath:upload.filePath,fileName:upload.fileName,fileSize:upload.fileSize,downloadName:'result.txt',contentType:'text/plain'}]}
  const fileApi=await api('/apis','POST',fileConfig,member.token)
  check('owned file response serves the stored bytes',(await (await fetch(state.url+'/mock/'+a.identifier+'/live-file')).text())==='owned-by-'+suffix)
  check('foreign-team attachment references are rejected',(await api('/apis','POST',{...fileConfig,teamId:b.id,path:'/foreign-file'},admin.token,true)).code===40925)
  await api('/users/'+user.id+'/teams','POST',{teamRoles:[{teamId:a.id,role:'VIEWER'}]})
  check('same sid reads after demotion but cannot write',!!(await api('/apis/'+fileApi.id,'GET',undefined,member.token)).id&&(await api('/apis/'+fileApi.id,'PUT',{...fileConfig,expectedRevision:1},member.token,true)).code===40104)
  await api('/apis/'+first.id+'?expectedRevision=6','DELETE')
  const forbidden=await api('/apis/'+first.id+'/purge?expectedRevision=7','DELETE',undefined,member.token,true)
  check('viewer cannot permanently purge team resources',[40101,40102,40103,40104].includes(forbidden.code))
  await api('/apis/'+first.id+'/purge?expectedRevision=7','DELETE')
  check('admin purge removes trash content',!(await api('/apis/trash?teamId='+a.id)).items.some(row=>row.id===first.id))
  report.passed=true;console.log(JSON.stringify({passed:true,checks:report.checks.length,output:'target/live-lifecycle-mcp-smoke/report.json'},null,2))
} catch(error){report.passed=false;report.error=error.message;console.error(JSON.stringify({passed:false,error:report.error,checks:report.checks.length},null,2));process.exitCode=1}
finally {
  if(savedPolicy){try{admin=await login(credentials.username,credentials.password);await api('/mcp/settings','PUT',Object.fromEntries(Object.entries(savedPolicy).filter(([key])=>key==='enabled'||key==='writeMode'||key.startsWith('tool'))))}catch{report.policyRestoreFailed=true;process.exitCode=1}}
  await mkdir('target/live-lifecycle-mcp-smoke',{recursive:true});await writeFile('target/live-lifecycle-mcp-smoke/report.json',JSON.stringify(report,null,2))
}
