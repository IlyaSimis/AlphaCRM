import { useCallback, useEffect, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Divider, FormControlLabel, Switch, TextField, Typography } from '@mui/material'
import type { StaffItem } from '../../api/types'
import { changeIntegrationStatus, checkIntegrationWebhookStatus, connectIntegrationWebhook, loadIntegrations, saveIntegrationSecret, saveIntegrationSettings, type IntegrationType, type IntegrationView, type IntegrationWebhookStatus } from '../../api/integrations'
import { can, rolesOf } from '../../shared/permissions'
import { NotificationSettingsPanel } from './NotificationSettingsPanel'
import { MetricaOfflineConversionsPanel } from './MetricaOfflineConversionsPanel'

type IntegrationsPageProps = {
  staff: StaffItem
  apiOnline: boolean
  notify: (message: string) => void
}

const TYPES: IntegrationType[] = ['MANGO', 'TELEGRAM', 'MAX', 'YANDEX_METRICA']
const DESCRIPTIONS: Record<IntegrationType, string> = {
  MANGO: 'Исходящие звонки из карточки пациента и входящие события ВАТС.',
  TELEGRAM: 'Уведомления пациентам через бота клиники напрямую или через relay.',
  MAX: 'Уведомления пациентам через MAX Bot API клиники.',
  YANDEX_METRICA: 'Посещаемость сайта, источники трафика и достижения целей в аналитике клиники.',
}

function defaultApiUrl(type: IntegrationType) {
  if (type === 'MANGO') return 'https://app.mango-office.ru'
  if (type === 'YANDEX_METRICA') return 'https://api-metrika.yandex.net'
  return type === 'TELEGRAM' ? 'https://api.telegram.org' : 'https://platform-api2.max.ru'
}

export function IntegrationsPage({ staff, apiOnline, notify }: IntegrationsPageProps) {
  const [items, setItems] = useState<IntegrationView[]>([])
  const [loading, setLoading] = useState(apiOnline)
  const [error, setError] = useState('')
  const editable = can(rolesOf(staff), 'integration.configuration.manage')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setItems(await loadIntegrations())
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (apiOnline) void reload()
  }, [apiOnline, reload])

  const updated = (item: IntegrationView) => setItems((current) => [...current.filter((existing) => existing.type !== item.type), item])

  return (
    <Box>
      <Box className="section-intro">
        <Box>
          <Typography sx={{ color: 'text.secondary' }}>Подключения действуют только для вашей клиники. Секреты хранятся зашифрованными и после сохранения не показываются.</Typography>
        </Box>
      </Box>
      {!apiOnline && <Alert severity="info">Настройка интеграций доступна только при подключённом API.</Alert>}
      {apiOnline && loading && <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress aria-label="Загрузка интеграций" /></Box>}
      {apiOnline && error && <Alert severity="error" action={<Button onClick={() => void reload()}>Повторить</Button>}>{error}</Alert>}
      <NotificationSettingsPanel apiOnline={apiOnline} editable={editable} notify={notify} />
      {apiOnline && !loading && !error && <Box sx={{ display: 'grid', gap: 2 }}>
        {TYPES.map((type) => <IntegrationCard key={`${type}-${items.find((item) => item.type === type)?.secretVersion ?? 0}`} type={type} integration={items.find((item) => item.type === type)} editable={editable} notify={notify} onChange={updated} />)}
      </Box>}
    </Box>
  )
}

