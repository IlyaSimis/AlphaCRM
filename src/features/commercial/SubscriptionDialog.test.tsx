import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../../test/helpers'
import { SubscriptionDialog } from './SubscriptionDialog'

describe('subscription dialog', () => {
  it('shows plans honestly without enabling payments when the provider is not configured', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      planCode: 'FREE', planName: 'Старт', currentPeriodEnd: null, monthlyAiRequests: 100, usedAiRequests: 0, remainingAiRequests: 100,
      checkoutEnabled: false,
      plans: [
        { code: 'FREE', name: 'Старт', description: 'Знакомство', priceMinor: 0, currency: 'RUB', monthlyAiRequests: 100 },
        { code: 'TEAM', name: 'Команда', description: 'Для команды', priceMinor: 199000, currency: 'RUB', monthlyAiRequests: 3000 },
        { code: 'CLINIC', name: 'Клиника', description: 'Для клиники', priceMinor: 499000, currency: 'RUB', monthlyAiRequests: 10000 },
      ],
      payments: [],
    })))
    render(<SubscriptionDialog open onClose={vi.fn()} />)

    expect(await screen.findByText('Онлайн-оплата ещё не подключена. Тарифы и лимиты доступны для просмотра; списаний не производится.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Текущий тариф' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: 'Перейти к оплате' })).toHaveLength(2)
    for (const button of screen.getAllByRole('button', { name: 'Перейти к оплате' })) expect(button).toBeDisabled()
    expect(screen.queryByText('Оплачен')).not.toBeInTheDocument()
  })
})
