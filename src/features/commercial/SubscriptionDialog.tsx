import { useEffect, useState } from 'react'
import { CheckCircleRounded, OpenInNewRounded, WorkspacePremiumRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, LinearProgress, Stack, Typography } from '@mui/material'
import { createSubscriptionCheckout, loadSubscription, type Subscription } from '../../api/commercial'

const money = (minor: number, currency: string) => new Intl.NumberFormat('ru-RU', { style: 'currency', currency, maximumFractionDigits: 0 }).format(minor / 100)
const paymentLabels: Record<string, string> = { PENDING: 'Ожидает оплаты', SUCCEEDED: 'Оплачен', CANCELED: 'Отменён', FAILED: 'Ошибка' }

/** Тариф клиники: оплата идёт на hosted checkout, приложение не принимает данные карты. */
export function SubscriptionDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [subscription, setSubscription] = useState<Subscription | null>(null)
  const [loading, setLoading] = useState(false)
  const [paying, setPaying] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    loadSubscription().then((next) => { if (!cancelled) setSubscription(next) }).catch((reason: Error) => { if (!cancelled) setError(reason.message) }).finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [open])

  const pay = async (planCode: string) => {
    setPaying(planCode)
    setError('')
    try {
      const checkout = await createSubscriptionCheckout(planCode)
      window.location.assign(checkout.checkoutUrl)
    } catch (reason) {
      setError((reason as Error).message)
      setPaying('')
    }
  }

  return (
    <Dialog open={open} onClose={paying ? undefined : onClose} fullWidth maxWidth="md" aria-labelledby="subscription-title">
      <DialogTitle id="subscription-title"><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><WorkspacePremiumRounded color="primary" /><span>Тариф и оплата</span></Stack></DialogTitle>
      <DialogContent>
        {loading && <LinearProgress sx={{ mb: 2 }} />}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {subscription && <>
          <Box className="panel" sx={{ p: 2, mb: 2 }}>
            <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="h6">Текущий тариф: {subscription.planName}</Typography>
              <Chip size="small" color="primary" label={`${subscription.remainingAiRequests} ИИ-запросов доступно`} />
            </Stack>
            <Typography sx={{ color: 'text.secondary', mt: 1 }}>Использовано {subscription.usedAiRequests} из {subscription.monthlyAiRequests} в текущем календарном месяце. Локальные переходы не учитываются.</Typography>
            {subscription.currentPeriodEnd && <Typography sx={{ color: 'text.secondary', mt: 0.5 }}>Оплаченный период до {new Date(subscription.currentPeriodEnd).toLocaleDateString('ru-RU')}.</Typography>}
          </Box>
          {!subscription.checkoutEnabled && <Alert severity="info" sx={{ mb: 2 }}>Онлайн-оплата ещё не подключена. Тарифы и лимиты доступны для просмотра; списаний не производится.</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 1.5 }}>
            {subscription.plans.map((plan) => {
              const current = plan.code === subscription.planCode
              return <Box key={plan.code} className="panel" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1, borderColor: current ? 'primary.main' : 'divider' }}>
                <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}><Typography variant="h6" sx={{ flex: 1 }}>{plan.name}</Typography>{current && <CheckCircleRounded color="primary" />}</Stack>
                <Typography sx={{ fontWeight: 750 }}>{plan.priceMinor ? `${money(plan.priceMinor, plan.currency)} / 30 дней` : 'Бесплатно'}</Typography>
                <Typography sx={{ color: 'text.secondary', fontSize: 14, flex: 1 }}>{plan.description}</Typography>
                <Chip size="small" variant="outlined" label={`${plan.monthlyAiRequests.toLocaleString('ru-RU')} ИИ-запросов / месяц`} />
                <Button variant={current ? 'outlined' : 'contained'} endIcon={!current && plan.code !== 'FREE' ? <OpenInNewRounded /> : undefined} disabled={current || plan.code === 'FREE' || !subscription.checkoutEnabled || Boolean(paying)} onClick={() => void pay(plan.code)}>{current ? 'Текущий тариф' : paying === plan.code ? 'Создаю оплату…' : 'Перейти к оплате'}</Button>
              </Box>
            })}
          </Box>
          <Typography sx={{ fontSize: 13, color: 'text.secondary', mt: 2 }}>Оплата проходит на защищённой странице ЮKassa. Тариф активируется после подтверждения платежа сервером. Автоматического продления и сохранения карты в AlphaCRM нет.</Typography>
          {subscription.payments.length > 0 && <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="h6" sx={{ mb: 1 }}>Последние платежи</Typography>
            <Stack spacing={0.5}>{subscription.payments.map((payment) => <Box key={payment.id} sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', py: 0.7 }}>
              <Typography sx={{ minWidth: 110 }}>{new Date(payment.createdAt).toLocaleDateString('ru-RU')}</Typography>
              <Typography sx={{ flex: 1 }}>{subscription.plans.find((plan) => plan.code === payment.planCode)?.name ?? payment.planCode}</Typography>
              <Typography>{money(payment.amountMinor, payment.currency)}</Typography>
              <Chip size="small" variant="outlined" label={paymentLabels[payment.status] ?? payment.status} />
            </Box>)}</Stack>
          </>}
        </>}
      </DialogContent>
      <DialogActions><Button onClick={onClose} disabled={Boolean(paying)}>Закрыть</Button></DialogActions>
    </Dialog>
  )
}