function IntegrationCard({ type, integration, editable, notify, onChange }: { type: IntegrationType; integration?: IntegrationView; editable: boolean; notify: (message: string) => void; onChange: (item: IntegrationView) => void }) {
  const [apiUrl, setApiUrl] = useState(integration?.settings.apiUrl ?? defaultApiUrl(type))
  const [extension, setExtension] = useState(integration?.settings.defaultExtension ?? '')
  const [lineNumber, setLineNumber] = useState(integration?.settings.lineNumber ?? '')
  const [publicUrl, setPublicUrl] = useState(integration?.settings.publicUrl ?? '')
  const [relayUrl, setRelayUrl] = useState(integration?.settings.relayUrl ?? '')
  const [webhookBaseUrl, setWebhookBaseUrl] = useState(integration?.settings.webhookBaseUrl ?? '')
  const [counterId, setCounterId] = useState(integration?.settings.counterId ?? '')
  const [offlineGoalId, setOfflineGoalId] = useState(integration?.settings.offlineGoalId ?? '')
  const [reminderButtons, setReminderButtons] = useState(integration?.settings.reminderButtons ?? true)
  const [firstSecret, setFirstSecret] = useState('')
  const [secondSecret, setSecondSecret] = useState('')
  const [webhookSecret, setWebhookSecret] = useState('')
  const [providerStatus, setProviderStatus] = useState<IntegrationWebhookStatus>()
  const [busy, setBusy] = useState<'save' | 'activate' | 'disable' | 'connect' | 'check' | ''>('')
  const [error, setError] = useState('')
  const title = type === 'MANGO' ? 'Mango' : type === 'TELEGRAM' ? 'Telegram' : type === 'MAX' ? 'MAX' : 'Яндекс Метрика'
  const status = integration?.status ?? 'PENDING'
  const hasSecret = integration?.hasSecret ?? false

  const save = async () => {
    setBusy('save')
    setError('')
    try {
      const settings = type === 'MANGO'
        ? { apiUrl, defaultExtension: extension, lineNumber }
        : type === 'YANDEX_METRICA'
          ? { apiUrl, counterId, offlineGoalId }
          : { apiUrl, publicUrl, relayUrl, webhookBaseUrl, reminderButtons }
      let saved = await saveIntegrationSettings(type, settings)
      if (firstSecret.trim() || secondSecret.trim() || webhookSecret.trim()) {
        const secret = type === 'MANGO'
          ? { apiKey: firstSecret, apiSalt: secondSecret }
          : type === 'YANDEX_METRICA'
            ? { oauthToken: firstSecret }
            : { botToken: firstSecret, relaySecret: secondSecret, webhookSecret }
        saved = await saveIntegrationSecret(type, secret)
      }
      onChange(saved)
      setFirstSecret('')
      setSecondSecret('')
      setWebhookSecret('')
      notify(`Настройки ${title} сохранены`)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy('')
    }
  }

  const changeStatus = async (action: 'activate' | 'disable') => {
    setBusy(action)
    setError('')
    try {
      onChange(await changeIntegrationStatus(type, action))
      notify(action === 'activate' ? `${title} активирован` : `${title} отключён`)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy('')
    }
  }

  const updateWebhook = async (action: 'connect' | 'check') => {
    setBusy(action)
    setError('')
    try {
      const result = action === 'connect' ? await connectIntegrationWebhook(type) : await checkIntegrationWebhookStatus(type)
      setProviderStatus(result)
      notify(action === 'connect' && result.state === 'CONNECTED' ? `Webhook ${title} подключён` : `Статус webhook ${title} обновлён`)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy('')
    }
  }

  const copyWebhook = async (event?: 'call' | 'recording') => {
    if (!integration?.publicKey) return
    const path = type === 'MANGO'
      ? `/api/v1/integrations/mango/events/${integration.publicKey}/${event}`
      : `/api/v1/integrations/${type.toLowerCase()}/events/${integration.publicKey}`
    const baseUrl = webhookBaseUrl.trim().replace(/\/+$/, '') || window.location.origin
    const url = `${baseUrl}${path}`
    try {
      await navigator.clipboard.writeText(url)
      notify('Webhook-ссылка скопирована')
    } catch {
      notify(url)
    }
  }

  return (
    <Box component="section" aria-label={`Интеграция ${title}`} className="panel" sx={{ p: { xs: 2, md: 3 } }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box><Typography variant="h6" sx={{ fontWeight: 750 }}>{title}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 14 }}>{DESCRIPTIONS[type]}</Typography></Box>
        <Chip size="small" color={status === 'ACTIVE' ? 'success' : status === 'ERROR' ? 'error' : 'default'} label={status === 'ACTIVE' ? 'Активно' : status === 'DISABLED' ? 'Отключено' : status === 'ERROR' ? 'Ошибка' : integration ? 'Требует настройки' : 'Не настроено'} />
      </Box>
      <Divider sx={{ my: 2 }} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
        <TextField label={`API URL ${title}`} value={apiUrl} onChange={(event) => setApiUrl(event.target.value)} disabled={!editable || !!busy} fullWidth />
        {type === 'MANGO' ? <>
          <TextField label="Внутренний номер по умолчанию" value={extension} onChange={(event) => setExtension(event.target.value)} disabled={!editable || !!busy} />
          <TextField label="Номер линии Mango" value={lineNumber} onChange={(event) => setLineNumber(event.target.value)} disabled={!editable || !!busy} />
          <TextField label="Mango API key" type="password" value={firstSecret} onChange={(event) => setFirstSecret(event.target.value)} disabled={!editable || !!busy} placeholder={hasSecret ? 'Задан — введите новый для замены' : ''} autoComplete="off" />
          <TextField label="Mango API salt" type="password" value={secondSecret} onChange={(event) => setSecondSecret(event.target.value)} disabled={!editable || !!busy} placeholder={hasSecret ? 'Задан — введите новый для замены' : ''} autoComplete="off" />
        </> : type === 'YANDEX_METRICA' ? <>
          <TextField label="ID счётчика" value={counterId} onChange={(event) => setCounterId(event.target.value)} disabled={!editable || !!busy} inputMode="numeric" />
          <TextField label="ID offline-цели" value={offlineGoalId} onChange={(event) => setOfflineGoalId(event.target.value)} disabled={!editable || !!busy} inputMode="numeric" helperText="Числовой ID цели «JavaScript-событие» для записи в CRM. Пусто — выгрузка отключена." />
          <TextField label="Яндекс OAuth-токен" type="password" value={firstSecret} onChange={(event) => setFirstSecret(event.target.value)} disabled={!editable || !!busy} placeholder={hasSecret ? 'Задан — введите новый для замены' : ''} autoComplete="off" helperText="Токен сохраняется зашифрованным и больше не показывается." />
        </> : <>
          <TextField label={`Ссылка на бота ${title}`} value={publicUrl} onChange={(event) => setPublicUrl(event.target.value)} disabled={!editable || !!busy} placeholder={type === 'TELEGRAM' ? 'https://t.me/your_clinic_bot' : 'https://max.ru/your_clinic_bot'} />
          <TextField label="Публичный HTTPS-адрес CRM" value={webhookBaseUrl} onChange={(event) => setWebhookBaseUrl(event.target.value)} disabled={!editable || !!busy} placeholder="https://crm.example.ru" helperText="Адрес без /api, доступный провайдеру из интернета." />
          <TextField label={`${title} Bot Token`} type="password" value={firstSecret} onChange={(event) => setFirstSecret(event.target.value)} disabled={!editable || !!busy} placeholder={hasSecret ? 'Задан — введите новый для замены' : ''} autoComplete="off" fullWidth />
          {type === 'TELEGRAM' && <>
            <TextField label="Telegram relay URL" value={relayUrl} onChange={(event) => setRelayUrl(event.target.value)} disabled={!editable || !!busy} helperText="Необязательно. Если задан, отправка идёт через relay." />
            <TextField label="Telegram relay secret" type="password" value={secondSecret} onChange={(event) => setSecondSecret(event.target.value)} disabled={!editable || !!busy} autoComplete="off" />
          </>}
          <TextField label={`${title} webhook secret`} type="password" value={webhookSecret} onChange={(event) => setWebhookSecret(event.target.value)} disabled={!editable || !!busy} autoComplete="off" helperText="5–256 латинских букв, цифр, дефисов или подчёркиваний. Пустое поле сохраняет текущий секрет." />
          <FormControlLabel control={<Switch checked={reminderButtons} onChange={(event) => setReminderButtons(event.target.checked)} disabled={!editable || !!busy} />} label="Кнопки подтверждения под напоминаниями" />
        </>}
      </Box>
      <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 2 }}>{hasSecret ? `Секрет задан · версия ${integration?.secretVersion}` : 'Секрет ещё не задан'}</Typography>
      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      {editable && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 2 }}>
        <Button variant="contained" onClick={() => void save()} disabled={!!busy}>{busy === 'save' ? 'Сохранение…' : `Сохранить ${title}`}</Button>
        {status !== 'ACTIVE' ? <Button variant="outlined" onClick={() => void changeStatus('activate')} disabled={!!busy || !integration || !hasSecret}>Активировать</Button> : <Button variant="outlined" color="warning" onClick={() => void changeStatus('disable')} disabled={!!busy}>Отключить</Button>}
      </Box>}
      {type === 'YANDEX_METRICA' && integration && <MetricaOfflineConversionsPanel editable={editable} goalConfigured={Boolean(offlineGoalId.trim())} notify={notify} />}
      {integration?.publicKey && type !== 'YANDEX_METRICA' && <Box sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
        <Typography sx={{ fontWeight: 650, fontSize: 14, mb: 1 }}>Webhook для кабинета {title}</Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {type === 'MANGO' ? <><Button size="small" onClick={() => void copyWebhook('call')}>Копировать call webhook</Button><Button size="small" onClick={() => void copyWebhook('recording')}>Копировать recording webhook</Button></> : <>
            <Button size="small" onClick={() => void copyWebhook()}>Копировать webhook</Button>
            {editable && <Button size="small" variant="contained" onClick={() => void updateWebhook('connect')} disabled={!!busy || status !== 'ACTIVE'}>{busy === 'connect' ? 'Подключение…' : 'Подключить webhook'}</Button>}
            <Button size="small" onClick={() => void updateWebhook('check')} disabled={!!busy}>{busy === 'check' ? 'Проверка…' : 'Проверить'}</Button>
          </>}
        </Box>
        {type !== 'MANGO' && <>
          <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1 }}>Сохраните публичный адрес, токен и webhook secret, активируйте интеграцию, затем подключите webhook. Проверка запрашивает актуальную подписку у {title}.</Typography>
          {providerStatus && <Box sx={{ mt: 1.5 }}>
            <Chip size="small" color={providerStatus.state === 'CONNECTED' ? 'success' : providerStatus.state === 'ERROR' ? 'error' : 'default'} label={`Webhook: ${providerStatus.state === 'CONNECTED' ? 'подключён' : providerStatus.state === 'ERROR' ? 'ошибка' : 'не настроен'}`} />
            {providerStatus.detail && <Typography color="error" sx={{ fontSize: 13, mt: 1 }}>{providerStatus.detail}</Typography>}
            {providerStatus.pendingUpdates !== null && <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1 }}>Ожидают доставки: {providerStatus.pendingUpdates}</Typography>}
          </Box>}
        </>}
      </Box>}
    </Box>
  )
}
