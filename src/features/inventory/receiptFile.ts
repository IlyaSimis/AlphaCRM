import { isZip, readXlsx } from '../../shared/xlsx'
import { decodeText, rowsFromTable, splitTable, type ReceiptRow } from './receipt'

/** Строки поступления из файла: книга Excel (.xlsx), CSV или текст с табуляциями. */
export async function readTableFile(file: File): Promise<ReceiptRow[]> {
  const data = await file.arrayBuffer()
  const bytes = new Uint8Array(data)
  if (isZip(bytes)) return rowsFromTable(await readXlsx(data))
  // D0 CF 11 E0 — старый двоичный формат Excel 97–2003.
  if (bytes[0] === 0xd0 && bytes[1] === 0xcf && bytes[2] === 0x11 && bytes[3] === 0xe0) {
    throw new Error('Старый формат .xls не читается: сохраните книгу как .xlsx или .csv')
  }
  return rowsFromTable(splitTable(decodeText(bytes)))
}
