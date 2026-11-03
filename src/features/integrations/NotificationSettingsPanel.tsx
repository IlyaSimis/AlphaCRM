import { useEffect, useState } from 'react'
import { Alert, Box, Button, FormControlLabel, Switch, Typography } from '@mui/material'
import { loadNotificationSettings, saveNotificationSettings } from '../../api/notifications'

export function NotificationSettingsPanel({ apiOnline, editable, notify }: { apiOnline: boolean; editable: boolean; notify: (message: string) => void }) {
  const [reminder24h, setReminder24h] = useState(true)
  const [reminder3h, setReminder3h] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadNotificationSettings()
      .then((settings) => {
        if (cancelled) return
        setReminder24h(settings.reminder24hEnabled)
        setReminder3h(settings.reminder3hEnabled)
        setLoaded(true)
      })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline])

  const save = async () => {
    setBusy(true)
    setError('')
    try {
      await saveNotificationSettings({ reminder24hEnabled: reminder24h, reminder3hEnabled: reminder3h })
      notify('Настройки напоминаний сохранены')
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!apiOnline || !loaded) return error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null
  return (
    <Box component="section" aria-label="Напоминания пациентам" className="panel" sx={{ p: { xs: 2, md: 3 }, mb: 2 }}>
      <Typography variant="h6" sx={{ fontWeight: 750 }}>Напоминания пациентам</Typography>
      <Typography sx={{ color: 'text.secondary', fontSize: 14, mb: 1 }}>Общее правило клиники для Telegram и MAX. Отключение действует на новые и ещё не отправленные напоминания.</Typography>
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <FormControlLabel control={<Switch checked={reminder24h} onChange={(event) => setReminder24h(event.target.checked)} disabled={!editable || busy} />} label="За 24 часа" />
        <FormControlLabel control={<Switch checked={reminder3h} onChange={(event) => setReminder3h(event.target.checked)} disabled={!editable || busy} />} label="За 3 часа" />
      </Box>
      <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>Можно включить оба напоминания или отключить их полностью.</Typography>
      {editable && <Button sx={{ mt: 2 }} variant="contained" onClick={() => void save()} disabled={busy}>{busy ? 'Сохранение…' : 'Сохранить напоминания'}</Button>}
    </Box>
  )
}
