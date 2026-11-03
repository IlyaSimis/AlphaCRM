import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { chooseRole, enterDemo, goOffline, jsonResponse, renderApp, openPage, menuHas, withAuth } from '../../test/helpers'

const overview = {
  from: '2026-09-23', to: '2026-10-20', currency: 'RUB',
  totals: {
    appointments: 12, attended: 8, noShow: 2, cancelled: 2, revenueMinor: 1250000, payments: 4, averageCheckMinor: 450000,
    debtMinor: 300000, unpaidCharges: 3, openPriceMinor: 300000, refundedMinor: 0, newPatients: 3, returningPatients: 2,
    completedPatients: 5, withoutNextVisit: 2, occupiedMinutes: 600, availableMinutes: 1200,
    waitlistActive: 2, waitlistBooked: 3, waitlistPeriodTotal: 4,
  },
  weeks: [{ weekStart: '2026-10-19', appointments: 12, attended: 8, noShow: 2, cancelled: 2, revenueMinor: 1250000, occupiedMinutes: 600, availableMinutes: 1200 }],
  practitioners: [{ id: '00000000-0000-0000-0000-000000000201', label: 'Александр Петров', specialty: 'Психиатр', occupiedMinutes: 600, availableMinutes: 1200 }],
  rooms: [{ id: 'room:101', label: '101', specialty: null, occupiedMinutes: 600, availableMinutes: 1200 }],
  nextSlots: [{ specialty: 'Психиатр', practitioners: 1, averageHours: 2.5, nextAvailableAt: '2026-10-21T09:00:00Z' }],
  funnel: { contacts: 10, booked: 7, visited: 5, paid: 4 },
  queues: { unclosedEncounters: 1, unsignedDocuments: 2, unpaidCharges: 3 },
}

