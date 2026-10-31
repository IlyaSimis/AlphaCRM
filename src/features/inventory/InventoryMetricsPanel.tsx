import { useEffect, useState } from 'react'
import { Alert, Box, Chip, Typography } from '@mui/material'
import { loadInventoryMetrics } from '../../api/inventory'
import type { StockMetrics } from '../../api/types'
import { localToday, shiftDate, shortDate } from '../../shared/dates'
import { formatQuantity } from './inventory'

function decimal(value: number | null) {
  return value === null ? '—' : formatQuantity(value)
}

/** Движение по проведённым накладным, агрегированное по всем складам клиники. */
export function InventoryMetricsPanel() {
  const [metrics, setMetrics] = useState<StockMetrics | null>(null)
  const [error, setError] = useState('')
  const to = localToday()
  const from = shiftDate(to, -29)

  useEffect(() => {
    let cancelled = false
    loadInventoryMetrics(from, to)
      .then((value) => { if (!cancelled) setMetrics(value) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [from, to])

  const lowStock = (metrics?.rows ?? []).filter((row) => row.stock < row.minStock).length
  return (
    <Box sx={{ mt: 3 }}>
      <Box className="chart-head" sx={{ mb: 1.5 }}>
        <Box>
          <Typography sx={{ fontWeight: 750 }}>Движение и запас за 30 дней</Typography>
          <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{shortDate(from)}–{shortDate(to)} · по всем складам, только проведённые накладные</Typography>
        </Box>
        {metrics && <Chip size="small" color={lowStock ? 'error' : 'success'} variant="outlined" label={`ниже минимума: ${lowStock}`} />}
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {metrics && metrics.rows.length === 0 && <Typography color="text.secondary">Номенклатура пока пуста.</Typography>}
      {metrics && metrics.rows.length > 0 && <table className="audit-table">
        <thead><tr><th>Позиция</th><th>Остаток / min</th><th>Приход</th><th>Расход</th><th>Списания</th><th>Оборот</th><th>Дней запаса</th></tr></thead>
        <tbody>{metrics.rows.map((row) => <tr key={row.itemId}>
          <td>{row.itemName}</td>
          <td className="nowrap">{formatQuantity(row.stock)} / {formatQuantity(row.minStock)} {row.unit}{row.stock < row.minStock && <Chip size="small" color="error" variant="outlined" label="ниже min" sx={{ ml: 1 }} />}</td>
          <td>{formatQuantity(row.received)}</td>
          <td>{formatQuantity(row.consumption)}</td>
          <td>{formatQuantity(row.writeOff)}</td>
          <td>{decimal(row.turnover)}</td>
          <td>{row.daysStock === null ? '—' : `${decimal(row.daysStock)} дн.`}</td>
        </tr>)}</tbody>
      </table>}
      <Typography sx={{ mt: 1, fontSize: 12, color: 'text.secondary' }}>Оборот = (расход + списания) / средний остаток. Дни запаса = текущий остаток / среднее списание за день.</Typography>
    </Box>
  )
}
