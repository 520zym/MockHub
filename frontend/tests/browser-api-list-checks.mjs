import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { deflateRawSync } from 'node:zlib'
const visible = 'e=>e.getBoundingClientRect().width>0'
function zipManifest(data) {
  const name=Buffer.from('mockhub-export.json'),raw=Buffer.from(JSON.stringify(data)),compressed=deflateRawSync(raw);let crc=0xffffffff
  for(const value of raw){crc^=value;for(let index=0;index<8;index++)crc=(crc>>>1)^((crc&1)?0xedb88320:0)}crc=(crc^0xffffffff)>>>0
  const local=Buffer.alloc(30),central=Buffer.alloc(46),end=Buffer.alloc(22)
  local.writeUInt32LE(0x04034b50);local.writeUInt16LE(20,4);local.writeUInt16LE(8,8);local.writeUInt32LE(crc,14);local.writeUInt32LE(compressed.length,18);local.writeUInt32LE(raw.length,22);local.writeUInt16LE(name.length,26)
  central.writeUInt32LE(0x02014b50);central.writeUInt16LE(20,4);central.writeUInt16LE(20,6);central.writeUInt16LE(8,10);central.writeUInt32LE(crc,16);central.writeUInt32LE(compressed.length,20);central.writeUInt32LE(raw.length,24);central.writeUInt16LE(name.length,28)
  end.writeUInt32LE(0x06054b50);end.writeUInt16LE(1,8);end.writeUInt16LE(1,10);end.writeUInt32LE(central.length+name.length,12);end.writeUInt32LE(local.length+name.length+compressed.length,16)
  return Buffer.concat([local,name,compressed,central,name,end])
}
export async function runListChecks({ cdp, evaluate, waitFor, click, check, screenshot }) {
  await waitFor('window.fixture && document.querySelectorAll(".api-grid tbody tr").length===6 && !document.querySelector(".api-results .el-loading-mask")')
  check('explicit API stub mounts the real current-team workspace',await evaluate('window.fixture.apiStub===true'))
  check('API table separates path, name, group and audit fields into single-purpose columns',await evaluate('(()=>{const heads=[...document.querySelectorAll(".api-grid th")].map(e=>e.textContent.trim());return ["路径","名称","分组","标签","修改时间","创建人"].every(t=>heads.includes(t))&&!document.querySelector(".api-grid .api-subtitle,.api-grid .revision-chip")})()'))
  check('normal table rows stay compact without stacked metadata',await evaluate('[...document.querySelectorAll(".api-grid tbody tr")].every(e=>e.getBoundingClientRect().height<=52)'))
  check('wide data scrolls inside the table with fixed operations',await evaluate('(()=>{const region=document.querySelector(".api-grid-scroll");return region.scrollWidth>region.clientWidth&&getComputedStyle(document.querySelector(".api-grid td.cell-actions")).position==="sticky"})()'))
  for(const role of ['EDITOR','VIEWER']) {
    await evaluate('window.fixture.setRole('+JSON.stringify(role)+')');await waitFor('document.querySelectorAll(".api-grid tbody tr").length===6 && !document.querySelector(".api-results .el-loading-mask")')
    for(const width of [1810,1440,768,375]){await cdp('Emulation.setDeviceMetricsOverride',{width,height:960,deviceScaleFactor:1,mobile:false});await screenshot('list-'+role.toLowerCase()+'-'+width);check('list '+role+' has no horizontal document overflow at '+width,await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));check('list '+role+' stays inside the real shell at '+width,await evaluate('document.querySelector(".workspace-table").getBoundingClientRect().right<=innerWidth+1'))}
  }
  await cdp('Emulation.setDeviceMetricsOverride',{width:1440,height:960,deviceScaleFactor:1,mobile:false})
  check('viewer has no create or import control',await evaluate('![...document.querySelectorAll("button")].some(e=>e.getBoundingClientRect().width>0&&["导入","＋ 新建接口"].includes(e.textContent.trim()))'))
  check('viewer has no enabled toggle controls',await evaluate('[...document.querySelectorAll(".api-grid .el-switch")].filter('+visible+').length===0'))
  await click('我创建的');await waitFor('document.querySelectorAll(".api-grid tbody tr").length===3')
  check('viewer createdBy=me is a real scoped server query',await evaluate('window.fixture.calls.some(c=>c.url==="/apis"&&c.params?.teamId==="team-a"&&c.params?.createdBy==="me")'))
  await click('我创建的');await waitFor('document.querySelectorAll(".api-grid tbody tr").length===6')
  await evaluate('window.fixture.setRole("EDITOR")');await waitFor('[...document.querySelectorAll(".api-grid .el-switch")].filter('+visible+').length===6')
  check('editor may edit teammates resources, not just own rows',await evaluate('document.querySelector(".api-grid tbody tr .cell-creator").textContent.includes("李哲")&&[...document.querySelectorAll(".api-grid tbody tr:first-child button")].some(e=>e.textContent.trim()==="编辑"&&!e.disabled)'))
  await evaluate('document.querySelector(".api-grid tbody tr:first-child .el-switch__core").click()');await waitFor('window.fixture.calls.some(c=>c.url==="/apis/api-a/toggle") && !document.querySelector(".api-results .el-loading-mask")')
  check('toggle sends the loaded revision',await evaluate('window.fixture.calls.find(c=>c.url==="/apis/api-a/toggle").params.expectedRevision===3'))
  await evaluate('document.querySelector(".api-grid tbody tr:first-child .el-checkbox").click()');await waitFor('document.querySelector(".api-selection")')
  check('editor batch delete is absent',await evaluate('![...document.querySelectorAll(".api-selection button")].some(e=>e.textContent.trim()==="批量删除")'))
  await click('停用');await waitFor('window.fixture.calls.some(c=>c.url==="/apis/batch")&&!document.querySelector(".api-selection")')
  check('batch carries each frozen row version',await evaluate('window.fixture.calls.find(c=>c.url==="/apis/batch").body.expectedRevisions["api-a"]===4'))
  await evaluate('document.querySelector(".api-grid tbody tr:first-child .el-checkbox").click()');await waitFor('document.querySelector(".api-selection")')
  await click('分组 / 标签');await evaluate('[...document.querySelectorAll(".el-dropdown-menu__item")].find(e=>e.getBoundingClientRect().width>0&&e.textContent.trim()==="管理分组").click()')
  await waitFor('document.querySelectorAll(".metadata-row").length===1')
  check('editor can rename shared groups',await evaluate('[...document.querySelectorAll(".metadata-row button")].some(e=>e.textContent.trim()==="重命名"&&!e.disabled)'))
  await click('重命名');await evaluate('(()=>{const input=document.querySelector("input[aria-label=分组名称]");input.value="新用户模块";input.dispatchEvent(new Event("input",{bubbles:true}))})()');await click('保存');await waitFor('window.fixture.calls.some(c=>c.method==="PUT"&&c.url==="/groups/group-a")&&!document.querySelector(".api-selection")')
  check('metadata saves clear old selected API versions',await evaluate('!document.querySelector(".api-selection")'))
  await click('关闭');await waitFor('![...document.querySelectorAll(".metadata-list")].some('+visible+')')
  await evaluate('(()=>{const button=[...document.querySelectorAll(".api-grid tbody tr:first-child button")].find(e=>e.textContent.trim()==="更多");button.click()})()');await evaluate('[...document.querySelectorAll(".el-dropdown-menu__item")].find(e=>e.getBoundingClientRect().width>0&&e.textContent.trim()==="移入回收站").click()');await waitFor('document.querySelector(".delete-targets")?.getBoundingClientRect().width>0')
  const deletes=await evaluate('window.fixture.calls.filter(c=>c.method==="DELETE").length');await evaluate('window.fixture.setTeam("team-b")');await waitFor('document.body.innerText.includes("支付对账")&&!document.querySelector(".delete-targets")?.getBoundingClientRect().width')
  check('team change cancels old-team delete confirmation without a request',await evaluate('window.fixture.calls.filter(c=>c.method==="DELETE").length')===deletes)
  await evaluate('window.fixture.setTeam("team-a");window.fixture.setRole("TEAM_ADMIN")');await waitFor('document.querySelectorAll(".api-grid tbody tr").length===6&&!document.querySelector(".api-results .el-loading-mask")')
  await click('导入');await waitFor('document.querySelector(".api-import-dialog input[type=file]")')
  const temp=await fs.mkdtemp(path.join(os.tmpdir(),'mockhub-ui-import-')),manifest={version:'2.2',apis:[{id:'source-api',teamId:'untrusted',name:'导入用户配置',type:'REST',method:'GET',path:'/api/user/{id}?ignored=1',responseCode:200,contentType:'application/json',responseBody:'{"ok":true}',revision:999}],globalHeaders:[{headerName:'X-Shared',headerValue:'source'}]},jsonFile=path.join(temp,'interfaces.json'),zipFile=path.join(temp,'interfaces.zip')
  await fs.writeFile(jsonFile,JSON.stringify(manifest));await fs.writeFile(zipFile,zipManifest(manifest))
  async function choose(file){const document=await cdp('DOM.getDocument'),node=await cdp('DOM.querySelector',{nodeId:document.root.nodeId,selector:'.api-import-dialog input[type=file]'});await cdp('DOM.setFileInputFiles',{nodeId:node.nodeId,files:[file]})}
  await choose(zipFile);await evaluate('[...document.querySelectorAll(".api-import-dialog .el-radio")].find(e=>e.textContent.includes("覆盖")).click()');await click('预览目标版本');await waitFor('document.querySelector(".import-preview")&&document.body.innerText.includes("ZIP")')
  check('real browser DEFLATE ZIP preview remains supported',await evaluate('document.querySelector(".import-preview").textContent.includes("预计覆盖")'))
  await choose(jsonFile);await click('预览目标版本');await waitFor('document.querySelector(".import-preview")&&document.body.innerText.includes("JSON")')
  const original=await evaluate('window.fixture.state.rows.find(row=>row.id==="api-a").revision');await evaluate('window.fixture.importConflict(true)');const gets=await evaluate('window.fixture.calls.filter(c=>c.method==="GET").length');await click('确认导入并覆盖');await waitFor('document.body.innerText.includes("导入文件仍保留")')
  check('import error preserves selected file and frozen preview',await evaluate('document.querySelector(".import-file").textContent.includes("interfaces.json")&&!!document.querySelector(".import-preview")'))
  check('import conflict does not silently refresh target versions',await evaluate('window.fixture.calls.filter(c=>c.method==="GET").length')===gets)
  check('override expected map uses target revision, never source 999',await evaluate('JSON.parse(window.fixture.calls.filter(c=>c.url==="/apis/import").at(-1).body.get("expectedRevisions"))["api-a"]')===original)
  await evaluate('window.fixture.importConflict(false);window.fixture.bumpTarget()');await click('重新预览目标版本');await waitFor('document.querySelector(".import-preview")&&!document.querySelector(".import-feedback--error")');await click('确认导入并覆盖');await waitFor('document.querySelector(".import-result")?.textContent.includes("导入完成")')
  check('server import warnings remain visible rather than only a success toast',await evaluate('document.querySelector(".import-result").textContent.includes("TEAM_HEADERS_NOT_IMPORTED")'))
  check('new target revision required an explicit new preview and confirmation',await evaluate('JSON.parse(window.fixture.calls.filter(c=>c.url==="/apis/import").at(-1).body.get("expectedRevisions"))["api-a"]')===original+1)
  await click('关闭并返回列表');await waitFor('document.body.innerText.includes("导入用户配置")&&!document.querySelector(".api-import-dialog")?.getBoundingClientRect().width')
  await evaluate('window.fixture.setTeam("team-b")');await waitFor('document.body.innerText.includes("支付对账")');await evaluate('window.fixture.holdList("team-a");window.fixture.setTeam("team-a");window.fixture.setTeam("team-b")');await waitFor('document.body.innerText.includes("支付对账")');await evaluate('window.fixture.releaseLists()')
  check('late old-team list cannot repaint current rows',await evaluate('!document.querySelector(".api-results").textContent.includes("导入用户配置")'))
  await evaluate('window.fixture.setTeam("team-a");window.fixture.longWorkspace()');await waitFor('document.querySelector(".api-results").textContent.includes("LONG_API_")');await cdp('Emulation.setDeviceMetricsOverride',{width:375,height:960,deviceScaleFactor:1,mobile:false});await screenshot('list-long-content-375')
  check('long names paths and actor labels fit mobile cards',await evaluate('document.documentElement.scrollWidth<=innerWidth+1&&[...document.querySelectorAll(".api-card")].every(e=>e.scrollWidth<=e.clientWidth+1)'))
  check('untrusted names render as text, not markup',await evaluate('!document.querySelector("[data-snapshot-injection]")'))
  check('every list or preview read supplies a concrete current team',await evaluate('window.fixture.calls.filter(c=>c.url==="/apis"&&c.method==="GET").every(c=>!!c.params?.teamId)'))
  const reads=await evaluate('window.fixture.calls.filter(c=>c.url==="/apis").length');await evaluate('window.fixture.revokeRead()');await waitFor('document.body.innerText.includes("等待加入团队")');check('empty team makes no global API-list request',await evaluate('window.fixture.calls.filter(c=>c.url==="/apis").length')===reads)
}