describe('analytics', () => {
  it('a doctor and support see no clinic analytics; a manager sees weekly trends', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(await menuHas(user, 'Аналитика')).toBe(false)
    await chooseRole(user, 'SUPPORT')
    expect(await menuHas(user, 'Аналитика')).toBe(false)
    await chooseRole(user, 'MANAGER')
    await openPage(user, 'Аналитика')
    expect(screen.getByRole('heading', { name: 'Показатели клиники' })).toBeInTheDocument()
    expect(screen.getByText('явка')).toBeInTheDocument()
    expect(screen.getByLabelText('20.10: всего 5')).toBeInTheDocument()
    expect(screen.getByText('Тренды по неделям')).toBeInTheDocument()
    expect(screen.getByText('Итоги по врачам')).toBeInTheDocument()
    expect(screen.getByText('4 недели')).toBeInTheDocument()
    await openPage(user, 'Журнал аудита')
    expect(screen.getByText(/доступен только при подключённом API/)).toBeInTheDocument()
  })

  it('shows financial, retention, waitlist and queue aggregates without patient data', async () => {
    withAuth((address: string) => {
      if (address.includes('/analytics/appointments')) return jsonResponse({ rows: [] })
      if (address.includes('/analytics/overview')) return jsonResponse(overview)
      if (address.includes('/practitioners')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000201', fullName: 'Александр Петров', specialty: 'Психиатр', room: '101', workingHours: [] }])
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/registry/') || address.includes('/workspace/') || address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.manager')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Аналитика')
    expect(await screen.findByText('Воронка новых пациентов')).toBeInTheDocument()
    expect(screen.getByText('новые / повторные').closest('.metric')).toHaveTextContent('3 / 2')
    expect(screen.getByText('выручка').closest('.metric')?.textContent).toMatch(/12\s500/)
    expect(screen.getByText('Запись и удержание')).toBeInTheDocument()
    expect(screen.getByText('Ближайшее свободное время по специальности')).toBeInTheDocument()
    expect(screen.getByText('Операционные очереди')).toBeInTheDocument()
    expect(screen.queryByText('Анна Михайлова')).not.toBeInTheDocument()
  })

  it('drills down from a service to its source appointments and patient card', async () => {
    const totals = { appointments: 2, visits: 1, uniquePatients: 1, newPatients: 0, returningPatients: 1, noShow: 1, cancelled: 0, withoutNextVisit: 1, revenueMinor: 450000, payments: 1, averageCheckMinor: 450000 }
    const fetchMock = withAuth((address: string) => {
      if (address.includes('/analytics/services/appointments')) return jsonResponse({
        from: '2026-09-23', to: '2026-10-20', serviceKey: '00000000-0000-0000-0000-000000000601', serviceName: 'Первичный приём',
        appointments: [{ appointmentId: 'a-service', patientId: '00000000-0000-0000-0000-000000000101', patientName: 'Анна Михайлова', practitionerId: '00000000-0000-0000-0000-000000000201', practitionerName: 'Александр Петров', startsAt: '2026-10-20T09:00:00Z', status: 'COMPLETED', chargeId: 'charge-1', chargeStatus: 'PAID', amountMinor: 450000, currency: 'RUB', withoutNextVisit: true }],
      })
      if (address.includes('/analytics/services')) return jsonResponse({
        from: '2026-09-23', to: '2026-10-20', previousFrom: '2026-08-26', previousTo: '2026-09-22', currency: 'RUB', totals, previousTotals: { ...totals, visits: 0, revenueMinor: 0 },
        rows: [{ ...totals, serviceKey: '00000000-0000-0000-0000-000000000601', serviceName: 'Первичный приём', previousVisits: 0, previousRevenueMinor: 0 }],
      })
      if (address.includes('/analytics/appointments')) return jsonResponse({ rows: [] })
      if (address.includes('/analytics/overview')) return jsonResponse(overview)
      if (address.includes('/practitioners')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000201', fullName: 'Александр Петров', specialty: 'Психиатр', room: '101', workingHours: [] }])
      if (address.includes('/patients')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000101', fullName: 'Анна Михайлова', birthDate: '1991-05-14', contacts: [], consents: [] }])
      if (address.includes('/registry/') || address.includes('/workspace/') || address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.manager', { services: null })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Аналитика')
    await user.click(screen.getByRole('button', { name: 'Услуги' }))
    await user.click(await screen.findByRole('button', { name: 'Открыть приёмы: Первичный приём' }))

    const details = await screen.findByRole('dialog', { name: 'Приёмы по услуге Первичный приём' })
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    expect(screen.getByText('PAID')).toBeInTheDocument()
    expect(screen.getByText('Нет')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/analytics/services/appointments') && String(url).includes('serviceKey=00000000-0000-0000-0000-000000000601'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Анна Михайлова' }))
    expect(await screen.findByRole('heading', { name: 'Анна Михайлова' })).toBeInTheDocument()
    expect(details).not.toBeVisible()
  })

  it('shows CRM acquisition cohorts and Yandex Metrica aggregates without patient identities', async () => {
    const fetchMock = withAuth((address: string) => {
      if (address.includes('/analytics/acquisition')) return jsonResponse({
        from: '2026-09-23', to: '2026-10-20', currency: 'RUB',
        referralSources: [
          { source: 'WEBSITE', contacts: 10, booked: 7, visited: 5, paid: 4, revenueMinor: 1800000 },
          { source: 'RECOMMENDATION', contacts: 4, booked: 3, visited: 2, paid: 2, revenueMinor: 900000 },
        ],
        web: {
          state: 'READY', detail: null,
          traffic: { visits: 1200, users: 900, pageviews: 2800, newVisitorsPercent: 62.5, bounceRate: 31.2, pageDepth: 2.3, averageVisitDurationSeconds: 185, goalReaches: 84, goalConversionRate: 7 },
          daily: [{ date: '2026-10-20', visits: 50, users: 42, goalReaches: 4 }],
          sources: [{ source: 'Переходы из поисковых систем', visits: 700, users: 520, goalReaches: 45 }],
        },
      })
      if (address.includes('/analytics/appointments')) return jsonResponse({ rows: [] })
      if (address.includes('/analytics/overview')) return jsonResponse(overview)
      if (address.includes('/practitioners')) return jsonResponse([])
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/registry/') || address.includes('/workspace/') || address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.manager')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Аналитика')
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/analytics/acquisition'))).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Привлечение' }))
    expect(await screen.findByText('Источники пациентов в CRM')).toBeInTheDocument()
    const website = screen.getByText('Сайт клиники').closest('tr') as HTMLElement
    expect(website).toHaveTextContent('70%')
    expect(website.textContent).toMatch(/18\s000/)
    expect(screen.getByText('конверсия в цель').closest('.metric')).toHaveTextContent('7%')
    expect(screen.getByText('Переходы из поисковых систем')).toBeInTheDocument()
    expect(screen.queryByLabelText('Врач')).not.toBeInTheDocument()
    expect(screen.queryByText('Анна Михайлова')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/analytics/acquisition?from=2026-09-23&to=2026-10-20'))).toBe(true)
  })
})
