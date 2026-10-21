// 造数脚本追加的七位序号不作为面向用户的商品名称展示。
export function customerProductName(product) {
  return String(product?.name ?? '').replace(/\s+\d{7}$/, '')
}
