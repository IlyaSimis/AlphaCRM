import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loadInventoryMetrics } from '../../api/inventory'
import { InventoryMetricsPanel } from './InventoryMetricsPanel'

vi.mock('../../api/inventory', () => ({ loadInventoryMetrics: vi.fn() }))

const load = vi.mocked(loadInventoryMetrics)

describe('inventory metrics', () => {
  beforeEach(() => { load.mockReset() })

  it('shows posted movements, turnover, days of stock and minimum-stock alerts', async () => {
    load.mockResolvedValue({
      from: '2026-10-02', to: '2026-10-31',
      rows: [{
        itemId: 'i-1', itemName: 'Перчатки', unit: 'пара', stock: 6, minStock: 8,
        received: 10, consumption: 4, writeOff: 0, turnover: 1.33, daysStock: 45,
      }],
    })
    render(<InventoryMetricsPanel />)
    expect(await screen.findByText('Движение и запас за 30 дней')).toBeInTheDocument()
    expect(screen.getByText('ниже минимума: 1')).toBeInTheDocument()
    expect(screen.getByText('Перчатки')).toBeInTheDocument()
    expect(screen.getByText('1,33')).toBeInTheDocument()
    expect(screen.getByText('45 дн.')).toBeInTheDocument()
    expect(screen.getByText('ниже min')).toBeInTheDocument()
    expect(load).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/))
  })
})
