import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FilePreviewDialog, type FilePreviewSource } from './FilePreviewDialog'

const createDescriptor = Object.getOwnPropertyDescriptor(URL, 'createObjectURL')
const revokeDescriptor = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL')
const createObjectURL = vi.fn(() => 'blob:alpha-preview')
const revokeObjectURL = vi.fn()

beforeEach(() => {
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL })
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL })
})

afterEach(() => {
  if (createDescriptor) Object.defineProperty(URL, 'createObjectURL', createDescriptor)
  else Reflect.deleteProperty(URL, 'createObjectURL')
  if (revokeDescriptor) Object.defineProperty(URL, 'revokeObjectURL', revokeDescriptor)
  else Reflect.deleteProperty(URL, 'revokeObjectURL')
  createObjectURL.mockClear()
  revokeObjectURL.mockClear()
})

describe('встроенный просмотр файлов', () => {
  it('opens PDF in the dialog and revokes its temporary URL after closing', async () => {
    const source: FilePreviewSource = { title: 'заключение.pdf', kind: 'pdf', load: vi.fn().mockResolvedValue(new Blob(['%PDF-1.4'], { type: 'application/pdf' })) }
    const onClose = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(<FilePreviewDialog source={source} onClose={onClose} />)

    expect(await screen.findByTitle('Просмотр: заключение.pdf')).toHaveAttribute('src', 'blob:alpha-preview')
    expect(source.load).toHaveBeenCalledOnce()
    await user.click(screen.getByRole('button', { name: 'Закрыть просмотр' }))
    expect(onClose).toHaveBeenCalledOnce()

    rerender(<FilePreviewDialog source={null} onClose={onClose} />)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:alpha-preview')
  })

  it('opens an image inside AlphaCRM rather than a new tab', async () => {
    const source: FilePreviewSource = { title: 'снимок.png', kind: 'image', load: vi.fn().mockResolvedValue(new Blob(['image'], { type: 'image/png' })) }
    render(<FilePreviewDialog source={source} onClose={() => undefined} />)

    expect(await screen.findByRole('img', { name: 'снимок.png' })).toHaveAttribute('src', 'blob:alpha-preview')
    expect(screen.queryByTitle('Просмотр: снимок.png')).not.toBeInTheDocument()
  })
})
