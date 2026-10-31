export type SupportedFilePreview = 'pdf' | 'image' | 'office'

/** Форматы, которые сервер может безопасно отдать для встроенного просмотра. */
export function supportedFilePreview(name: string): SupportedFilePreview | null {
  const extension = name.toLocaleLowerCase('ru-RU').match(/\.([a-z0-9]+)$/)?.[1]
  if (extension === 'pdf') return 'pdf'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(extension ?? '')) return 'image'
  if (extension === 'docx' || extension === 'xlsx') return 'office'
  return null
}
