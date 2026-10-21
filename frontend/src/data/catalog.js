// 只决定分类图标；分类名称、ID 和展示顺序统一来自 /api/categories。
export function categoryIcon(name = '') {
  if (/数码|手机|电脑|电器|电子/.test(name)) return 'headphones'
  if (/家居|日用|生活|家装/.test(name)) return 'home'
  if (/办公|文具|图书|书籍/.test(name)) return 'pen'
  if (/服饰|箱包|鞋|穿搭|运动/.test(name)) return 'bag'
  return 'box'
}
