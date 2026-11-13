import { useRef, useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { refundPayment, voidCharge } from '../../api/billing'
import type { ChargeItem, RefundReason, VoidReason } from '../../api/types'
import { refundReasonLabels, voidReasonLabels } from '../finance/finance'
import { formatMoney } from '../schedule/booking'

type CloseChargeDialogProps = {
  /** void — аннулировать неоплаченный счёт; refund — вернуть оплату. */
  mode: 'void' | 'refund'
  charge: ChargeItem
  /** Кто и за что: «Анна Михайлова, 20.10 10:00, Первичный приём». */
  subject: string
  onClose: () => void
  onDone: (message: string) => void
}

/** Аннулирование или возврат — с причиной из списка; свободного текста нет (в журнал идёт код). */
export function CloseChargeDialog({ mode, charge, subject, onClose, onDone }: CloseChargeDialogProps) {
  const [voidReason, setVoidReason] = useState<VoidReason>('ERROR')
  const [refundReason, setRefundReason] = useState<RefundReason>('PATIENT_REQUEST')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const key = useRef(`refund-${crypto.randomUUID()}`)
  const amount = charge.amountMinor == null ? null : formatMoney(charge.amountMinor, charge.currency)

  const submit = async () => {
    setBusy(true)
    setError('')
    try {
      if (mode === 'void') {
        await voidCharge(charge.chargeId, voidReason)
        onDone('Счёт аннулирован')
      } else {
        if (!charge.paymentId) throw new Error('У счёта нет оплаты')
        await refundPayment(charge.paymentId, refundReason, key.current)
        onDone(`Возврат оформлен${amount ? `: ${amount}` : ''}. Чек возврата уйдёт в кассу, когда она подключена`)
      }
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{mode === 'void' ? 'Аннулировать счёт' : 'Возврат оплаты'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>{subject}{amount ? ` · ${amount}` : ''}</Typography>
          {mode === 'void'
            ? <TextField select label="Причина" value={voidReason} onChange={(event) => setVoidReason(event.target.value as VoidReason)}>
              {(Object.keys(voidReasonLabels) as VoidReason[]).map((reason) => <MenuItem key={reason} value={reason}>{voidReasonLabels[reason]}</MenuItem>)}
            </TextField>
            : <TextField select label="Причина" value={refundReason} onChange={(event) => setRefundReason(event.target.value as RefundReason)}>
              {(Object.keys(refundReasonLabels) as RefundReason[]).map((reason) => <MenuItem key={reason} value={reason}>{refundReasonLabels[reason]}</MenuItem>)}
            </TextField>}
          <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>
            {mode === 'void' ? 'Счёт перестанет ждать оплаты; запись об этом останется в журнале.' : 'Возвращается вся сумма оплаты; счёт получит статус «Возврат». Платёжный шлюз не подключён — деньги пациенту возвращаются вне системы.'}
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Отмена</Button>
        <Button variant="contained" color={mode === 'void' ? 'warning' : 'error'} disabled={busy} onClick={() => void submit()}>{mode === 'void' ? 'Аннулировать' : 'Оформить возврат'}</Button>
      </DialogActions>
    </Dialog>
  )
}
