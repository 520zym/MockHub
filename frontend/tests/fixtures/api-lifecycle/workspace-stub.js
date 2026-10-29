// Explicit in-memory transport for the real workspace component fixture; never used by production.
import { trashInvoke } from './trash-stub.js'
function seed(state) {
  if(state.rows)return
  state.rows=[['api-a','GET','/api/user/{id}','用户详情与状态','other',3102],['api-login','POST','/api/user/login','账号登录','user-1',1844],['api-profile','GET','/api/user/profile','个人资料','user-1',842],['api-update','PATCH','/api/user/profile','修改个人资料','other',206],['api-token','POST','/api/user/token','刷新令牌 · Mock 示例','user-1',418],['api-legacy','GET','/legacy/user/level','旧版会员等级','other',0],['api-b','GET','/api/pay/{id}','支付对账','other',812]].map(([id,method,path,name,createdBy,hitCount],index)=>({id,teamId:id==='api-b'?'team-b':'team-a',teamName:id==='api-b'?'支付中心':'用户中心',teamIdentifier:id==='api-b'?'PAY':'USER',type:index===0?'SOAP':'REST',method,path,name,createdBy,createdByName:createdBy==='user-1'?'王琳':'李哲',updatedBy:'user-1',updatedByName:'王琳',revision:3,hitCount,lastCalledAt:hitCount?'2026-10-29T03:12:00Z':null,createdAt:'2026-10-20T02:00:00Z',updatedAt:'2026-10-29T03:12:00Z',enabled:id!=='api-legacy',groupId:'group-'+(id==='api-b'?'b':'a'),groupName:id==='api-b'?'支付模块':'用户模块',tags:[{id:'tag-'+(id==='api-b'?'b':'a'),name:id==='api-b'?'支付':'用户',color:'#3568d4'}]}))
  state.groups=['a','b'].map(key=>({id:'group-'+key,teamId:'team-'+key,name:key==='a'?'用户模块':'支付模块',sortOrder:1,apiCount:key==='a'?6:1}))
  state.tags=['a','b'].map(key=>({id:'tag-'+key,teamId:'team-'+key,name:key==='a'?'用户':'支付',color:'#3568d4'}))
  state.holdListTeam='';state.listDeferred=[];state.importConflict=false
}
const answer=data=>({handled:true,data})
export async function workspaceInvoke(state,method,url,body,config) {
  seed(state)
  const trash=await trashInvoke(state,method,url,body,config);if(trash.handled)return trash
  if(url==='/apis'&&method==='GET') {
    const params=config?.params||{};if(!params.teamId)throw new Error('Fixture forbids unscoped list requests')
    let list=state.rows.filter(row=>row.teamId===params.teamId)
    if(params.createdBy==='me')list=list.filter(row=>row.createdBy==='user-1')
    if(params.keyword)list=list.filter(row=>(row.name+' '+row.path).includes(params.keyword))
    if(params.method)list=list.filter(row=>row.method===params.method)
    if(params.type)list=list.filter(row=>row.type===params.type)
    if(typeof params.enabled==='boolean')list=list.filter(row=>row.enabled===params.enabled)
    if(Object.hasOwn(params,'groupId'))list=list.filter(row=>(row.groupId||'')===params.groupId)
    const result={items:structuredClone(list.slice(((params.page||1)-1)*(params.size||20),(params.page||1)*(params.size||20))),total:list.length}
    if(state.holdListTeam===params.teamId)await new Promise(resolve=>state.listDeferred.push(resolve))
    return answer(result)
  }
  if(url==='/groups'||url==='/tags') {
    const collection=url==='/groups'?'groups':'tags',teamId=config?.params?.teamId||body?.teamId
    if(method==='GET')return answer(structuredClone(state[collection].filter(row=>row.teamId===teamId)))
    if(method==='POST'){if(state.role==='VIEWER')throw {code:40104,msg:'只读不能创建共享配置'};const row={...body,id:collection+'-'+Date.now(),apiCount:0};state[collection].push(row);return answer(row)}
  }
  const metadata=url.match(/^\/(groups|tags)\/([^/]+)$/)
  if(metadata){
    if(state.role==='VIEWER')throw {code:40104,msg:'只读不能修改共享配置'}
    const collection=metadata[1],id=decodeURIComponent(metadata[2]),row=state[collection].find(item=>item.id===id)
    if(method==='PUT')Object.assign(row,body)
    if(method==='DELETE')state[collection]=state[collection].filter(item=>item.id!==id)
    for(const api of state.rows.filter(api=>api.teamId===row.teamId)){api.revision++;if(collection==='groups')api.groupName=method==='DELETE'?'':row.name;else api.tags=method==='DELETE'?[]:[{...row}]}
    return answer(method==='DELETE'?null:structuredClone(row))
  }
  if(url==='/apis/batch'&&method==='POST') {
    if(state.role==='VIEWER'||(body.action==='delete'&&state.role!=='TEAM_ADMIN'))throw {code:40104,msg:'当前角色不能批量修改'}
    const targets=body.ids.map(id=>state.rows.find(row=>row.id===id));if(targets.some(row=>!row||row.revision!==body.expectedRevisions[row.id]))throw {code:40921,msg:'版本冲突'}
    for(const row of targets){row.revision++;if(body.action==='enable')row.enabled=true;if(body.action==='disable')row.enabled=false;if(body.action==='move-group'){row.groupId=body.targetGroupId;row.groupName=state.groups.find(group=>group.id===body.targetGroupId)?.name||''}}
    if(body.action==='delete')state.rows=state.rows.filter(row=>!body.ids.includes(row.id))
    return answer({affected:targets.length,revisions:Object.fromEntries(targets.map(row=>[row.id,row.revision]))})
  }
  if(url==='/apis/import'&&method==='POST') {
    const mode=body.get('mode'),teamId=body.get('teamId'),expected=JSON.parse(body.get('expectedRevisions'))
    if(state.role==='VIEWER'||(mode==='override'&&state.role!=='TEAM_ADMIN'))throw {code:40104,msg:'当前角色不能导入'}
    if(state.importConflict)throw {code:40921,msg:'目标版本已变化'}
    const data=JSON.parse(await body.get('file').text());let imported=0,skipped=0,overridden=0
    for(const source of data.apis){const path='/' + source.path.trim().split(/[?#]/)[0].replace(/^\//,''),target=state.rows.find(row=>row.teamId===teamId&&row.method===source.method&&row.path===path);if(target&&mode==='merge'){skipped++;continue}if(target){if(expected[target.id]!==target.revision)throw {code:40921,msg:'目标版本已变化'};target.revision++;target.name=source.name;overridden++}else{state.rows.push({...structuredClone(state.rows.find(row=>row.teamId===teamId)),id:'import-'+imported+'-'+Date.now(),teamId,path,name:source.name,method:source.method,revision:1});imported++}}
    return answer({imported,skipped,overridden,warnings:['TEAM_HEADERS_NOT_IMPORTED: 团队全局响应头未修改，请管理员单独核对']})
  }
  const action=url.match(/^\/apis\/([^/]+)(?:\/(toggle|copy))?$/)
  if(action&&['PUT','POST','DELETE'].includes(method)) {
    if(state.role==='VIEWER')throw {code:40104,msg:'当前角色不能修改'}
    const row=state.rows.find(row=>row.id===action[1]),expected=config?.params?.expectedRevision
    if(!row||state.conflict||expected!==row.revision)throw {code:40921,msg:'接口版本已变化'}
    if(action[2]==='toggle'){row.enabled=!row.enabled;row.revision++;return answer({enabled:row.enabled,revision:row.revision})}
    if(action[2]==='copy'){const copy={...structuredClone(row),id:'copy-'+state.rows.length,path:row.path+'-copy',name:row.name+'（副本）',revision:1,createdBy:'user-1',createdByName:'王琳'};state.rows.push(copy);return answer(copy)}
    if(method==='DELETE'){state.rows=state.rows.filter(item=>item.id!==row.id);return answer(null)}
  }
  if(url.startsWith('/ai/'))return answer(url.includes('conversations')?[]:{enabled:false})
  return {handled:false}
}
