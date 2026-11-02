export function formatMoney(value) {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(value ?? ''))
  if (!match) return '—'
  const integer = match[1].replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return integer + '.' + (match[2] || '').padEnd(2, '0')
}

export function normalizeProductPrice(value) {
  const text = String(value ?? '').trim()
  if (!/^\d{1,10}(?:\.\d{1,2})?$/.test(text) || !/[1-9]/.test(text)) {
    throw new Error('价格须大于 0，最多 10 位整数和 2 位小数')
  }
  const [integer, fraction = ''] = text.split('.')
  return integer.replace(/^0+(?=\d)/, '') + '.' + fraction.padEnd(2, '0')
}
