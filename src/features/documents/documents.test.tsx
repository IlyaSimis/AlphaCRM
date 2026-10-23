import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../../api/client'
import type { SignedDocumentItem } from '../../api/types'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

const original: SignedDocumentItem = {
  id: '00000000-0000-0000-0000-00000000d001', encounterId: 'e1', appointmentId: 'a1', patientId: anna.id, patientName: 'Анна Михайлова',
  practitionerId: PETROV_ID, practitionerName: 'Александр Петров', signedAt: '2026-10-20T09:40:00Z', templateVersion: 'v2.4', amendsDocumentId: null,
  complaints: 'Жалобы из снимка', mentalStatus: 'Статус из снимка', diagnosis: 'Диагноз из снимка', recommendations: 'Рекомендации из снимка',
}
const amendment: SignedDocumentItem = { ...original, id: '00000000-0000-0000-0000-00000000d002', signedAt: '2026-10-21T10:00:00Z', amendsDocumentId: original.id, diagnosis: 'Уточнённый диагноз' }
const lebedeva: SignedDocumentItem = { ...original, id: '00000000-0000-0000-0000-00000000d003', practitionerId: LEBEDEVA_ID, practitionerName: 'Ольга Лебедева', diagnosis: 'Диагноз Лебедевой' }

function archive() {
  return (address: string) => {
    const url = new URL(address, 'http://localhost')
    if (url.pathname.endsWith('/clinical/signed-documents')) {
      const doctor = url.searchParams.get('practitionerId')
      return jsonResponse([amendment, original, lebedeva].filter((doc) => !doctor || doc.practitionerId === doctor))
    }
    const card = /\/clinical\/signed-documents\/(.+)$/.exec(url.pathname)
    if (card) return jsonResponse([amendment, original, lebedeva].find((doc) => doc.id === card[1]) ?? {}, card[1] ? 200 : 404)
    if (address.includes('/patients')) return jsonResponse([anna])
    if (address.includes('/appointments')) return jsonResponse([])
    return jsonResponse({}, 404)
  }
}

describe('signed documents', () => {
  it('a manager sees the signed snapshot text, filters by doctor and opens an amendment', async () => {
    const fetchMock = withAuth(archive(), 't.manager')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Подписанные документы' }))
    expect(await screen.findByText(/Уточнённый диагноз/)).toBeInTheDocument()
    expect(screen.getByText(/Диагноз: Диагноз из снимка/)).toBeInTheDocument()
    expect(screen.getByText(/Диагноз Лебедевой/)).toBeInTheDocument()
    expect(screen.getAllByText(/Дополнение к документу от/).length).toBe(1)
    // Страница только для чтения: подписать или изменить отсюда нельзя.
    expect(screen.queryByRole('button', { name: /подписать|сохранить|дополнение/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Врач' }))
    await user.click(screen.getByRole('option', { name: 'Ольга Лебедева' }))
    await waitFor(() => expect(screen.queryByText(/Уточнённый диагноз/)).not.toBeInTheDocument())
    expect(screen.getByText(/Диагноз Лебедевой/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes(`/clinical/signed-documents?practitionerId=${LEBEDEVA_ID}`))).toBe(true)

    await user.click(screen.getByRole('combobox', { name: 'Врач' }))
    await user.click(screen.getByRole('option', { name: 'Все врачи' }))
    await user.click((await screen.findAllByRole('button', { name: /^Открыть документ Анна Михайлова/ }))[0])
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/Дополнение к документу от/)).toBeInTheDocument()
    expect(within(dialog).getByText('Уточнённый диагноз')).toBeInTheDocument()
    expect(within(dialog).getByText('Жалобы из снимка')).toBeInTheDocument()
    expect(within(dialog).getByText('Александр Петров')).toBeInTheDocument()
    expect(within(dialog).getByText('v2.4')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Открыть исходный документ' }))
    expect(await within(screen.getByRole('dialog')).findByText('Диагноз из снимка')).toBeInTheDocument()
    // Карточка читается отдельным запросом — сервер пишет чтение в аудит.
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith(`/clinical/signed-documents/${original.id}`))).toBe(true)
  })

  it('a doctor sees their own archive without a doctor filter', async () => {
    withAuth(archive(), 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Подписанные документы' }))
    expect(await screen.findByText(/Документы, которые вы подписали/)).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Врач' })).not.toBeInTheDocument()
  })

  it('administrator and cashier do not see the menu item; the sandbox manager sees an explanation', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    expect(screen.queryByRole('button', { name: 'Подписанные документы' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'CASHIER')
    expect(screen.queryByRole('button', { name: 'Подписанные документы' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'MANAGER')
    await user.click(screen.getByRole('button', { name: 'Подписанные документы' }))
    expect(screen.getByText(/доступны только при подключённом API/)).toBeInTheDocument()
  })
})
