export function formatMoney(value) {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(value ?? ''))
  if (!match) return '—'
  const integer = match[1].replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return integer + '.' + (match[2] || '').padEnd(2, '0')
}
