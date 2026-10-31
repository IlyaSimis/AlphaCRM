import { describe, expect, it } from 'vitest'
import type { InventoryItem } from '../../api/types'
import { dateFrom, decodeText, emptyRow, gtinFrom, kindFrom, matchItem, receiptLines, rowProblem, rowsFromTable, splitTable } from './receipt'

const gloves: InventoryItem = {
  id: 'i-1', categoryId: 'c', categoryName: 'Расходники', kind: 'CONSUMABLE', name: 'Перчатки нитриловые', unit: 'пара',
  gtin: '04601234567893', marked: false, markingSystem: 'NONE', rxOnly: false, active: true, stock: 0, minStock: 0,
}

describe('receipt from a file or a paste', () => {
  it('reads a CSV from Russian Excel with a header in any column order', () => {
    const table = splitTable('Количество;Наименование;Срок годности;Серия\r\n10;"Бинт ""стерильный""";31.12.2027;A1\r\n2,5;Спирт;12.2026;\r\n')
    const rows = rowsFromTable(table)
    expect(rows.map((row) => [row.name, row.quantity, row.expiresOn, row.batch])).toEqual([
      ['Бинт "стерильный"', '10', '2027-12-31', 'A1'],
      ['Спирт', '2,5', '2026-12-31', ''],
    ])
  })

  it('reads rows pasted from Excel without a header: name, quantity, unit, batch, expiry, GTIN', () => {
    const rows = rowsFromTable(splitTable('Шприц 5 мл\t100\tшт\tS-7\t2028-01-15\t4601234567893\nМаска\t50\n'))
    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({ name: 'Шприц 5 мл', quantity: '100', unit: 'шт', batch: 'S-7', expiresOn: '2028-01-15', gtin: '04601234567893' })
    expect(rows[1]).toMatchObject({ name: 'Маска', quantity: '50', unit: '' })
  })

  it('does not take a data row that starts with a word like «Препарат» for a header', () => {
    expect(rowsFromTable([['Препарат Х', '5']]).map((row) => row.name)).toEqual(['Препарат Х'])
  })

  it('understands kinds, dates and GTIN in the usual spellings', () => {
    expect(kindFrom('Лекарственные средства')).toBe('DRUG')
    expect(kindFrom('медизделие')).toBe('MEDICAL_DEVICE')
    expect(kindFrom('что-то')).toBe('CONSUMABLE')
    expect(dateFrom('01/02/28')).toBe('2028-02-01')
    expect(dateFrom('31.02.2027')).toBe('')
    expect(dateFrom('46387')).toBe('2026-12-31')
    expect(gtinFrom('4 601234 567893')).toBe('04601234567893')
  })

  it('decodes a Windows-1251 CSV when it is not UTF-8', () => {
    const cp1251 = new Uint8Array([0xc1, 0xe8, 0xed, 0xf2, 0x3b, 0x35]) // «Бинт;5»
    expect(decodeText(cp1251)).toBe('Бинт;5')
    expect(decodeText(new TextEncoder().encode('Бинт;5'))).toBe('Бинт;5')
  })
})

describe('receipt lines', () => {
  it('sends known items by id and new ones by name, skipping the empty row', () => {
    const rows = [
      emptyRow({ name: 'перчатки НИТРИЛОВЫЕ ', quantity: '3' }),
      emptyRow({ name: 'Бахилы', quantity: '0,5', unit: 'уп', kind: 'GOODS', expiresOn: '2027-01-01' }),
      emptyRow(),
    ]
    expect(matchItem(rows[0], [gloves])?.id).toBe('i-1')
    expect(receiptLines(rows, [gloves])).toEqual([
      { itemId: 'i-1', quantity: 3, batch: null, expiresOn: null },
      { name: 'Бахилы', kind: 'GOODS', unit: 'уп', gtin: null, quantity: 0.5, batch: null, expiresOn: '2027-01-01' },
    ])
  })

  it('finds an item by GTIN even under another name and explains bad rows', () => {
    expect(matchItem(emptyRow({ name: 'Перчатки M', gtin: '04601234567893' }), [gloves])?.id).toBe('i-1')
    expect(rowProblem(emptyRow({ name: '', quantity: '1' }))).toBe('Укажите название')
    expect(rowProblem(emptyRow({ name: 'Бинт', quantity: '0' }))).toBe('Количество — число больше нуля')
    expect(rowProblem(emptyRow({ name: 'Бинт', quantity: '1', gtin: '123' }))).toBe('GTIN — 14 цифр')
    expect(rowProblem(emptyRow({ name: 'Бинт', quantity: '1' }))).toBeNull()
  })
})
