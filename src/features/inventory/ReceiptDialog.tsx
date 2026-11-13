import { type ClipboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import { AddRounded, DeleteOutlineRounded, UploadFileRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material'
import { loadItems, loadWarehouses, receiveStock, type ReceiptResult } from '../../api/inventory'
import type { InventoryItem, ItemKind, Warehouse } from '../../api/types'
import { localToday } from '../../shared/dates'
import { kindLabels } from './inventory'
import { emptyRow, isBlank, matchItem, receiptLines, rowProblem, rowsFromTable, splitTable, type ReceiptRow } from './receipt'
import { readTableFile } from './receiptFile'

type ReceiptDialogProps = {
  /** Строки из загруженного файла; без них — одна пустая строка. */
  initialRows?: ReceiptRow[]
  /** Имя загруженного файла — для подсказки над таблицей. */
  fileName?: string
  onClose: () => void
  onDone: (result: ReceiptResult) => void
}

/**
 * Поступление на склад в одном окне: строки можно набрать, вставить из Excel (Ctrl+V) или
 * загрузить файлом. Позиции, которых нет в номенклатуре, заводятся сами; накладная сразу
 * проводится.
 */
export function ReceiptDialog({ initialRows, fileName, onClose, onDone }: ReceiptDialogProps) {
  const [rows, setRows] = useState<ReceiptRow[]>(() => (initialRows && initialRows.length > 0 ? [...initialRows, emptyRow()] : [emptyRow()]))
  const [items, setItems] = useState<InventoryItem[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [warehouseId, setWarehouseId] = useState('')
  const [supplier, setSupplier] = useState('')
  const [date, setDate] = useState(localToday())
  const [source, setSource] = useState(fileName ?? '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [tried, setTried] = useState(false)
  const key = useRef(crypto.randomUUID())
  const file = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    Promise.all([loadItems({ sort: 'name' }), loadWarehouses()])
      .then(([catalog, stores]) => {
        setItems(catalog.filter((item) => item.active))
        setWarehouses(stores)
        setWarehouseId((stores.find((store) => store.isDefault) ?? stores[0])?.id ?? '')
      })
      .catch((reason: Error) => setError(reason.message))
  }, [])

  const filled = rows.filter((row) => !isBlank(row))
  const fresh = useMemo(() => filled.filter((row) => row.name.trim() && !matchItem(row, items)), [filled, items])
  const problems = filled.map(rowProblem).filter(Boolean)

  const patch = (rowKey: string, change: Partial<ReceiptRow>) => setRows((current) => {
    const next = current.map((row) => (row.key === rowKey ? { ...row, ...change } : row))
    // Последняя строка заполнена — снизу всегда есть пустая для следующей позиции.
    return isBlank(next[next.length - 1]) ? next : [...next, emptyRow()]
  })

  const remove = (rowKey: string) => setRows((current) => {
    const next = current.filter((row) => row.key !== rowKey)
    return next.length === 0 || !isBlank(next[next.length - 1]) ? [...next, emptyRow()] : next
  })

  /** Вставка нескольких строк из Excel или таблицы — сразу строками поступления. */
  const paste = (rowKey: string, text: string) => {
    if (!text.includes('\n') && !text.includes('\t')) return false
    const parsed = rowsFromTable(splitTable(text))
    if (parsed.length === 0) return false
    setRows((current) => {
      const index = current.findIndex((row) => row.key === rowKey)
      const before = current.slice(0, index).filter((row) => !isBlank(row))
      const after = current.slice(index + 1).filter((row) => !isBlank(row))
      return [...before, ...parsed, ...after, emptyRow()]
    })
    return true
  }

  const loadFile = async (picked: File | undefined) => {
    if (!picked) return
    try {
      const parsed = await readTableFile(picked)
      if (parsed.length === 0) {
        setError('В файле не нашлось строк с позициями')
        return
      }
      setRows((current) => [...current.filter((row) => !isBlank(row)), ...parsed, emptyRow()])
      setSource(picked.name)
      setError('')
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const submit = async () => {
    setTried(true)
    if (filled.length === 0) {
      setError('Добавьте хотя бы одну позицию')
      return
    }
    if (problems.length > 0) {
      setError('Проверьте строки, отмеченные красным')
      return
    }
    setBusy(true)
    setError('')
    try {
      onDone(await receiveStock({ warehouseId: warehouseId || null, supplier: supplier.trim() || null, documentDate: date || null, idempotencyKey: key.current, lines: receiptLines(rows, items) }))
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const unitOf = (row: ReceiptRow) => matchItem(row, items)?.unit

  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>Поступление на склад</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
            {warehouses.length > 1 && <TextField size="small" select label="Склад" value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)} sx={{ minWidth: 200 }}>
              {warehouses.map((store) => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}
            </TextField>}
            <TextField size="small" label="Поставщик" value={supplier} onChange={(event) => setSupplier(event.target.value)} sx={{ flex: 1 }} />
            <TextField size="small" type="date" label="Дата" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <Button variant="outlined" startIcon={<UploadFileRounded />} onClick={() => file.current?.click()}>Загрузить файл</Button>
            <input ref={file} type="file" hidden accept=".xlsx,.csv,.txt,.tsv" aria-label="Файл поступления" onChange={(event) => { void loadFile(event.target.files?.[0]); event.target.value = '' }} />
          </Stack>
          <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>
            {source ? `Загружено из «${source}». ` : ''}Начните вводить название — подскажем позиции из номенклатуры; новые позиции заведутся сами. Можно вставить строки из Excel (Ctrl+V) или загрузить .xlsx / .csv с колонками: название, количество, единица, партия, срок годности, GTIN.
          </Typography>
          {error && <Alert severity="error" onClose={() => setError('')}>{error}</Alert>}
          {/* Подсказки названий — встроенный список браузера: позиции номенклатуры. */}
          <datalist id="receipt-items">{items.map((item) => <option key={item.id} value={item.name} />)}</datalist>
          <Box className="receipt-grid" role="table" aria-label="Строки поступления">
            <Box className="receipt-row receipt-head" role="row">
              <span role="columnheader">Наименование</span><span role="columnheader">Количество</span><span role="columnheader">Ед.</span><span role="columnheader">Вид (для новой)</span><span role="columnheader">Партия</span><span role="columnheader">Годен до</span><span role="columnheader" />
            </Box>
            {rows.map((row, index) => {
              const item = matchItem(row, items)
              const problem = tried && !isBlank(row) ? rowProblem(row) : null
              const last = index === rows.length - 1
              return (
                <Box key={row.key} className="receipt-row" role="row">
                  <TextField size="small" value={row.name} placeholder={last ? 'Новая позиция…' : ''} error={Boolean(problem)} helperText={problem ?? undefined} onChange={(event) => patch(row.key, { name: event.target.value })} slotProps={{ htmlInput: { list: 'receipt-items', autoComplete: 'off', 'aria-label': `Наименование, строка ${index + 1}`, onPaste: (event: ClipboardEvent<HTMLInputElement>) => { if (paste(row.key, event.clipboardData.getData('text'))) event.preventDefault() } } }} />
                  <TextField size="small" value={row.quantity} onChange={(event) => patch(row.key, { quantity: event.target.value })} slotProps={{ htmlInput: { inputMode: 'decimal', 'aria-label': `Количество, строка ${index + 1}` } }} />
                  <TextField size="small" value={item ? item.unit : row.unit} placeholder={unitOf(row) ?? 'шт'} disabled={Boolean(item)} onChange={(event) => patch(row.key, { unit: event.target.value })} slotProps={{ htmlInput: { 'aria-label': `Единица, строка ${index + 1}` } }} />
                  {item || !row.name.trim()
                    ? <Box sx={{ display: 'flex', alignItems: 'center', minWidth: 0 }}>{item && <Chip size="small" variant="outlined" label={item.marked ? 'маркируемая' : 'в номенклатуре'} />}</Box>
                    : <TextField size="small" select value={row.kind} onChange={(event) => patch(row.key, { kind: event.target.value as ItemKind })} slotProps={{ htmlInput: { 'aria-label': `Вид, строка ${index + 1}` } }}>
                      {(Object.keys(kindLabels) as ItemKind[]).map((kind) => <MenuItem key={kind} value={kind}>{kindLabels[kind]}</MenuItem>)}
                    </TextField>}
                  <TextField size="small" value={row.batch} onChange={(event) => patch(row.key, { batch: event.target.value })} slotProps={{ htmlInput: { 'aria-label': `Партия, строка ${index + 1}` } }} />
                  <TextField size="small" type="date" value={row.expiresOn} onChange={(event) => patch(row.key, { expiresOn: event.target.value })} slotProps={{ htmlInput: { 'aria-label': `Годен до, строка ${index + 1}` } }} />
                  <Box>{!last && <Tooltip title="Убрать строку"><IconButton size="small" aria-label={`Убрать строку ${index + 1}`} onClick={() => remove(row.key)}><DeleteOutlineRounded fontSize="small" /></IconButton></Tooltip>}</Box>
                </Box>
              )
            })}
          </Box>
          <Button size="small" startIcon={<AddRounded />} sx={{ alignSelf: 'start' }} onClick={() => setRows((current) => [...current, emptyRow()])}>Ещё строка</Button>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Typography sx={{ flex: 1, fontSize: 13, color: 'text.secondary' }}>
          {filled.length === 0 ? 'Позиций нет' : `Позиций: ${filled.length}${fresh.length > 0 ? `, новых в номенклатуре: ${fresh.length}` : ''}`}
        </Typography>
        <Button onClick={onClose}>Отмена</Button>
        <Button variant="contained" disabled={busy || filled.length === 0} onClick={() => void submit()}>Провести поступление</Button>
      </DialogActions>
    </Dialog>
  )
}
