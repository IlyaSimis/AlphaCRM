import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import { describe, expect, it } from 'vitest'
import App from './App'

const renderApp = () => render(<ThemeProvider theme={createTheme()}><App /></ThemeProvider>)

describe('основные сценарии', () => {
  it('создаёт запись из расписания', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Новая запись')
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('скрывает клинические данные от регистратора', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByLabelText('Роль пользователя'))
    await user.click(screen.getByRole('option', { name: 'Регистратор' }))
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Клинические данные недоступны')).toBeInTheDocument()
    expect(screen.queryByText(/Тревожно-депрессивная/)).not.toBeInTheDocument()
  })

  it('подписывает и блокирует врачебный документ', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /начать приём/i }))
    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    expect(screen.getByText(/после подписания эту версию нельзя изменить/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /подписать и заблокировать/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText(/документ подписан 20\.10\.2026/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /создать дополнение/i })).toBeInTheDocument()
  })
})
