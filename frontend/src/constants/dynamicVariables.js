/**
 * 动态变量元数据
 *
 * 与后端 DynamicVariableUtil.java 中支持的内置变量保持一致。
 * 新增/修改变量时需同步两侧。路径参数 {{path.xxx}} 由用户手动书写，不在此列表。
 */
export const DYNAMIC_VARIABLES = [
  { name: 'timestamp',  desc: '当前毫秒时间戳' },
  { name: 'uuid',       desc: '随机 UUID' },
  { name: 'date',       desc: '当前日期 yyyy-MM-dd' },
  { name: 'datetime',   desc: '当前日期时间 yyyy-MM-dd HH:mm:ss' },
  { name: 'random_int', desc: '0~10000 随机整数' }
]
