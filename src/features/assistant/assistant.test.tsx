import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

describe('AI navigator', () => {
  it('opens overdue tasks locally in the sandbox without a model request', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')

    await user.click(screen.getByRole('button', { name: 'ИИ-навигатор' }))
    await user.type(screen.getByLabelText('Что открыть?'), 'просроченные задачи')
    await user.click(screen.getByRole('button', { name: 'Открыть раздел' }))

    expect(await screen.findByRole('heading', { name: 'Моё пространство', level: 4 })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Срок' })).toHaveTextContent('Просрочено')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'ИИ-навигатор' })).not.toBeInTheDocument())
  })

  it('uses the model only for an ambiguous request and opens its whitelisted destination', async () => {
    const fetchMock = withAuth((address, init) => {
      if (address.endsWith('/assistant/status')) return jsonResponse({ enabled: true, model: 'test-model' })
      if (address.endsWith('/assistant/navigate') && init?.method === 'POST') {
        return jsonResponse({ destinationId: 'cashier', source: 'AI' })
      }
      return jsonResponse([])
    })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'ИИ-навигатор' }))
    await user.type(await screen.findByLabelText('Что открыть?'), 'где принять деньги')
    await user.click(screen.getByRole('button', { name: 'Открыть раздел' }))

    expect(await screen.findByRole('heading', { name: 'Касса', level: 5 })).toBeInTheDocument()
    const navigation = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/assistant/navigate') && init?.method === 'POST')
    expect(navigation).toBeDefined()
    const body = JSON.parse(String(navigation?.[1]?.body))
    expect(body.query).toBe('где принять деньги')
    expect(body.destinations.some((item: { id: string }) => item.id === 'cashier')).toBe(true)
    expect(JSON.stringify(body)).not.toContain('Анна')
  })
})
