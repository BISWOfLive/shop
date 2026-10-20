export const sampleCategories = [
  { id: 'digital', name: '数码配件' },
  { id: 'home', name: '家居日用' },
  { id: 'office', name: '办公文具' },
  { id: 'style', name: '服饰箱包' },
]

// 首页无在售商品或服务不可用时展示，始终在页面中标明为示例商品。
export const sampleProducts = [
  { id: 'sample-1', name: '轻享无线头戴式耳机 柔软耳罩 舒适聆听', categoryId: 'digital', categoryName: '数码配件', price: 299, sampleImageIndex: 0, description: '奶油白外观，柔软包耳设计，为日常通勤与桌面时光增添一点好心情。此商品为首页设计示例。' },
  { id: 'sample-2', name: '随行真空保温杯 简约便携 清新鼠尾草绿', categoryId: 'home', categoryName: '家居日用', price: 89, sampleImageIndex: 1, description: '把喜欢的饮品装进日常，简约配色搭配轻便杯身。此商品为首页设计示例。' },
  { id: 'sample-3', name: '简约桌面阅读台灯 点亮你的专注时光', categoryId: 'home', categoryName: '家居日用', price: 159, sampleImageIndex: 2, description: '圆润灯罩与纤细灯杆，为书桌搭配一盏温柔的灯。此商品为首页设计示例。' },
  { id: 'sample-4', name: '轻巧机械键盘 清透浅蓝 桌面灵感好搭档', categoryId: 'digital', categoryName: '数码配件', price: 239, sampleImageIndex: 3, description: '用清爽的配色整理桌面心情，让每一次敲击都成为工作的小仪式。此商品为首页设计示例。' },
  { id: 'sample-5', name: '日常通勤帆布托特包 轻装出门 自在随行', categoryId: 'style', categoryName: '服饰箱包', price: 79, sampleImageIndex: 4, description: '米白帆布搭配棕色提手，装下出门需要的小物件。此商品为首页设计示例。' },
  { id: 'sample-6', name: '轻盈休闲小白鞋 百搭配色 日常出行', categoryId: 'style', categoryName: '服饰箱包', price: 199, sampleImageIndex: 5, description: '一双适合日常穿搭的白色休闲鞋，为出门的每一步增添轻快。此商品为首页设计示例。' },
  { id: 'sample-7', name: '简约无线鼠标 轻巧握感 释放桌面空间', categoryId: 'digital', categoryName: '数码配件', price: 69, sampleImageIndex: 6, description: '干净的白色外观与简洁轮廓，搭配你的日常办公桌面。此商品为首页设计示例。' },
  { id: 'sample-8', name: '布面手账笔记本 记录灵感与日常小事', categoryId: 'office', categoryName: '办公文具', price: 29.9, sampleImageIndex: 7, description: '自然色布面封皮，把计划、灵感和生活片段留在纸上。此商品为首页设计示例。' },
  { id: 'sample-9', name: '布艺桌面收纳盒 小物归位 日常整整齐齐', categoryId: 'home', categoryName: '家居日用', price: 39.9, sampleImageIndex: 8, description: '温暖的米色布艺，收纳桌面杂物，留出更舒展的生活空间。此商品为首页设计示例。' },
].map(product => ({ ...product, status: 1, isSample: true }))

export function categoryPresentation(name = '') {
  if (/数码|手机|电脑|电器/.test(name)) return { icon: 'headphones', detail: '耳机 / 键鼠 / 数码好物', note: '给生活加点灵感' }
  if (/家居|日用|生活/.test(name)) return { icon: 'home', detail: '水杯 / 灯具 / 桌面收纳', note: '把日常过成喜欢的样子' }
  if (/办公|文具|图书/.test(name)) return { icon: 'pen', detail: '手账 / 书写 / 办公好物', note: '让专注更有仪式感' }
  if (/服饰|箱包|鞋|穿搭/.test(name)) return { icon: 'bag', detail: '通勤包 / 鞋履 / 日常穿搭', note: '出门就有好心情' }
  return { icon: 'box', detail: '发现更多精选商品', note: '总有一件合你心意' }
}
