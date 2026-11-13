import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { requestBreakGlass } from '../../api/clinical'
import type { BreakGlassGrant, BreakGlassReason } from '../../api/types'
import { breakGlassReasonLabels, reasonTextProblem } from './breakGlass'

type BreakGlassDialogProps = {
  patientId: string
  patientName: string
  onClose: () => void
  onGranted: (grant: BreakGlassGrant) => void
}

/**
 * Экстренный доступ владельца (K1): причина из списка, пояснение и пароль ещё раз. Доступ — к
 * истории одного пациента, только чтение, 60 минут; выдача и каждое чтение — в журнале.
 */
export function BreakGlassDialog({ patientId, patientName, onClose, onGranted }: BreakGlassDialogProps) {
  const [reasonCode, setReasonCode] = useState<BreakGlassReason>('PATIENT_COMPLAINT')
  const [reasonText, setReasonText] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [tried, setTried] = useState(false)
  const textProblem = tried ? reasonTextProblem(reasonText) : null

  const submit = async () => {
    setTried(true)
    if (reasonTextProblem(reasonText) || !password) {
      setError(!password ? 'Введите свой пароль' : '')
      return
    }
    setBusy(true)
    setError('')
    try {
      onGranted(await requestBreakGlass(patientId, { reasonCode, reasonText: reasonText.trim(), password }))
    } catch (reason) {
      setError((reason as Error).message)
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Экстренный доступ к медицинским данным</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity="warning">
            Пациент: {patientName}. Доступ — только чтение истории приёмов этого пациента на 60 минут. Выдача и каждое открытие пишутся в журнал; старший администратор и лечащие врачи увидят, что доступ был.
          </Alert>
          <TextField select label="Причина" value={reasonCode} onChange={(event) => setReasonCode(event.target.value as BreakGlassReason)}>
            {(Object.keys(breakGlassReasonLabels) as BreakGlassReason[]).map((code) => <MenuItem key={code} value={code}>{breakGlassReasonLabels[code]}</MenuItem>)}
          </TextField>
          <TextField label="Пояснение" value={reasonText} onChange={(event) => setReasonText(event.target.value)} multiline minRows={2} error={Boolean(textProblem)} helperText={textProblem ?? 'Хранится зашифрованным, видно в журнале экстренных доступов'} slotProps={{ htmlInput: { maxLength: 1000 } }} />
          <TextField label="Ваш пароль" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
          {error && <Alert severity="error">{error}</Alert>}
          <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Продлить доступ можно только новым запросом с новой причиной.</Typography>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Отмена</Button>
        <Button variant="contained" color="error" disabled={busy} onClick={() => void submit()}>Открыть доступ</Button>
      </DialogActions>
    </Dialog>
  )
}
