// Maps backend netting failure codes to operator-facing repair guidance.
const ADVICE = {
  SUSPENDED_MEMBER: '存在被停用(SUSPENDED)的会员。请到「会员」页重新启用相关会员后重试。',
  NO_OBLIGATIONS: '该交割日/币种下没有 OPEN 义务。请确认日期与币种,或到「义务」页录入义务后重试。',
  MIXED_CURRENCY: '待轧差义务中混入了不同币种。请修正义务数据,确保同一批次只含单一币种。',
  INVALID_AMOUNT: '存在金额无效(为空或小于等于 0)的义务。请修正义务金额后重试。',
  MEMBER_NOT_FOUND: '义务引用了不存在的会员。请补齐会员或修正义务后重试。',
  CONSERVATION_BROKEN: '净额守恒校验失败(ΣnetAmount ≠ 0),义务数据不平,请核对后重试。',
  NETTING_FAILED: '轧差过程发生系统异常,请联系管理员排查后重试。'
}

export function failureAdvice(code) {
  return ADVICE[code] || '请根据失败原因检查相关数据,修复后重试。'
}
