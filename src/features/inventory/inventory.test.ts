import { describe, expect, it } from 'vitest'
import type { StockBalance } from '../../api/types'
import { balanceAlert, formatQuantity, parseQuantity, warehousesOf } from './inventory'

const row = (quantity: number, expiresOn: string | null): StockBalance => ({
  itemId: 'i', itemName: 'Перчатки', categoryId: 'c', categoryName: 'Расходники', kind: 'CONSUMABLE', unit: 'пара',
  marked: false, warehouseId: 'w', warehouseName: 'Основной склад', batch: '', expiresOn, quantity, codesInStock: 0,
})

describe('inventory rules', () => {
  it('reads quantities with a comma and rejects zero or garbage', () => {
    expect(parseQuantity('2')).toBe(2)
    expect(parseQuantity('0,5')).toBe(0.5)
    expect(parseQuantity('0')).toBeNull()
    expect(parseQuantity('abc')).toBeNull()
    expect(formatQuantity(1.25)).toBe('1,25')
    expect(formatQuantity(3)).toBe('3')
  })

  it('asks for the right warehouses per document type', () => {
    expect(warehousesOf('RECEIPT')).toEqual({ from: false, to: true })
    expect(warehousesOf('CONSUMPTION')).toEqual({ from: true, to: false })
    expect(warehousesOf('TRANSFER')).toEqual({ from: true, to: true })
  })

  it('flags negative stock and batches expiring within 30 days', () => {
    expect(balanceAlert(row(-1, null), '2026-10-28')).toBe('negative')
    expect(balanceAlert(row(5, '2026-11-20'), '2026-10-28')).toBe('expiring')
    expect(balanceAlert(row(5, '2027-05-01'), '2026-10-28')).toBeNull()
  })
})
