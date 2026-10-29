import { useEffect, useState } from 'react'
import { CallRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, IconButton, Stack, TextField, Typography } from '@mui/material'
import { loadPatientCalls, loadTelephonyStatus, startPatientCall, type CallItem, type TelephonyStatus } from '../../api/telephony'
import { clinicTimeZone } from '../../shared/clinicTime'
import { isUuid } from '../../shared/text'

const STATUS_LABELS: Record<string, string> = {
  REQUESTING: 'Отправляем команду', REQUESTED: 'Вызов запрошен', Appeared: 'Звонок', Connected: 'Соединён',
  Disconnected: 'Завершён', FAILED: 'Ошибка вызова', UNKNOWN: 'Статус уточняется',
}

function callTime(value: string) {
  return new Date(value).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', timeZone: clinicTimeZone() })
}

/** Mango в карточке пациента: исходящий callback и связанная история звонков. */
export function PatientCalls({ patientId, apiOnline }: { patientId: string; apiOnline: boolean }) {
  const enabled = apiOnline && isUuid(patientId)
  const [status, setStatus] = useState<TelephonyStatus | null>(null)
  const [calls, setCalls] = useState<CallItem[]>([])
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [dialog, setDialog] = useState(false)
  const [extension, setExtension] = useState('')
  const [busy, setBusy] = useState(false)

  const refresh = async () => {
    if (!enabled) return
    try {
      const [nextStatus, nextCalls] = await Promise.all([loadTelephonyStatus(), loadPatientCalls(patientId)])
      setStatus(nextStatus)
      setCalls(nextCalls)
      setError('')
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  useEffect(() => {
    setStatus(null)
    setCalls([])
    setError('')
    setMessage('')
    void refresh()
    // patientId меняется вместе с открытой карточкой.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId, enabled])

  const openDialog = () => {
    const saved = localStorage.getItem('alphacrm.mango-extension') ?? ''
    setExtension(saved || status?.defaultExtension || '')
    setMessage('')
    setDialog(true)
  }

  const call = async () => {
    setBusy(true)
    setError('')
    try {
      const created = await startPatientCall(patientId, extension.trim())
      localStorage.setItem('alphacrm.mango-extension', extension.trim())
      setCalls((current) => [created, ...current.filter((item) => item.id !== created.id)])
      setDialog(false)
      setMessage('Mango принял команду: сначала зазвонит телефон сотрудника, затем начнётся соединение с пациентом')
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!enabled) return null
  return (
    <>
      <Divider sx={{ my: 2 }} />
      <Box className="panel-title-row">
        <Typography className="panel-label">ТЕЛЕФОНИЯ MANGO</Typography>
        <Box>
          <IconButton size="small" aria-label="Обновить звонки" onClick={() => void refresh()}><RefreshRounded fontSize="small" /></IconButton>
          <Button size="small" variant="outlined" startIcon={<CallRounded />} disabled={!status?.configured} onClick={openDialog}>Позвонить</Button>
        </Box>
      </Box>
      {status === null && !error && <CircularProgress size={20} sx={{ mt: 1 }} aria-label="Загрузка телефонии" />}
      {status && !status.configured && <Typography sx={{ mt: 1, fontSize: 13, color: 'text.secondary' }}>Mango не подключён. Ключи API-коннектора задаются на сервере.</Typography>}
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {message && <Alert severity="success" sx={{ mt: 1 }}>{message}</Alert>}
      {calls.length > 0 && <Stack spacing={1} sx={{ mt: 1.5 }}>
        {calls.map((item) => <Box className="history-item" key={item.id}>
          <Box>
            <Typography sx={{ fontSize: 14, fontWeight: 650 }}>{item.direction === 'INBOUND' ? 'Входящий' : 'Исходящий'} · {STATUS_LABELS[item.status] ?? item.status}</Typography>
            <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{callTime(item.occurredAt)}{item.extension ? ` · доб. ${item.extension}` : ''}</Typography>
          </Box>
          {item.recordingAvailable && <Chip size="small" variant="outlined" label="Есть запись" />}
        </Box>)}
      </Stack>}
      {dialog && <Dialog open onClose={() => !busy && setDialog(false)} fullWidth maxWidth="xs">
        <DialogTitle>Звонок пациенту</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2, color: 'text.secondary' }}>Mango сначала позвонит на добавочный сотрудника. После ответа начнётся вызов пациента.</Typography>
          <TextField autoFocus fullWidth label="Добавочный номер сотрудника" value={extension} onChange={(event) => setExtension(event.target.value)} inputMode="numeric" helperText="Только цифры, например 101" />
        </DialogContent>
        <DialogActions><Button disabled={busy} onClick={() => setDialog(false)}>Отмена</Button><Button variant="contained" startIcon={<CallRounded />} disabled={busy || !/^\d{1,32}$/.test(extension.trim())} onClick={() => void call()}>{busy ? 'Вызываем…' : 'Начать звонок'}</Button></DialogActions>
      </Dialog>}
    </>
  )
}
