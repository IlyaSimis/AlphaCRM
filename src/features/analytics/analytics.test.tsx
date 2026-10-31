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
})
