import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

type User = ReturnType<typeof userEvent.setup>

async function fillNote(user: User) {
  await user.type(screen.getByLabelText('Жалобы и анамнез'), 'Нарушение сна')
  await user.type(screen.getByLabelText('Психический статус'), 'Контакт продуктивный')
  await user.type(screen.getByLabelText('Диагноз'), 'Синтетический диагноз')
  await user.type(screen.getByLabelText('Назначения и рекомендации'), 'Контроль через 2 недели')
}

describe('patient visit', () => {
  it('opens the visit of the selected appointment and signs the document from the form', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    expect(screen.getByRole('heading', { name: 'Анна Михайлова' })).toBeInTheDocument()
    expect(screen.getByText(/ПЕРВИЧНЫЙ ПРИЁМ · .* · 09:00–09:50/)).toBeInTheDocument()
    expect(screen.getByLabelText('Диагноз')).toHaveValue('')

    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    expect(await screen.findByText('Заполните все четыре раздела заметки')).toBeInTheDocument()

    await fillNote(user)
    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    expect(screen.getByText(/после подписания эту версию нельзя изменить/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /подписать и заблокировать/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText(/документ подписан/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Диагноз')).toBeDisabled()
  })

  it('an amendment opens a new draft while the original stays signed', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    await fillNote(user)
    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    await user.click(screen.getByRole('button', { name: /подписать и заблокировать/i }))
    await user.click(await screen.findByRole('button', { name: /создать дополнение/i }))
    expect(screen.getByText('Дополнение · черновик', { selector: '.MuiChip-label' })).toBeInTheDocument()
    expect(screen.getByText(/Документ приёма · подписан/)).toBeInTheDocument()
    expect(screen.getByLabelText('Диагноз')).toBeEnabled()
  })

  it('on the server starts a visit, saves the draft from the form and signs it', async () => {
    const appointmentId = '00000000-0000-0000-0000-000000000301'
    const fetchMock = withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      const method = init?.method ?? 'GET'
      const encounter = { encounterId: 'e1', appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', status: 'IN_PROGRESS', startedAt: '2026-10-20T09:01:00Z', documents: [] }
      if (address.endsWith('/clinical/encounters') && method === 'POST') return jsonResponse(encounter, 201)
      if (address.includes('/clinical/patients/')) return jsonResponse([encounter])
      if (address.endsWith('/note')) return jsonResponse({ ...encounter, ...JSON.parse(String(init?.body)) })
      if (address.endsWith('/documents')) return jsonResponse({ id: 'd1', state: 'DRAFT', templateVersion: 'v2.4' }, 201)
      if (address.endsWith('/sign')) return jsonResponse({ id: 'd1', state: 'SIGNED_LOCKED', templateVersion: 'v2.4', signedAt: '2026-10-20T09:40:00Z' })
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([{ id: appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T09:00:00Z', endsAt: '2026-10-20T09:50:00Z', serviceName: 'Первичный приём', status: 'ARRIVED' }])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    expect(await screen.findByText('Данные приёма на сервере')).toBeInTheDocument()
    const start = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/clinical/encounters') && init?.method === 'POST')
    expect(JSON.parse(String(start?.[1]?.body))).toEqual({ appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201' })

    await fillNote(user)
    await user.click(screen.getByRole('button', { name: 'Сохранить черновик' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/note'))).toBe(true))
    const note = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/note'))
    expect(JSON.parse(String(note?.[1]?.body))).toEqual({ complaints: 'Нарушение сна', mentalStatus: 'Контакт продуктивный', diagnosis: 'Синтетический диагноз', recommendations: 'Контроль через 2 недели' })

    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    await user.click(screen.getByRole('button', { name: /подписать и заблокировать/i }))
    expect(await screen.findByText(/документ подписан/i)).toBeInTheDocument()
    const paths = fetchMock.mock.calls.map(([url]) => String(url))
    expect(paths.some((path) => path.endsWith('/clinical/encounters/e1/documents'))).toBe(true)
    expect(paths.some((path) => path.endsWith('/clinical/documents/d1/sign'))).toBe(true)
  })
})
