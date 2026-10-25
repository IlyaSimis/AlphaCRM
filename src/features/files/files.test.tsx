import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PatientFileItem } from '../../api/types'
import { anna, jsonResponse, renderApp, withAuth } from '../../test/helpers'
import { fileRights, SIGN_NOTICE } from './fileRules'

const file = (patch: Partial<PatientFileItem> = {}): PatientFileItem => ({
  id: '00000000-0000-0000-0000-0000000f0001', patientId: anna.id, patientName: 'Анна Михайлова', originalName: 'заключение.pdf', sizeBytes: 250 * 1024,
  state: 'DRAFT', uploadedBy: '00000000-0000-0000-0000-0000000a0001', uploadedByName: 'Администратор', uploadedAt: '2026-10-20T07:00:00Z', ...patch,
})

const signed = file({ id: '00000000-0000-0000-0000-0000000f0002', originalName: 'выписка.pdf', state: 'SIGNED_LOCKED', signedBy: '00000000-0000-0000-0000-0000000a0004', signedByName: 'Александр Петров', signedAt: '2026-10-20T08:00:00Z', contentHash: 'ab' })

async function openAnna(user: ReturnType<typeof userEvent.setup>) {
  expect(await screen.findByText('API подключён')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Пациенты' }))
  await user.click(screen.getByText('Анна Михайлова'))
  return screen.findByRole('region', { name: 'Документы PDF' })
}

describe('PDF пациента', () => {
  it('rights by role: the cashier sees nothing, the manager only downloads', () => {
    expect(fileRights('CASHIER').visible).toBe(false)
    expect(fileRights('CASHIER').download).toBe(false)
    expect(fileRights('MANAGER')).toMatchObject({ visible: true, upload: false, download: true })
    expect(fileRights('MANAGER').sign(file())).toBe(false)
    expect(fileRights('DOCTOR').sign(file())).toBe(true)
    expect(fileRights('ADMINISTRATOR').sign(signed)).toBe(false)
  })

  it('the administrator uploads only a PDF and signs it after the notice', async () => {
    let rows: PatientFileItem[] = [signed]
    const sent: { file: File | null } = { file: null }
    let signedId = ''
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith(`/patients/${anna.id}/files`) && method === 'POST') {
        sent.file = (init?.body as FormData).get('file') as File
        rows = [file({ originalName: sent.file.name }), ...rows]
        return jsonResponse(rows[0], 201)
      }
      if (address.endsWith(`/patients/${anna.id}/files`)) return jsonResponse(rows)
      if (address.includes('/patient-files/') && address.endsWith('/sign')) {
        signedId = address.split('/').at(-2) ?? ''
        rows = rows.map((row) => row.id === signedId ? { ...row, state: 'SIGNED_LOCKED', signedByName: 'Администратор', signedAt: '2026-10-20T09:00:00Z' } : row)
        return jsonResponse(rows.find((row) => row.id === signedId))
      }
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup({ applyAccept: false })
    renderApp()
    const block = await openAnna(user)
    expect(await within(block).findByText(/выписка\.pdf/)).toBeInTheDocument()
    expect(within(block).getByText(/Подписал Александр Петров/)).toBeInTheDocument()
    expect(within(block).queryByRole('button', { name: 'Подписать выписка.pdf' })).not.toBeInTheDocument()

    // Не PDF — отказ ещё до запроса.
    await user.upload(within(block).getByLabelText('Файл PDF'), new File(['просто текст'], 'notes.txt', { type: 'text/plain' }))
    expect(await within(block).findByText('Можно загрузить только PDF')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/files') && init?.method === 'POST')).toBe(false)

    await user.upload(within(block).getByLabelText('Файл PDF'), new File(['%PDF-1.4 test'], 'заключение.pdf', { type: 'application/pdf' }))
    await waitFor(() => expect(sent.file).not.toBeNull())
    expect(sent.file?.name).toBe('заключение.pdf')
    const post = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/files') && init?.method === 'POST')
    // FormData уходит без JSON-заголовка: границу multipart ставит браузер.
    expect((post?.[1]?.headers as Record<string, string>)['Content-Type']).toBeUndefined()

    await user.click(await within(block).findByRole('button', { name: 'Подписать заключение.pdf' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(SIGN_NOTICE)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Подписать' }))
    await waitFor(() => expect(signedId).toBe('00000000-0000-0000-0000-0000000f0001'))
    await waitFor(() => expect(within(block).queryByRole('button', { name: 'Подписать заключение.pdf' })).not.toBeInTheDocument())
  })

  it('a manager downloads signed files but cannot upload or sign; the archive lists them', async () => {
    withAuth((address) => {
      if (address.endsWith(`/patients/${anna.id}/files`)) return jsonResponse([signed])
      if (address.endsWith('/patient-files/signed')) return jsonResponse([signed])
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      if (address.includes('/clinical/signed-documents')) return jsonResponse([])
      return jsonResponse([])
    }, 't.manager')
    const user = userEvent.setup()
    renderApp()
    const block = await openAnna(user)
    expect(await within(block).findByRole('button', { name: 'Скачать выписка.pdf' })).toBeInTheDocument()
    expect(within(block).queryByRole('button', { name: 'Загрузить' })).not.toBeInTheDocument()
    expect(within(block).queryByRole('button', { name: /^Подписать/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Подписанные документы' }))
    const archive = await screen.findByRole('region', { name: 'Подписанные PDF' })
    expect(await within(archive).findByText('Анна Михайлова · выписка.pdf')).toBeInTheDocument()
    expect(within(archive).getByText('Все подписанные файлы клиники.')).toBeInTheDocument()
  })

  it('the doctor uploads a conclusion from the visit page; it is tied to the appointment and prints from a new tab', async () => {
    const appointmentId = '00000000-0000-0000-0000-000000000301'
    let rows: PatientFileItem[] = [file({ id: '00000000-0000-0000-0000-0000000f0009', originalName: 'другой приём.pdf', appointmentId: '00000000-0000-0000-0000-000000000399' })]
    let form: FormData | null = null
    const encounter = { encounterId: 'e1', appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', status: 'IN_PROGRESS', startedAt: '2026-10-20T09:01:00Z', documents: [] }
    withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith(`/patients/${anna.id}/files`) && method === 'POST') {
        form = init?.body as FormData
        const created = file({ id: '00000000-0000-0000-0000-0000000f0010', originalName: 'заключение.pdf', appointmentId: String(form.get('appointmentId')) })
        rows = [created, ...rows]
        return jsonResponse(created, 201)
      }
      if (address.endsWith(`/patients/${anna.id}/files`)) return jsonResponse(rows)
      if (address.endsWith('/clinical/encounters') && method === 'POST') return jsonResponse(encounter, 201)
      if (address.includes('/clinical/patients/')) return jsonResponse([encounter])
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([{ id: appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T09:00:00Z', endsAt: '2026-10-20T09:50:00Z', serviceName: 'Первичный приём', status: 'ARRIVED' }])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const tab = { addEventListener: vi.fn(), focus: vi.fn(), print: vi.fn() }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    const block = await screen.findByRole('region', { name: 'Заключения и файлы приёма' })
    // Файлы других приёмов здесь не показываются — они в карточке пациента.
    expect(await within(block).findByText('К этому приёму файлов нет.')).toBeInTheDocument()
    expect(within(block).queryByText(/другой приём/)).not.toBeInTheDocument()

    await user.upload(within(block).getByLabelText('Файл PDF'), new File(['%PDF-1.4 conclusion'], 'заключение.pdf', { type: 'application/pdf' }))
    await waitFor(() => expect(form).not.toBeNull())
    expect(form!.get('appointmentId')).toBe(appointmentId)
    await user.click(await within(block).findByRole('button', { name: 'Печать заключение.pdf' }))
    expect(open).toHaveBeenCalledWith('/api/v1/patient-files/00000000-0000-0000-0000-0000000f0010/inline', '_blank')
    // Печать — после загрузки вкладки.
    const onLoad = tab.addEventListener.mock.calls.find(([event]) => event === 'load')?.[1] as () => void
    onLoad()
    expect(tab.print).toHaveBeenCalled()
    open.mockRestore()
  })

  it('the cashier sees no PDF block on the card', async () => {
    const fetchMock = withAuth((address) => {
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse([])
    }, 't.cashier')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByRole('heading', { name: 'Анна Михайлова' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Документы PDF' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/files'))).toBe(false)
  })
})
