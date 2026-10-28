import { useEffect, useState } from 'react'
import { AddRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { cancelDocument, loadDocuments, loadItems, loadWarehouses, postDocument } from '../../api/inventory'
import type { InventoryItem, StockDocument, StockDocumentType, Warehouse } from '../../api/types'
import { shortDate } from '../../shared/dates'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { DocumentDialog } from './DocumentDialog'
import { documentStatusLabels, documentTypeLabels, shortageText } from './inventory'

type DocumentsTabProps = { role: Role; notify: (message: string) => void }

/** Накладные: черновик правится и проводится; проведённая — только для чтения. */
export function DocumentsTab({ role, notify }: DocumentsTabProps) {
  const [documents, setDocuments] = useState<StockDocument[] | null>(null)
  const [items, setItems] = useState<InventoryItem[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [type, setType] = useState<StockDocumentType | ''>('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [open, setOpen] = useState<StockDocument | 'new' | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const allowNegative = can(role, 'inventory.document.allow-negative')

  useEffect(() => {
    Promise.all([loadItems({ sort: 'name' }), loadWarehouses()])
      .then(([catalog, stores]) => { setItems(catalog); setWarehouses(stores) })
      .catch((reason: Error) => setError(reason.message))
  }, [revision])

  useEffect(() => {
    let cancelled = false
    loadDocuments(type, status)
      .then((rows) => { if (!cancelled) { setDocuments(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [type, status, revision])

  const act = async (document: StockDocument, action: 'post' | 'cancel') => {
    setBusy(document.id)
    try {
      const result = action === 'post' ? await postDocument(document.id) : await cancelDocument(document.id)
      notify(action === 'post' ? shortageText(result) ?? 'Накладная проведена' : 'Накладная отменена')
      setRevision((value) => value + 1)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(null)
    }
  }

  const warehouseName = (id: string | null) => (id ? warehouses.find((item) => item.id === id)?.name : null) ?? '—'

  return (
    <Box className="panel">
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField size="small" select label="Тип" value={type} onChange={(event) => setType(event.target.value as StockDocumentType | '')} sx={{ minWidth: 200 }}>
          <MenuItem value="">Все типы</MenuItem>
          {(Object.keys(documentTypeLabels) as StockDocumentType[]).map((value) => <MenuItem key={value} value={value}>{documentTypeLabels[value]}</MenuItem>)}
        </TextField>
        <TextField size="small" select label="Статус" value={status} onChange={(event) => setStatus(event.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="">Все</MenuItem>
          {Object.entries(documentStatusLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<AddRounded />} disabled={items.length === 0} onClick={() => setOpen('new')}>Новая накладная</Button>
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {documents === null && !error
        ? <Box className="empty" sx={{ minHeight: 120 }}><CircularProgress aria-label="Загрузка накладных" /></Box>
        : documents && documents.length === 0
          ? <Typography sx={{ color: 'text.secondary' }}>Накладных нет.</Typography>
          : <table className="audit-table">
            <thead><tr><th>Дата</th><th>Тип</th><th>Статус</th><th>Откуда → куда</th><th>Поставщик / основание</th><th>Строк</th><th /></tr></thead>
            <tbody>
              {(documents ?? []).map((document) => (
                <tr key={document.id}>
                  <td className="nowrap">{shortDate(document.documentDate)}</td>
                  <td>{documentTypeLabels[document.type]}</td>
                  <td><Chip size="small" variant="outlined" label={documentStatusLabels[document.status] ?? document.status} color={document.status === 'POSTED' ? 'success' : document.status === 'DRAFT' ? 'warning' : 'default'} /></td>
                  <td>{warehouseName(document.fromWarehouseId)} → {warehouseName(document.toWarehouseId)}</td>
                  <td>{document.supplier ?? (document.appointmentId ? 'По приёму (нормы услуги)' : '—')}</td>
                  <td>{document.lines.length}</td>
                  <td className="nowrap">
                    <Button size="small" onClick={() => setOpen(document)}>{document.status === 'DRAFT' ? 'Открыть' : 'Просмотр'}</Button>
                    {document.status === 'DRAFT' && <Button size="small" variant="contained" disabled={busy !== null} onClick={() => void act(document, 'post')}>Провести</Button>}
                    {document.status === 'DRAFT' && <Button size="small" color="warning" disabled={busy !== null} onClick={() => void act(document, 'cancel')}>Отменить</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
      {open && <DocumentDialog document={open === 'new' ? null : open} items={items} warehouses={warehouses} allowNegative={allowNegative} onClose={() => setOpen(null)} onDone={(message) => { setOpen(null); setRevision((value) => value + 1); notify(message) }} />}
    </Box>
  )
}
