import { useRef, useState } from 'react'
import { AddRounded, UploadFileRounded } from '@mui/icons-material'
import { Alert, Box, Button, Stack, Typography } from '@mui/material'
import type { ReceiptResult } from '../../api/inventory'
import type { ServiceItem } from '../../api/types'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { BalancesTab } from './BalancesTab'
import { DocumentsTab } from './DocumentsTab'
import { ItemsTab } from './ItemsTab'
import { MarkingTab } from './MarkingTab'
import { NormsTab } from './NormsTab'
import { ReceiptDialog } from './ReceiptDialog'
import type { ReceiptRow } from './receipt'
import { readTableFile } from './receiptFile'

export type InventorySection = 'balances' | 'items' | 'documents' | 'marking' | 'norms'

type InventoryPageProps = {
  role: Role
  apiOnline: boolean
  services: ServiceItem[]
  notify: (message: string) => void
  /** Раздел из бокового меню. */
  section: InventorySection
  onSection: (section: InventorySection) => void
}

const intros: Record<InventorySection, string> = {
  balances: 'Сколько чего лежит на складах, по партиям и срокам годности.',
  items: 'Что клиника хранит и расходует: лекарства, медизделия, расходники и товары.',
  documents: 'Приход, расход, списание, перемещение и возврат. Проведённая накладная меняет остаток.',
  marking: 'Марки Честного знака и очередь операций с оператором маркировки.',
  norms: 'Что и сколько списывается со склада при завершении приёма по услуге.',
}

/**
 * Склад (раздел E, inventory). Поступление — в два клика: «Поступление» или «Загрузить из файла»,
 * затем «Провести». Пациентов здесь нет: расход по приёму хранит только номер приёма.
 */
export function InventoryPage({ role, apiOnline, services, notify, section, onSection }: InventoryPageProps) {
  const [receipt, setReceipt] = useState<{ rows?: ReceiptRow[]; fileName?: string } | null>(null)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState('')
  const file = useRef<HTMLInputElement | null>(null)
  const canReceive = can(role, 'inventory.document.write')

  const openFile = async (picked: File | undefined) => {
    if (!picked) return
    try {
      const rows = await readTableFile(picked)
      if (rows.length === 0) {
        setError(`В файле «${picked.name}» не нашлось строк с позициями`)
        return
      }
      setError('')
      setReceipt({ rows, fileName: picked.name })
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const done = (result: ReceiptResult) => {
    setReceipt(null)
    setRevision((value) => value + 1)
    const count = result.document.lines.length
    const created = result.itemsCreated > 0 ? `, новых позиций: ${result.itemsCreated}` : ''
    if (result.posted) {
      notify(`Поступление проведено: строк ${count}${created}`)
      if (section !== 'balances' && section !== 'items' && can(role, 'inventory.stock.read')) onSection('balances')
    } else {
      notify(`Накладная сохранена черновиком: отсканируйте марки — ${result.waitingCodes.join(', ')}`)
      onSection('documents')
    }
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography sx={{ color: 'text.secondary' }}>{intros[section]}</Typography></Box>
        {apiOnline && canReceive && <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          <Button variant="outlined" startIcon={<UploadFileRounded />} onClick={() => file.current?.click()}>Загрузить из файла</Button>
          <Button variant="contained" startIcon={<AddRounded />} onClick={() => setReceipt({})}>Поступление</Button>
          <input ref={file} type="file" hidden accept=".xlsx,.csv,.txt,.tsv" aria-label="Файл с поступлением" onChange={(event) => { void openFile(event.target.files?.[0]); event.target.value = '' }} />
        </Stack>}
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      {!apiOnline
        ? <Alert severity="info">Склад работает только с сервером: в демо-песочнице остатков и накладных нет.</Alert>
        : <Box key={revision}>
          {section === 'balances' && <BalancesTab onReceive={canReceive ? () => setReceipt({}) : undefined} />}
          {section === 'items' && <ItemsTab role={role} notify={notify} />}
          {section === 'documents' && <DocumentsTab role={role} notify={notify} />}
          {section === 'marking' && <MarkingTab role={role} notify={notify} />}
          {section === 'norms' && <NormsTab services={services} canEdit={can(role, 'schedule.service.manage', 'inventory.item.write')} notify={notify} />}
        </Box>}
      {receipt && <ReceiptDialog initialRows={receipt.rows} fileName={receipt.fileName} onClose={() => setReceipt(null)} onDone={done} />}
    </>
  )
}
