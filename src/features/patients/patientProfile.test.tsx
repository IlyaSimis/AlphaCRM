import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { enterDemo, goOffline, openPage, renderApp } from '../../test/helpers'

describe('расширенная карточка пациента', () => {
  it('shows every field group and saves profile details and representatives', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Дмитрий Волков'))
    await user.click(await screen.findByRole('button', { name: 'Редактировать' }))

    const labels = [
      'Отчество', 'Тип адреса', 'Регион', 'Населённый пункт', 'Улица', 'Дом', 'Квартира',
      'Место работы', 'Профессия/должность (по компании)', 'Профессия/должность (по НСИ)',
      'Дата начала работы', 'Бонусная карта', 'Документ', 'Серия и номер', 'Дата выдачи',
      'Код подразделения', 'Орган выдачи', 'Номер договора', 'Договор от', 'Договор до',
      'СНИЛС', 'ИНН', 'Реферальный агент', 'Теги', 'Номер', 'Заметки',
    ]
    for (const label of labels) expect(screen.getByLabelText(label)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Регион'), 'Москва')
    await user.type(screen.getByLabelText('Улица'), 'Академика Лазарева')
    await user.type(screen.getByLabelText('Документ'), 'Паспорт РФ')
    await user.type(screen.getByLabelText('Серия и номер'), '4523 293608')
    await user.type(screen.getByLabelText('СНИЛС'), '123-456-789 01')
    await user.type(screen.getByLabelText('ИНН'), '771234567890')
    await user.type(screen.getByLabelText('Теги'), 'VIP, семья')
    await user.type(screen.getByLabelText('Номер'), '0328')
    await user.type(screen.getByLabelText('Заметки'), 'Удобно после 18:00')
    await user.click(screen.getByRole('button', { name: 'Добавить представителя' }))
    await user.type(screen.getByLabelText('Представитель 1: ФИО'), 'Борис Волков')
    await user.type(screen.getByLabelText('Кем приходится'), 'Отец')
    await user.click(screen.getByRole('checkbox', { name: 'Основной' }))
    await user.click(screen.getByRole('checkbox', { name: 'SMS' }))
    await user.click(screen.getByRole('checkbox', { name: 'Push' }))
    await user.click(screen.getByRole('button', { name: 'Сохранить изменения' }))

    expect(await screen.findByText('123-456-789 01')).toBeInTheDocument()
    expect(screen.getByText('771234567890')).toBeInTheDocument()
    expect(screen.getByText('Паспорт РФ')).toBeInTheDocument()
    expect(screen.getByText('4523 293608')).toBeInTheDocument()
    expect(screen.getByText('VIP, семья')).toBeInTheDocument()
    expect(screen.getByText('Удобно после 18:00')).toBeInTheDocument()
    expect(screen.getByText('1. Борис Волков')).toBeInTheDocument()
    expect(screen.getByText('Отец')).toBeInTheDocument()
    expect(screen.getByText('Основной')).toBeInTheDocument()
    expect(screen.getByText('SMS · Push')).toBeInTheDocument()
  })
})
