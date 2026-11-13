import { useEffect, useState } from 'react'
import { RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadMarkingCodes, loadMarkingOperations, loadMarkingStatus, retryMarkingOperation } from '../../api/inventory'
import type { MarkingCodeItem, MarkingOperationItem, MarkingStatus } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { Metric } from '../../shared/ui'
import { adapterLabels, codeStatusLabels, operationErrorLabels, operationLabels, operationStatusLabels } from './inventory'

type MarkingTabProps = { role: Role; notify: (message: string) => void }

/**
 * Маркировка (раздел G): очередь операций к оператору и реестр марок. Без настроенной интеграции
 * операции ждут в очереди с причиной «не настроено»; остатки и накладные при этом работают.
 */
export function MarkingTab({ role, notify }: MarkingTabProps) {
  const manage = can(role, 'inventory.marking.manage')
  const codesAllowed = can(role, 'inventory.stock.read')
  const [status, setStatus] = useState<MarkingStatus | null>(null)
  const [operations, setOperations] = useState<MarkingOperationItem[]>([])
  const [codes, setCodes] = useState<MarkingCodeItem[]>([])
  const [filter, setFilter] = useState('')
  const [codeFilter, setCodeFilter] = useState('')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([loadMarkingStatus(), loadMarkingOperations(filter), codesAllowed ? loadMarkingCodes(codeFilter) : Promise.resolve([] as MarkingCodeItem[])])
      .then(([state, queue, registry]) => { if (!cancelled) { setStatus(state); setOperations(queue); setCodes(registry); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [filter, codeFilter, codesAllowed, revision])

  const retry = async (operation: MarkingOperationItem) => {
    try {
      await retryMarkingOperation(operation.id)
      notify('Операция отправлена повторно')
      setRevision((value) => value + 1)
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  return (
    <>
      {status && <Box className="summary-grid" sx={{ mb: 2 }}>
        <Metric value={status.adapter} label="интеграция" note={adapterLabels[status.adapter] ?? status.adapter} accent={status.adapter === 'NONE'} />
        <Metric value={String(status.pending)} label="в очереди" note="ждут ответа оператора" />
        <Metric value={String(status.errors)} label="с ошибкой" note="нужен разбор" accent={status.errors > 0} />
      </Box>}
      {status?.adapter === 'NONE' && <Alert severity="info" sx={{ mb: 2 }}>Интеграция с Честным знаком не настроена: операции копятся в очереди и уйдут после подключения. Для лекарств вывод из оборота при медицинском применении идёт через регистратор выбытия (МДЛП) — уточните схему у оператора.</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box className="panel" sx={{ mb: 2 }}>
        <Stack direction="row" spacing={1.5} sx={{ mb: 1.5, alignItems: 'center' }}>
          <Typography className="panel-label" sx={{ flex: 1 }}>ОПЕРАЦИИ МАРКИРОВКИ</Typography>
          <TextField size="small" select label="Статус" value={filter} onChange={(event) => setFilter(event.target.value)} sx={{ minWidth: 160 }}>
            <MenuItem value="">Все</MenuItem>
            {Object.entries(operationStatusLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
          <Button startIcon={<RefreshRounded />} onClick={() => setRevision((value) => value + 1)}>Обновить</Button>
        </Stack>
        {operations.length === 0
          ? <Typography sx={{ color: 'text.secondary' }}>Операций нет.</Typography>
          : <table className="audit-table">
            <thead><tr><th>Создана</th><th>Операция</th><th>Марок</th><th>Статус</th><th>Попыток</th><th>Причина</th><th /></tr></thead>
            <tbody>
              {operations.map((operation) => (
                <tr key={operation.id}>
                  <td className="nowrap">{formatClinicMoment(operation.createdAt)}</td>
                  <td>{operationLabels[operation.type] ?? operation.type}</td>
                  <td>{operation.codes}</td>
                  <td><Chip size="small" variant="outlined" label={operationStatusLabels[operation.status] ?? operation.status} color={operation.status === 'DONE' ? 'success' : operation.status === 'ERROR' ? 'error' : 'warning'} /></td>
                  <td>{operation.attempts}</td>
                  <td>{operation.lastError ? operationErrorLabels[operation.lastError] ?? operation.lastError : '—'}</td>
                  <td>{manage && operation.status !== 'DONE' && <Button size="small" onClick={() => void retry(operation)}>Повторить</Button>}</td>
                </tr>
              ))}
            </tbody>
          </table>}
      </Box>
      {codesAllowed && <Box className="panel">
        <Stack direction="row" spacing={1.5} sx={{ mb: 1.5, alignItems: 'center' }}>
          <Typography className="panel-label" sx={{ flex: 1 }}>МАРКИ (SGTIN)</Typography>
          <TextField size="small" select label="Статус марки" value={codeFilter} onChange={(event) => setCodeFilter(event.target.value)} sx={{ minWidth: 200 }}>
            <MenuItem value="">Все</MenuItem>
            {Object.entries(codeStatusLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
        </Stack>
        {codes.length === 0
          ? <Typography sx={{ color: 'text.secondary' }}>Марок нет.</Typography>
          : <table className="audit-table">
            <thead><tr><th>SGTIN</th><th>Позиция</th><th>Партия</th><th>Годен до</th><th>Статус</th><th>Обновлена</th></tr></thead>
            <tbody>
              {codes.map((code) => (
                <tr key={code.sgtin}>
                  <td className="nowrap">{code.sgtin}</td>
                  <td>{code.itemName}</td>
                  <td>{code.batch || '—'}</td>
                  <td>{code.expiresOn ?? '—'}</td>
                  <td>{codeStatusLabels[code.status] ?? code.status}</td>
                  <td className="nowrap">{formatClinicMoment(code.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>}
      </Box>}
    </>
  )
}
