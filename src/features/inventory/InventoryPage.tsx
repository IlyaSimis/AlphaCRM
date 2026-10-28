import { useState } from 'react'
import { Alert, Box, Tab, Tabs, Typography } from '@mui/material'
import type { ServiceItem } from '../../api/types'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'
import { BalancesTab } from './BalancesTab'
import { DocumentsTab } from './DocumentsTab'
import { ItemsTab } from './ItemsTab'
import { MarkingTab } from './MarkingTab'
import { NormsTab } from './NormsTab'

type InventoryPageProps = {
  role: Role
  apiOnline: boolean
  services: ServiceItem[]
  notify: (message: string) => void
}

type InventoryTab = 'balances' | 'items' | 'documents' | 'marking' | 'norms'

/**
 * Склад (раздел E, inventory): остатки, номенклатура, накладные, маркировка и нормы расхода
 * услуг. Пациентов здесь нет: расход по приёму хранит только номер приёма.
 */
export function InventoryPage({ role, apiOnline, services, notify }: InventoryPageProps) {
  const tabs: Array<{ value: InventoryTab; label: string }> = [
    ...(can(role, 'inventory.stock.read') ? [{ value: 'balances' as const, label: 'Остатки' }] : []),
    ...(can(role, 'inventory.item.read') ? [{ value: 'items' as const, label: 'Номенклатура' }] : []),
    ...(can(role, 'inventory.document.write') ? [{ value: 'documents' as const, label: 'Накладные' }] : []),
    ...(can(role, 'inventory.marking.read') ? [{ value: 'marking' as const, label: 'Маркировка' }] : []),
    ...(can(role, 'schedule.service.manage', 'inventory.item.write') ? [{ value: 'norms' as const, label: 'Нормы расхода' }] : []),
  ]
  const [tab, setTab] = useState<InventoryTab>(tabs[0]?.value ?? 'balances')

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Склад</Typography><Typography sx={{ color: 'text.secondary' }}>Номенклатура, накладные, остатки и марки Честного знака. Расход по приёму хранит только номер приёма — без пациента и диагноза.</Typography></Box>
      </Box>
      {!apiOnline
        ? <Alert severity="info">Склад работает только с сервером: в демо-песочнице остатков и накладных нет.</Alert>
        : <>
          <Tabs value={tab} onChange={(_event: unknown, next: InventoryTab) => setTab(next)} variant="scrollable" sx={{ mb: 2 }} aria-label="Разделы склада">
            {tabs.map((item) => <Tab key={item.value} value={item.value} label={item.label} />)}
          </Tabs>
          {tab === 'balances' && <BalancesTab />}
          {tab === 'items' && <ItemsTab role={role} notify={notify} />}
          {tab === 'documents' && <DocumentsTab role={role} notify={notify} />}
          {tab === 'marking' && <MarkingTab role={role} notify={notify} />}
          {tab === 'norms' && <NormsTab services={services} canEdit={can(role, 'schedule.service.manage', 'inventory.item.write')} notify={notify} />}
        </>}
    </>
  )
}
