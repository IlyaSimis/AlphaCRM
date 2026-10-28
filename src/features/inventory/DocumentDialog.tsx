import { useState } from 'react'
import { DeleteOutlineRounded, QrCodeScannerRounded } from '@mui/icons-material'
import { Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { createDocument, postDocument, scanCode, updateDocument, type DocumentInput } from '../../api/inventory'
import type { InventoryItem, StockDocument, StockDocumentType, Warehouse } from '../../api/types'
import { localToday } from '../../shared/dates'
import { documentTypeLabels, formatQuantity, parseQuantity, shortageText, warehousesOf } from './inventory'

type LineDraft = { key: string; itemId: string; quantity: string; batch: string; expiresOn: string; codes: string[] }

type DocumentDialogProps = {
  document: StockDocument | null
  items: InventoryItem[]
  warehouses: Warehouse[]
  /** Можно провести расход сверх остатка (администраторы). */
  allowNegative: boolean
  onClose: () => void
  onDone: (message: string) => void
}

const newLine = (itemId = ''): LineDraft => ({ key: crypto.randomUUID(), itemId, quantity: '', batch: '', expiresOn: '', codes: [] })

/**
 * Накладная: шапка (тип, дата, склады, поставщик) и строки. Для маркируемой позиции марки
 * сканируются по одной на упаковку — количество равно числу марок. Сканер работает как клавиатура:
 * поле «Скан» принимает строку и Enter.
 */
export function DocumentDialog({ document, items, warehouses, allowNegative, onClose, onDone }: DocumentDialogProps) {
  const readOnly = Boolean(document && document.status !== 'DRAFT')
  const defaultWarehouse = warehouses.find((item) => item.isDefault)?.id ?? warehouses[0]?.id ?? ''
  const [type, setType] = useState<StockDocumentType>(document?.type ?? 'RECEIPT')
  const [date, setDate] = useState(document?.documentDate ?? localToday())
  const [supplier, setSupplier] = useState(document?.supplier ?? '')
  const [fromId, setFromId] = useState(document?.fromWarehouseId ?? defaultWarehouse)
  const [toId, setToId] = useState(document?.toWarehouseId ?? defaultWarehouse)
  const [note, setNote] = useState(document?.note ?? '')
  const [lines, setLines] = useState<LineDraft[]>(() => document
    ? document.lines.map((line) => ({ key: line.id ?? crypto.randomUUID(), itemId: line.itemId, quantity: formatQuantity(line.quantity), batch: line.batch, expiresOn: line.expiresOn ?? '', codes: line.codes }))
    : [newLine()])
  const [scan, setScan] = useState('')
  const [negative, setNegative] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [key] = useState(() => crypto.randomUUID())
  const stores = warehousesOf(type)
  const itemOf = (id: string) => items.find((item) => item.id === id)
  const activeItems = items.filter((item) => item.active)

  const patch = (lineKey: string, change: Partial<LineDraft>) => setLines((current) => current.map((line) => line.key === lineKey ? { ...line, ...change } : line))

  /** Скан марки: позиция по GTIN, марка в её строку, количество — число марок. */
  const addScan = async () => {
    const raw = scan
    if (!raw.trim()) return
    setScan('')
    try {
      const code = await scanCode(raw)
      if (!code.itemId) {
        setError(`Позиции с GTIN ${code.gtin} нет в номенклатуре`)
        return
      }
      if (lines.some((line) => line.codes.includes(code.sgtin))) {
        setError('Эта упаковка уже отсканирована')
        return
      }
      setError('')
      setLines((current) => {
        const target = current.find((line) => line.itemId === code.itemId) ?? current.find((line) => !line.itemId)
        if (!target) {
          return [...current, { ...newLine(code.itemId as string), quantity: '1', batch: code.batch, expiresOn: code.expiresOn ?? '', codes: [code.sgtin] }]
        }
        return current.map((line) => line.key !== target.key ? line : {
          ...line,
          itemId: code.itemId as string,
          codes: [...line.codes, code.sgtin],
          quantity: String(line.codes.length + 1),
          batch: line.batch || code.batch,
          expiresOn: line.expiresOn || code.expiresOn || '',
        })
      })
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const build = (): DocumentInput | string => {
    const filled = lines.filter((line) => line.itemId)
    if (filled.length === 0) return 'Добавьте хотя бы одну строку'
    const result: DocumentInput['lines'] = []
    for (const line of filled) {
      const item = itemOf(line.itemId)
      const quantity = item?.marked ? line.codes.length : parseQuantity(line.quantity)
      if (!quantity) return item?.marked ? `Отсканируйте марки: ${item.name}` : `Укажите количество: ${item?.name ?? 'позиция'}`
      result.push({ itemId: line.itemId, quantity, batch: line.batch.trim() || null, expiresOn: line.expiresOn || null, codes: line.codes })
    }
    if (stores.from && stores.to && fromId === toId) return 'Для перемещения выберите два разных склада'
    return {
      type,
      documentDate: date,
      supplier: supplier.trim() || null,
      fromWarehouseId: stores.from ? fromId : null,
      toWarehouseId: stores.to ? toId : null,
      note: note.trim() || null,
      idempotencyKey: key,
      lines: result,
    }
  }

  const save = async (post: boolean) => {
    const input = build()
    if (typeof input === 'string') {
      setError(input)
      return
    }
    setBusy(true)
    try {
      const saved = document ? await updateDocument(document.id, input) : await createDocument(input)
      if (!post) {
        onDone('Черновик накладной сохранён')
        return
      }
      const posted = await postDocument(saved.id, negative)
      onDone(shortageText(posted) ?? 'Накладная проведена')
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{document ? `${documentTypeLabels[document.type]} от ${document.documentDate}` : 'Новая накладная'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {readOnly && <Alert severity="info">Проведённая накладная не правится: исправление — новым документом (например, возвратом или списанием).</Alert>}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField select label="Тип" value={type} disabled={Boolean(document)} onChange={(event) => setType(event.target.value as StockDocumentType)} sx={{ minWidth: 200 }}>
              {(Object.keys(documentTypeLabels) as StockDocumentType[]).map((value) => <MenuItem key={value} value={value}>{documentTypeLabels[value]}</MenuItem>)}
            </TextField>
            <TextField label="Дата" type="date" value={date} disabled={readOnly} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            {stores.from && <TextField select label="Со склада" value={fromId} disabled={readOnly} onChange={(event) => setFromId(event.target.value)} sx={{ minWidth: 180 }}>{warehouses.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}</TextField>}
            {stores.to && <TextField select label="На склад" value={toId} disabled={readOnly} onChange={(event) => setToId(event.target.value)} sx={{ minWidth: 180 }}>{warehouses.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}</TextField>}
          </Stack>
          {(type === 'RECEIPT' || type === 'RETURN_TO_SUPPLIER') && <TextField label="Поставщик" value={supplier} disabled={readOnly} onChange={(event) => setSupplier(event.target.value)} />}
          {document?.appointmentId && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Основание: завершённый приём (нормы расхода услуги). Пациента склад не знает.</Typography>}
          {!readOnly && <TextField label="Скан марки (DataMatrix)" value={scan} onChange={(event) => setScan(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void addScan() } }} helperText="Наведите сканер и нажмите Enter — марка попадёт в строку своей позиции" slotProps={{ input: { startAdornment: <QrCodeScannerRounded sx={{ mr: 1, color: 'text.secondary' }} /> } }} />}
          {lines.map((line) => {
            const item = itemOf(line.itemId)
            return (
              <Box key={line.key} sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 1.5 }}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ alignItems: { md: 'center' } }}>
                  <TextField select size="small" label="Позиция" value={line.itemId} disabled={readOnly} onChange={(event) => patch(line.key, { itemId: event.target.value, codes: [] })} sx={{ minWidth: 240, flex: 1 }}>
                    {(readOnly ? items : activeItems).map((option) => <MenuItem key={option.id} value={option.id}>{option.name}{option.marked ? ' · марк.' : ''}</MenuItem>)}
                  </TextField>
                  <TextField size="small" label={item ? `Кол-во, ${item.unit}` : 'Кол-во'} value={item?.marked ? String(line.codes.length) : line.quantity} disabled={readOnly || Boolean(item?.marked)} onChange={(event) => patch(line.key, { quantity: event.target.value })} sx={{ width: 120 }} />
                  <TextField size="small" label="Партия" value={line.batch} disabled={readOnly} onChange={(event) => patch(line.key, { batch: event.target.value })} sx={{ width: 140 }} />
                  {type === 'RECEIPT' && <TextField size="small" label="Годен до" type="date" value={line.expiresOn} disabled={readOnly} onChange={(event) => patch(line.key, { expiresOn: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />}
                  {!readOnly && <IconButton aria-label="Удалить строку" onClick={() => setLines((current) => current.filter((row) => row.key !== line.key))}><DeleteOutlineRounded /></IconButton>}
                </Stack>
                {item?.marked && <Stack direction="row" spacing={0.5} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
                  {line.codes.length === 0 && <Typography sx={{ fontSize: 13, color: 'warning.main' }}>Маркируемая позиция: отсканируйте марку каждой упаковки.</Typography>}
                  {line.codes.map((code) => <Chip key={code} size="small" label={code} onDelete={readOnly ? undefined : () => patch(line.key, { codes: line.codes.filter((value) => value !== code) })} />)}
                </Stack>}
              </Box>
            )
          })}
          {!readOnly && <Button onClick={() => setLines((current) => [...current, newLine()])}>Добавить строку</Button>}
          <TextField label="Примечание" value={note} disabled={readOnly} onChange={(event) => setNote(event.target.value)} />
          {!readOnly && allowNegative && type === 'CONSUMPTION' && <FormControlLabel control={<Checkbox checked={negative} onChange={(event) => setNegative(event.target.checked)} />} label="Провести даже при нехватке остатка (регистратура получит задачу)" />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{readOnly ? 'Закрыть' : 'Отмена'}</Button>
        {!readOnly && <Button disabled={busy} onClick={() => void save(false)}>Сохранить черновик</Button>}
        {!readOnly && <Button variant="contained" disabled={busy} onClick={() => void save(true)}>Провести</Button>}
      </DialogActions>
    </Dialog>
  )
}
