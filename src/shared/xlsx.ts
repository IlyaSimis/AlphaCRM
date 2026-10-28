/**
 * Первый лист книги Excel (.xlsx) как таблица строк — без сторонних библиотек: zip-архив читаем
 * сами, deflate распаковывает браузер (DecompressionStream), XML разбирает DOMParser.
 * Старый формат .xls (не zip) не поддерживается — его сохраняют как .xlsx или .csv.
 */
export async function readXlsx(data: ArrayBuffer): Promise<string[][]> {
  const files = zipEntries(new DataView(data))
  const text = async (name: string) => {
    const entry = files.get(name)
    return entry ? new TextDecoder('utf-8').decode(await inflate(data, entry)) : null
  }
  const sheetPath = await firstSheetPath(text)
  const sheet = await text(sheetPath)
  if (!sheet) throw new Error('В книге нет листов')
  const shared = parseSharedStrings(await text('xl/sharedStrings.xml'))
  return parseSheet(sheet, shared)
}

/** Это zip (xlsx), а не старый .xls или текст. */
export const isZip = (bytes: Uint8Array) => bytes.length > 4 && bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04

type Entry = { method: number; compressedSize: number; localOffset: number }

function zipEntries(view: DataView): Map<string, Entry> {
  // Конец центрального каталога: подпись 0x06054b50, ищем с конца (после неё — комментарий до 64 КБ).
  let end = -1
  for (let offset = view.byteLength - 22; offset >= Math.max(0, view.byteLength - 65_557); offset--) {
    if (view.getUint32(offset, true) === 0x06054b50) { end = offset; break }
  }
  if (end < 0) throw new Error('Файл не похож на книгу Excel (.xlsx)')
  const count = view.getUint16(end + 10, true)
  let offset = view.getUint32(end + 16, true)
  const entries = new Map<string, Entry>()
  const decoder = new TextDecoder('utf-8')
  for (let index = 0; index < count; index++) {
    if (view.getUint32(offset, true) !== 0x02014b50) break
    const method = view.getUint16(offset + 10, true)
    const compressedSize = view.getUint32(offset + 20, true)
    const nameLength = view.getUint16(offset + 28, true)
    const extraLength = view.getUint16(offset + 30, true)
    const commentLength = view.getUint16(offset + 32, true)
    const localOffset = view.getUint32(offset + 42, true)
    const name = decoder.decode(new Uint8Array(view.buffer, view.byteOffset + offset + 46, nameLength))
    entries.set(name, { method, compressedSize, localOffset })
    offset += 46 + nameLength + extraLength + commentLength
  }
  return entries
}

async function inflate(data: ArrayBuffer, entry: Entry): Promise<ArrayBuffer> {
  const view = new DataView(data)
  const start = entry.localOffset + 30 + view.getUint16(entry.localOffset + 26, true) + view.getUint16(entry.localOffset + 28, true)
  const bytes = new Uint8Array(data, start, entry.compressedSize)
  if (entry.method === 0) return bytes.slice().buffer
  if (entry.method !== 8) throw new Error('Неизвестное сжатие в книге Excel')
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'))
  return new Response(stream).arrayBuffer()
}

const xml = (text: string) => new DOMParser().parseFromString(text, 'application/xml')

/** Путь первого листа: workbook.xml → id связи → путь в workbook.xml.rels. */
async function firstSheetPath(text: (name: string) => Promise<string | null>) {
  const fallback = 'xl/worksheets/sheet1.xml'
  const workbook = await text('xl/workbook.xml')
  const rels = await text('xl/_rels/workbook.xml.rels')
  if (!workbook || !rels) return fallback
  const sheet = xml(workbook).getElementsByTagName('sheet')[0]
  const id = sheet?.getAttribute('r:id') ?? sheet?.getAttributeNS('http://schemas.openxmlformats.org/officeDocument/2006/relationships', 'id')
  const relation = Array.from(xml(rels).getElementsByTagName('Relationship')).find((item) => item.getAttribute('Id') === id)
  const target = relation?.getAttribute('Target')
  if (!target) return fallback
  return target.startsWith('/') ? target.slice(1) : `xl/${target.replace(/^\.\//, '')}`
}

export function parseSharedStrings(text: string | null): string[] {
  if (!text) return []
  return Array.from(xml(text).getElementsByTagName('si')).map((item) => Array.from(item.getElementsByTagName('t')).map((node) => node.textContent ?? '').join(''))
}

/** Номер колонки из ссылки ячейки: A1 → 0, AB7 → 27. */
export function columnIndex(reference: string) {
  const letters = /^[A-Z]+/.exec(reference)?.[0] ?? 'A'
  return letters.split('').reduce((total, letter) => total * 26 + letter.charCodeAt(0) - 64, 0) - 1
}

export function parseSheet(text: string, shared: string[]): string[][] {
  const rows: string[][] = []
  for (const row of Array.from(xml(text).getElementsByTagName('row'))) {
    const cells: string[] = []
    let next = 0
    for (const cell of Array.from(row.getElementsByTagName('c'))) {
      const reference = cell.getAttribute('r')
      const index = reference ? columnIndex(reference) : next
      const type = cell.getAttribute('t')
      const raw = cell.getElementsByTagName('v')[0]?.textContent ?? ''
      const value = type === 's'
        ? shared[Number(raw)] ?? ''
        : type === 'inlineStr'
          ? Array.from(cell.getElementsByTagName('t')).map((node) => node.textContent ?? '').join('')
          : type === 'b' ? (raw === '1' ? 'TRUE' : 'FALSE') : raw
      cells[index] = value.trim()
      next = index + 1
    }
    const filled = Array.from(cells, (value) => value ?? '')
    if (filled.some((value) => value !== '')) rows.push(filled)
  }
  return rows
}
