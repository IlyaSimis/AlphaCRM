import type { InventoryItem, ItemKind } from '../../api/types'
import { parseQuantity } from './inventory'

/** Строка поступления в диалоге: позиция из номенклатуры или новая — по названию. */
export type ReceiptRow = {
  key: string
  name: string
  quantity: string
  unit: string
  kind: ItemKind
  batch: string
  expiresOn: string
  gtin: string
}

let counter = 0
export const emptyRow = (patch: Partial<ReceiptRow> = {}): ReceiptRow => ({ key: `row-${++counter}`, name: '', quantity: '', unit: '', kind: 'CONSUMABLE', batch: '', expiresOn: '', gtin: '', ...patch })

/** Строка пустая — её не отправляем и не считаем ошибкой. */
export const isBlank = (row: ReceiptRow) => !row.name.trim() && !row.quantity.trim() && !row.batch.trim() && !row.expiresOn.trim() && !row.gtin.trim()

/** Позиция номенклатуры с тем же GTIN или названием (без учёта регистра) — как ищет сервер. */
export function matchItem(row: Pick<ReceiptRow, 'name' | 'gtin'>, items: InventoryItem[]): InventoryItem | null {
  const gtin = row.gtin.trim()
  if (gtin) {
    const byGtin = items.find((item) => item.gtin === gtin)
    if (byGtin) return byGtin
  }
  const name = row.name.trim().toLocaleLowerCase('ru-RU')
  if (!name) return null
  const same = items.filter((item) => item.name.trim().toLocaleLowerCase('ru-RU') === name)
  return same.find((item) => item.active) ?? same[0] ?? null
}

/** Что не так со строкой; null — строку можно провести. */
export function rowProblem(row: ReceiptRow): string | null {
  if (!row.name.trim()) return 'Укажите название'
  if (parseQuantity(row.quantity) === null) return 'Количество — число больше нуля'
  if (row.gtin.trim() && !/^\d{14}$/.test(row.gtin.trim())) return 'GTIN — 14 цифр'
  if (row.expiresOn && !/^\d{4}-\d{2}-\d{2}$/.test(row.expiresOn)) return 'Срок годности — дата'
  return null
}

/** Тело запроса быстрого прихода: известные позиции — по id, новые — по названию. */
export function receiptLines(rows: ReceiptRow[], items: InventoryItem[]) {
  return rows.filter((row) => !isBlank(row)).map((row) => {
    const item = matchItem(row, items)
    const common = { quantity: parseQuantity(row.quantity) ?? 0, batch: row.batch.trim() || null, expiresOn: row.expiresOn || null }
    return item
      ? { itemId: item.id, ...common }
      : { name: row.name.trim(), kind: row.kind, unit: row.unit.trim() || null, gtin: row.gtin.trim() || null, ...common }
  })
}

// ---------- таблица из файла или из буфера обмена ----------

type Column = 'name' | 'quantity' | 'unit' | 'kind' | 'batch' | 'expiresOn' | 'gtin'

/** Заголовки колонок, которые узнаём (нижний регистр, начало слова). */
const headers: Array<[Column, RegExp]> = [
  ['name', /^(наименование|название|номенклатура|товар|позиция|препарат|name|item|product)/],
  ['quantity', /^(кол|количество|qty|quantity|число|штук)/],
  ['unit', /^(ед|единица|unit|uom)/],
  ['kind', /^(вид|тип|группа|kind|type)/],
  ['batch', /^(партия|серия|лот|batch|lot|series)/],
  ['expiresOn', /^(срок|годен|годность|exp|expires|best before)/],
  ['gtin', /^(gtin|штрихкод|штрих-код|ean|barcode|код товара)/],
]

/** Порядок колонок, если заголовка нет: так проще всего набрать в Excel. */
const defaultOrder: Column[] = ['name', 'quantity', 'unit', 'batch', 'expiresOn', 'gtin']

const kinds: Array<[ItemKind, RegExp]> = [
  ['DRUG', /^(лек|препарат|drug|медикамент)/],
  ['MEDICAL_DEVICE', /^(мед.*изд|изделие|device|медизделие)/],
  ['CONSUMABLE', /^(расход|consum)/],
  ['GOODS', /^(товар|goods|прочее)/],
]

/** Вид позиции из текста ячейки; не узнали — расходники. */
export function kindFrom(text: string): ItemKind {
  const value = text.trim().toLocaleLowerCase('ru-RU')
  return kinds.find(([, pattern]) => pattern.test(value))?.[0] ?? 'CONSUMABLE'
}

