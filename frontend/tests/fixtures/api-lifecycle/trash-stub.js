// Explicit API stub data. These examples are NOT real deleted business resources.
function seed(state) {
  if(state.trash)return
  const now=Date.now(),day=86400000
  state.trash=[['deleted-api-conflict','team-a','GET','/api/user/{id}','旧用户详情',4,20,10],['deleted-api-retained','team-a','POST','/api/member/benefits','会员权益查询',2,3,27],['deleted-api-expired','team-a','GET','/legacy/member/level','旧版会员等级',8,31,-1],['deleted-pay','team-b','POST','/api/payment/legacy','支付历史配置',3,25,5]].map(([id,teamId,method,path,name,revision,age,remaining])=>({id,teamId,teamName:teamId==='team-a'?'用户中心':'支付中心',teamIdentifier:teamId==='team-a'?'USER':'PAY',type:'REST',method,path,name,revision,deletedAt:new Date(now-age*day).toISOString(),deletedBy:'other',deletedByName:'李哲',purgeAfter:new Date(now+remaining*day).toISOString()}))
  state.holdTrashTeam='';state.trashDeferred=[];state.archivedTeams={};state.purged=[]
  for(const row of state.trash)state.revisions[row.id]=row.revision
}
const answer=data=>({handled:true,data})
export async function trashInvoke(state,method,url,body,config) {
  seed(state)
  if(url==='/apis/trash'&&method==='GET') {
    const params=config?.params||{};if(!params.teamId)throw new Error('Fixture forbids unscoped recycle bin')
    const list=state.trash.filter(row=>row.teamId===params.teamId).map(row=>({...row,canRestore:state.role!=='VIEWER'&&!state.archivedTeams[row.teamId]&&Date.parse(row.purgeAfter)>Date.now(),canPurge:state.role==='TEAM_ADMIN'&&!state.archivedTeams[row.teamId]})),result={items:structuredClone(list.slice(((params.page||1)-1)*(params.size||20),(params.page||1)*(params.size||20))),total:list.length}
    if(state.holdTrashTeam===params.teamId)await new Promise(resolve=>state.trashDeferred.push(resolve))
    return answer(result)
  }
  const action=url.match(/^\/apis\/([^/]+)\/(restore|purge)$/)
  if(!action)return {handled:false}
  const row=state.trash.find(row=>row.id===decodeURIComponent(action[1]))
  if(!row)throw {code:40402,msg:'接口不存在或已被彻底删除'}
  if(state.role==='VIEWER')throw {code:40104,msg:'只读角色不能写入回收站'}
  if(state.archivedTeams[row.teamId])throw {code:40903,msg:'归档团队不能写入'}
  const expected=action[2]==='restore'?body.expectedRevision:config?.params?.expectedRevision
  if(expected!==row.revision)throw {code:40921,msg:'接口版本已变化'}
  if(action[2]==='restore') {
    if(Date.parse(row.purgeAfter)<=Date.now())throw {code:40923,msg:'恢复期限已过'}
    const path=body.newPath||row.path
    if(state.rows.some(api=>api.teamId===row.teamId&&api.method===row.method&&api.path===path))throw {code:40922,msg:'恢复路径冲突'}
    const active={...row,path,enabled:false,revision:row.revision+1,updatedByName:'王琳',createdBy:'other',createdByName:'李哲',updatedAt:new Date().toISOString(),hitCount:0,tags:[]};state.rows.push(active);state.revisions[row.id]=active.revision;state.trash=state.trash.filter(item=>item.id!==row.id)
    return answer({id:active.id,teamId:active.teamId,name:active.name,path:active.path,method:active.method,revision:active.revision})
  }
  if(state.role!=='TEAM_ADMIN')throw {code:40101,msg:'彻底删除需要团队管理员'}
  state.trash=state.trash.filter(item=>item.id!==row.id);state.purged.push({id:row.id,teamId:row.teamId})
  return answer(null)
}
