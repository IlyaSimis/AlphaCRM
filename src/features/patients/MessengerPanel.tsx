import { useEffect, useState } from 'react'
import { ContentCopyRounded, OpenInNewRounded } from '@mui/icons-material'
import { Alert, Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { createMessengerLink, disconnectMessenger, preferMessenger, type MessengerChannel, type MessengerLink } from '../../api/messengers'
import type { PatientItem } from '../../api/types'

const labels: Record<MessengerChannel, string> = { TELEGRAM: 'Telegram', MAX: 'MAX' }

/** Привязка без ручного ввода внешнего id: пациент сам подтверждает канал по одноразовой ссылке. */
export function MessengerPanel({ patient, admin }: { patient: PatientItem; admin: boolean }) {
  const [telegram, setTelegram] = useState(Boolean(patient.telegramConnected))
  const [max, setMax] = useState(Boolean(patient.maxConnected))
  const [preferred, setPreferred] = useState<MessengerChannel | ''>(patient.preferredMessenger ?? '')
  const [link, setLink] = useState<MessengerLink | null>(null)
  const [error, setError] = useState('')
  const [working, setWorking] = useState('')

  useEffect(() => {
    setTelegram(Boolean(patient.telegramConnected))
    setMax(Boolean(patient.maxConnected))
    setPreferred(patient.preferredMessenger ?? '')
    setLink(null)
    setError('')
  }, [patient.id, patient.telegramConnected, patient.maxConnected, patient.preferredMessenger])

  const run = async (key: string, action: () => Promise<void>) => {
    setWorking(key)
    setError('')
    try { await action() } catch (reason) { setError((reason as Error).message) } finally { setWorking('') }
  }

  const connect = (channel: MessengerChannel) => void run(`link-${channel}`, async () => {
    setLink(await createMessengerLink(patient.id, channel))
  })

  const disconnect = (channel: MessengerChannel) => void run(`disconnect-${channel}`, async () => {
    const updated = await disconnectMessenger(patient.id, channel)
    setTelegram(Boolean(updated.telegramConnected))
    setMax(Boolean(updated.maxConnected))
    setPreferred(updated.preferredMessenger ?? '')
    setLink(null)
  })

  const prefer = (channel: MessengerChannel | '') => void run('preference', async () => {
    const updated = await preferMessenger(patient.id, channel)
    setPreferred(updated.preferredMessenger ?? '')
  })

  return (
    <Box sx={{ mt: 2 }}>
      <Typography sx={{ fontWeight: 700, mb: 1 }}>Мессенджеры</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1.5 }}>
        {(Object.keys(labels) as MessengerChannel[]).map((channel) => {
          const connected = channel === 'TELEGRAM' ? telegram : max
          return (
            <Box key={channel} sx={{ flex: 1, p: 1.25, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
              <Typography sx={{ fontWeight: 650 }}>{labels[channel]}</Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1 }}>{connected ? 'Подключён' : 'Не подключён'}</Typography>
              {admin && (connected
                ? <Button size="small" color="inherit" disabled={working !== ''} onClick={() => disconnect(channel)}>Отключить</Button>
                : <Button size="small" variant="outlined" disabled={working !== ''} onClick={() => connect(channel)}>Получить ссылку</Button>)}
            </Box>
          )
        })}
      </Stack>
      {admin && (telegram || max) && (
        <TextField select fullWidth size="small" label="Основной канал уведомлений" value={preferred}
          disabled={working !== ''} onChange={(event) => prefer(event.target.value as MessengerChannel | '')}>
          <MenuItem value="">Автоматически</MenuItem>
          {telegram && <MenuItem value="TELEGRAM">Telegram</MenuItem>}
          {max && <MenuItem value="MAX">MAX</MenuItem>}
        </TextField>
      )}
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {link && (
        <Alert severity="info" sx={{ mt: 1 }}>
          <Typography variant="body2">Ссылка для {labels[link.channel]} действует 10 минут и один раз. Передайте её пациенту.</Typography>
          {link.url
            ? <Button size="small" endIcon={<OpenInNewRounded />} href={link.url} target="_blank" rel="noreferrer">Открыть привязку</Button>
            : <Typography variant="body2" sx={{ mt: 1, wordBreak: 'break-all' }}>Токен: {link.token}. Публичный адрес бота не задан в настройках сервера.</Typography>}
          <Button size="small" startIcon={<ContentCopyRounded />} onClick={() => void navigator.clipboard.writeText(link.url || link.token)}>Скопировать</Button>
        </Alert>
      )}
    </Box>
  )
}