/** Дата из ячейки: 31.12.2027, 31/12/27, 2027-12-31 или число дней Excel. Не дата — ''. */
export function dateFrom(text: string): string {
  const value = text.trim()
  if (!value) return ''
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(value)
  if (iso) return valid(Number(iso[1]), Number(iso[2]), Number(iso[3]))
  const ru = /^(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})$/.exec(value)
  if (ru) {
    const year = Number(ru[3]) < 100 ? 2000 + Number(ru[3]) : Number(ru[3])
    return valid(year, Number(ru[2]), Number(ru[1]))
  }
  const month = /^(\d{1,2})[./](\d{4})$/.exec(value)
  if (month) {
    // «12.2027» — годен до конца месяца.
    const year = Number(month[2])
    const monthNumber = Number(month[1])
    const last = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()
    return valid(year, monthNumber, last)
  }
  if (/^\d{5}(\.\d+)?$/.test(value)) {
    // Excel хранит дату как число дней от 30.12.1899.
    const date = new Date(Date.UTC(1899, 11, 30) + Math.floor(Number(value)) * 86_400_000)
    return date.toISOString().slice(0, 10)
  }
  return ''
}

function valid(year: number, month: number, day: number) {
  const date = new Date(Date.UTC(year, month - 1, day))
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return ''
  return date.toISOString().slice(0, 10)
}

/** Разбор текста CSV или вставки из Excel в ячейки; кавычки по правилам CSV. */
export function splitTable(text: string): string[][] {
  const clean = text.replace(/^﻿/, '').replace(/\r\n?/g, '\n')
  const firstLine = clean.split('\n').find((line) => line.trim()) ?? ''
  const delimiter = firstLine.includes('\t') ? '\t' : firstLine.includes(';') ? ';' : ','
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false
  for (let index = 0; index < clean.length; index++) {
    const char = clean[index]
    if (quoted) {
      if (char === '"' && clean[index + 1] === '"') { cell += '"'; index++ }
      else if (char === '"') quoted = false
      else cell += char
    } else if (char === '"' && cell === '') quoted = true
    else if (char === delimiter) { row.push(cell); cell = '' }
    else if (char === '\n') { row.push(cell); rows.push(row); row = []; cell = '' }
    else cell += char
  }
  if (cell !== '' || row.length > 0) { row.push(cell); rows.push(row) }
  return rows.map((cells) => cells.map((value) => value.trim())).filter((cells) => cells.some((value) => value !== ''))
}

/**
 * Строки поступления из таблицы. Первая строка с узнаваемыми заголовками задаёт колонки; без
 * заголовков колонки идут так: название, количество, единица, партия, срок годности, GTIN.
 */
export function rowsFromTable(table: string[][]): ReceiptRow[] {
  if (table.length === 0) return []
  const first = table[0].map((value) => value.toLocaleLowerCase('ru-RU'))
  const mapped = first.map((value) => headers.find(([, pattern]) => pattern.test(value))?.[0] ?? null)
  // Заголовок — две узнанные колонки или одна без чисел в строке: «Препарат Х; 5» — уже данные.
  const recognized = mapped.filter(Boolean).length
  const numeric = table[0].some((value) => parseQuantity(value) !== null)
  const hasHeader = recognized >= 2 || (recognized === 1 && !numeric)
  const columns: Array<Column | null> = hasHeader ? mapped : defaultOrder
  const body = hasHeader ? table.slice(1) : table
  return body.map((cells) => {
    const value = (column: Column) => {
      const index = columns.indexOf(column)
      return index >= 0 ? cells[index] ?? '' : ''
    }
    return emptyRow({
      name: value('name'),
      quantity: quantityFrom(value('quantity')),
      unit: value('unit'),
      kind: kindFrom(value('kind')),
      batch: value('batch'),
      expiresOn: dateFrom(value('expiresOn')),
      gtin: gtinFrom(value('gtin')),
    })
  }).filter((row) => !isBlank(row))
}

/** Количество из ячейки: без пробелов; хвост двоичной дроби Excel (2.4999999999999996) округляется. */
export function quantityFrom(text: string) {
  const value = text.replace(/\s/g, '')
  return /^\d+\.\d{4,}$/.test(value) ? String(Number(Number(value).toFixed(3))) : value
}

/** GTIN из ячейки: только цифры; EAN-13 дополняется нулём слева до 14 знаков. */
export function gtinFrom(text: string) {
  const digits = text.replace(/\D/g, '')
  return digits.length === 13 ? `0${digits}` : digits
}

/** Текст файла: UTF-8, а если в нём не UTF-8 (CSV из русского Excel) — Windows-1251. */
export function decodeText(bytes: Uint8Array): string {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return new TextDecoder('windows-1251').decode(bytes)
  }
}
