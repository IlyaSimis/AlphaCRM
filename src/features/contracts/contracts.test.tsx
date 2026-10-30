import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ContractTemplateItem, PatientContractItem } from '../../api/types'
import { anna, jsonResponse, openPage, renderApp, withAuth } from '../../test/helpers'

const template: ContractTemplateItem = {
  id: '00000000-0000-0000-0000-0000000c0001', name: 'Договор на приём', originalName: 'договор.docx', version: 1,
  placeholders: ['patient.fullName', 'contract.number', 'service.name', 'service.price', 'custom.note'], createdAt: '2026-10-30T08:00:00Z',
}

describe('Договоры пациента', () => {
  it('uploads DOCX and creates a contract with automatic and custom fields including open price', async () => {
    let templates: ContractTemplateItem[] = []
    let contracts: PatientContractItem[] = []
    let uploaded: FormData | null = null
    let created: { templateId: string; serviceId?: string; values: Record<string, string> } | null = null
    withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/contract-templates') && method === 'POST') {
        uploaded = init?.body as FormData
        templates = [template]
        return jsonResponse(template, 201)
      }
      if (address.endsWith('/contract-templates')) return jsonResponse(templates)
      if (address.endsWith(`/patients/${anna.id}/contracts`) && method === 'POST') {
        created = JSON.parse(String(init?.body))
        const contract: PatientContractItem = {
          id: '00000000-0000-0000-0000-0000000c0002', patientId: anna.id, contractNumber: 'Д-2026-00001',
          templateName: template.name, serviceName: 'Консультация', pdfAvailable: false,
          createdAt: '2026-10-30T08:01:00Z', createdByName: 'Администратор',
        }
        contracts = [contract]
        return jsonResponse(contract, 201)
      }
      if (address.endsWith(`/patients/${anna.id}/contracts`)) return jsonResponse(contracts)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse([], 200)
    }, 'admin', { services: [{ id: '00000000-0000-0000-0000-000000000601', name: 'Консультация', durationMinutes: 30, priceMinor: null, priceMode: 'OPEN', currency: 'RUB', active: true }] })
    const user = userEvent.setup({ applyAccept: false })
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Шаблоны договоров')
    const templatesPage = await screen.findByRole('region', { name: 'Шаблоны договоров' })
    expect(await within(templatesPage).findByText('Шаблонов пока нет')).toBeInTheDocument()

    await user.click(within(templatesPage).getByRole('button', { name: 'Загрузить шаблон' }))
    const uploadDialog = screen.getByRole('dialog', { name: 'Шаблон договора' })
    await user.upload(within(uploadDialog).getByLabelText('Файл шаблона DOCX'), new File(['PK docx'], 'договор.docx'))
    await waitFor(() => expect(within(uploadDialog).getByDisplayValue('договор')).toBeInTheDocument())
    await user.click(within(uploadDialog).getByRole('button', { name: 'Загрузить' }))
    await waitFor(() => expect(uploaded).not.toBeNull())
    expect(uploaded!.get('name')).toBe('договор')
    expect((uploaded!.get('file') as File).name).toBe('договор.docx')
    expect(await within(templatesPage).findByText(/Договор на приём · версия 1/)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Шаблон договора' })).not.toBeInTheDocument())

    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    const block = await screen.findByRole('region', { name: 'Договоры пациента' })
    expect(await within(block).findByText('Договоров пока нет.')).toBeInTheDocument()
    expect(within(block).queryByRole('button', { name: 'Загрузить шаблон' })).not.toBeInTheDocument()

    await user.click(await within(block).findByRole('button', { name: 'Сформировать' }))
    const dialog = screen.getByRole('dialog', { name: 'Сформировать договор' })
    expect(within(dialog).queryByLabelText(/patient.fullName/)).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText(/contract.number/)).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /Услуга/ }))
    await user.click(screen.getByRole('option', { name: 'Консультация' }))
    await user.type(within(dialog).getByLabelText(/Стоимость услуги/), '5 000 ₽')
    await user.type(within(dialog).getByLabelText(/custom.note/), 'Особые условия')
    await user.click(within(dialog).getByRole('button', { name: 'Сформировать' }))
    await waitFor(() => expect(created).not.toBeNull())
    expect(created).toEqual({
      templateId: template.id,
      serviceId: '00000000-0000-0000-0000-000000000601',
      values: { 'service.price': '5 000 ₽', 'custom.note': 'Особые условия' },
    })
    expect(await within(block).findByText(/Д-2026-00001 · Договор на приём/)).toBeInTheDocument()
    expect(await within(block).findByRole('button', { name: 'Скачать DOCX Д-2026-00001' })).toBeInTheDocument()
    expect(within(block).queryByRole('button', { name: 'Скачать PDF Д-2026-00001' })).not.toBeInTheDocument()
  })
})
