import { useCallback, useEffect, useState } from 'react'
import { Alert, Box, Button, Typography } from '@mui/material'
import { loadMetricaOfflineConversions, uploadMetricaOfflineConversions, type MetricaOfflineConversionsStatus } from '../../api/integrations'

export function MetricaOfflineConversionsPanel({ editable, goalConfigured, notify }: { editable: boolean; goalConfigured: boolean; notify: (message: string) => void }) {
  const [status, setStatus] = useState<MetricaOfflineConversionsStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setStatus(await loadMetricaOfflineConversions())
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const upload = async () => {
    setUploading(true)
    setError('')
    try {
      const result = await uploadMetricaOfflineConversions()
      notify(result.uploaded > 0 ? `Метрика приняла конверсий: ${result.uploaded}` : 'Новых конверсий для выгрузки нет')
      await reload()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setUploading(false)
    }
  }

  return <Box component="section" aria-label="Offline conversions Яндекс Метрики" sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
    <Typography sx={{ fontWeight: 650, fontSize: 14 }}>Offline conversions: запись на приём</Typography>
    <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 0.5 }}>Сайт пациента передаёт <code>metricaClientId</code> при создании записи через <code>POST /api/v1/appointments</code>. CRM не использует идентификатор браузера сотрудника.</Typography>
    {!goalConfigured && <Alert severity="info" sx={{ mt: 1.5 }}>Укажите ID offline-цели и сохраните настройки, чтобы включить выгрузку.</Alert>}
    {error && <Alert severity="error" sx={{ mt: 1.5 }}>{error}</Alert>}
    {loading && !status ? <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1.5 }}>Загрузка статуса…</Typography> : status && <Box sx={{ mt: 1.5 }}>
      <Typography sx={{ fontSize: 14 }}>Записей с ClientID: {status.attributed}</Typography>
      <Typography sx={{ fontSize: 14 }}>К выгрузке: {status.pending}{status.failed > 0 ? ` · ошибок: ${status.failed}` : ''}</Typography>
      {status.uploading > 0 && <Typography sx={{ fontSize: 14 }}>Отправляются: {status.uploading}</Typography>}
      <Typography sx={{ fontSize: 14 }}>Принято Метрикой: {status.uploaded}</Typography>
      {status.lastUploadedAt && <Typography sx={{ color: 'text.secondary', fontSize: 12, mt: 0.5 }}>Последняя выгрузка: {new Date(status.lastUploadedAt).toLocaleString('ru-RU')}. Данные появляются в отчётах Метрики в течение нескольких часов.</Typography>}
      {status.lastError && <Typography color="error" sx={{ fontSize: 13, mt: 0.5 }}>Последняя ошибка: {status.lastError}</Typography>}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1.5 }}>
        {editable && <Button size="small" variant="contained" onClick={() => void upload()} disabled={uploading || loading || !status.configured || status.pending === 0}>{uploading ? 'Выгрузка…' : 'Выгрузить конверсии'}</Button>}
        <Button size="small" onClick={() => void reload()} disabled={loading || uploading}>Обновить статус</Button>
      </Box>
    </Box>}
  </Box>
}
