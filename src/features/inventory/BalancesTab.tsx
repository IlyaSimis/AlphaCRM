import { useEffect, useState } from 'react'
import { AddRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadBalances, loadCategories, loadWarehouses } from '../../api/inventory'
import type { InventoryCategory, StockBalance, Warehouse } from '../../api/types'
import { localToday, shortDate } from '../../shared/dates'
import { balanceAlert, formatQuantity, kindLabels } from './inventory'

/** Остатки по складам и партиям; минус и истекающий срок — с пометкой. */
export function BalancesTab({ onReceive }: { onReceive?: () => void }) {
  const [rows, setRows] = useState<StockBalance[] | null>(null)
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [categories, setCategories] = useState<InventoryCategory[]>([])
  const [warehouse, setWarehouse] = useState('')
  const [category, setCategory] = useState('')
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const today = localToday()

  useEffect(() => {
    Promise.all([loadWarehouses(), loadCategories()])
      .then(([stores, groups]) => { setWarehouses(stores); setCategories(groups) })
      .catch((reason: Error) => setError(reason.message))
  }, [])

  useEffect(() => {
    let cancelled = false
    loadBalances({ warehouse, category, q: query.trim() })
      .then((items) => { if (!cancelled) { setRows(items); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [warehouse, category, query])

  return (
    <Box className="panel">
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField size="small" select label="Склад" value={warehouse} onChange={(event) => setWarehouse(event.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="">Все склады</MenuItem>
          {warehouses.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>
        <TextField size="small" select label="Категория" value={category} onChange={(event) => setCategory(event.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="">Все категории</MenuItem>
          {categories.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>
        <TextField size="small" label="Поиск по названию" value={query} onChange={(event) => setQuery(event.target.value)} />
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {rows === null && !error
        ? <Box className="empty" sx={{ minHeight: 120 }}><CircularProgress aria-label="Загрузка остатков" /></Box>
        : rows && rows.length === 0
          ? <Box className="empty" sx={{ minHeight: 160, flexDirection: 'column', gap: 1.5 }}>
            <Typography sx={{ color: 'text.secondary' }}>{query || warehouse || category ? 'Ничего не нашлось.' : 'На складе пока пусто.'}</Typography>
            {onReceive && !query && !warehouse && !category && <Button variant="contained" startIcon={<AddRounded />} onClick={onReceive}>Добавить первое поступление</Button>}
          </Box>
          : <table className="audit-table">
            <thead><tr><th>Позиция</th><th>Категория</th><th>Склад</th><th>Партия</th><th>Годен до</th><th>Остаток</th><th>Марок</th></tr></thead>
            <tbody>
              {(rows ?? []).map((row) => {
                const alert = balanceAlert(row, today)
                return (
                  <tr key={`${row.itemId}-${row.warehouseId}-${row.batch}`}>
                    <td>{row.itemName}</td>
                    <td>{row.categoryName} · {kindLabels[row.kind] ?? row.kind}</td>
                    <td>{row.warehouseName}</td>
                    <td>{row.batch || '—'}</td>
                    <td className="nowrap">{row.expiresOn ? shortDate(row.expiresOn) : '—'}{alert === 'expiring' && <Chip size="small" color="warning" variant="outlined" label="истекает" sx={{ ml: 1 }} />}</td>
                    <td className="nowrap">{formatQuantity(row.quantity)} {row.unit}{alert === 'negative' && <Chip size="small" color="error" variant="outlined" label="нехватка" sx={{ ml: 1 }} />}</td>
                    <td>{row.marked ? row.codesInStock : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>}
    </Box>
  )
}
