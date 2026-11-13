import { useEffect, useRef, useState } from 'react'
import { CreateNewFolderRounded, DeleteOutlineRounded, DownloadRounded, EditRounded, FolderRounded, HistoryRounded, InsertDriveFileRounded, LinkRounded, RestoreRounded, ShareRounded, CloudUploadRounded, VisibilityRounded } from '@mui/icons-material'
import { Alert, Box, Breadcrumbs, Button, Chip, IconButton, Link, Stack, TextField, Tooltip, Typography } from '@mui/material'
import { createFolder, downloadByLink, downloadFile, loadFolder, loadLink, loadQuota, loadShared, loadTrash, previewDriveFile, previewDriveLink, renameNode, restoreNode, trashNode, uploadFile, uploadVersion } from '../../api/drive'
import type { DriveListing, DriveNode, DriveQuota, StaffItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { FilePreviewDialog, type FilePreviewSource } from '../files/FilePreviewDialog'
import { supportedFilePreview } from '../files/filePreview'
import { formatBytes } from '../today/dashboard'
import { allows, permissionLabels, tokenFrom } from './drive'
import { NameDialog } from './NameDialog'
import { ShareDialog } from './ShareDialog'
import { VersionsDialog } from './VersionsDialog'

export type DriveSection = 'mine' | 'shared' | 'trash'

type DrivePageProps = {
  apiOnline: boolean
  self: StaffItem
  notify: (message: string) => void
  /** Раздел из бокового меню: «Мой диск», «Доступные мне», «Корзина». */
  section: DriveSection
  /** Переход в другой раздел — например, папка из «Доступных мне» открывается в «Моём диске». */
  onSection: (section: DriveSection) => void
}

type View = DriveSection | 'link'

/**
 * Корпоративный диск (раздел H): регламенты, договоры, шаблоны, отчёты. Права наследуются от папки к
 * содержимому; ссылка открывается только сотрудником клиники после входа. Медицинские документы
 * пациента сюда не загружаются — они в карточке пациента.
 */
export function DrivePage({ apiOnline, self, notify, section, onSection }: DrivePageProps) {
  // Ссылка из адреса открывается поверх раздела; выбор раздела в меню её закрывает.
  const [linkMode, setLinkMode] = useState(() => Boolean(linkFromAddress()))
  const [shownSection, setShownSection] = useState(section)
  if (shownSection !== section) {
    setShownSection(section)
    setLinkMode(false)
  }
  const view: View = linkMode ? 'link' : section
  const [folder, setFolder] = useState<string | null>(null)
  const [listing, setListing] = useState<DriveListing | null>(null)
  const [nodes, setNodes] = useState<DriveNode[]>([])
  const [quota, setQuotaState] = useState<DriveQuota | null>(null)
  const [link, setLink] = useState(() => linkFromAddress() ?? '')
  const [linkInput, setLinkInput] = useState('')
  const [linkFolder, setLinkFolder] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [naming, setNaming] = useState<{ mode: 'folder' } | { mode: 'rename'; node: DriveNode } | null>(null)
  const [sharing, setSharing] = useState<DriveNode | null>(null)
  const [versions, setVersions] = useState<DriveNode | null>(null)
  const [preview, setPreview] = useState<FilePreviewSource | null>(null)
  const upload = useRef<HTMLInputElement | null>(null)
  const versionUpload = useRef<HTMLInputElement | null>(null)
  const [versionTarget, setVersionTarget] = useState<DriveNode | null>(null)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    const done = (rows: DriveNode[], details: DriveListing | null) => { if (!cancelled) { setNodes(rows); setListing(details); setError('') } }
    const fail = (reason: Error) => { if (!cancelled) setError(reason.message) }
    if (view === 'mine') loadFolder(folder).then((value) => done(value.children, value)).catch(fail)
    if (view === 'shared') loadShared().then((rows) => done(rows, null)).catch(fail)
    if (view === 'trash') loadTrash().then((rows) => done(rows, null)).catch(fail)
    if (view === 'link' && link) loadLink(link, linkFolder).then((value) => done(value.children, value)).catch(fail)
    loadQuota().then((value) => { if (!cancelled) setQuotaState(value) }).catch(() => undefined)
    return () => { cancelled = true }
  }, [apiOnline, view, folder, link, linkFolder, revision])

  if (!apiOnline) return <Alert severity="info">Диск работает только с сервером: в демо-песочнице файлов нет.</Alert>

  const current = listing?.node ?? null
  const canEditHere = view === 'mine' && (current === null || allows(current, 'EDIT'))
  const refresh = () => setRevision((value) => value + 1)

  const run = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action()
      notify(message)
      refresh()
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const open = (node: DriveNode) => {
    if (node.kind !== 'FOLDER') return
    if (view === 'link') {
      setLinkFolder(node.id)
      return
    }
    if (section !== 'mine') onSection('mine')
    setFolder(node.id)
  }

  const download = (node: DriveNode) => void (view === 'link' ? downloadByLink(link, node) : downloadFile(node)).catch((reason: Error) => setError(reason.message))

  const onFiles = (files: FileList | null) => {
    const file = files?.[0]
    if (!file) return
    if (quota && file.size > quota.maxFileBytes) {
      setError(`Файл больше ${formatBytes(quota.maxFileBytes)}`)
      return
    }
    void run(() => uploadFile(folder, file), `Файл «${file.name}» загружен`)
  }

  const onVersion = (files: FileList | null) => {
    const file = files?.[0]
    if (!file || !versionTarget) return
    void run(() => uploadVersion(versionTarget.id, file), `Новая версия «${versionTarget.name}» загружена`)
    setVersionTarget(null)
  }

  const openLink = () => {
    const token = tokenFrom(linkInput)
    if (!token) return
    setLink(token)
    setLinkFolder(null)
    setLinkMode(true)
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Диск</Typography><Typography sx={{ color: 'text.secondary' }}>Регламенты, договоры, шаблоны и отчёты клиники. Права на папку действуют и на её содержимое.</Typography></Box>
      </Box>
      <Alert severity="warning" sx={{ mb: 2 }}>Медицинские документы пациента загружайте в карточку пациента, а не на диск: там они шифруются и открываются только врачу по связи с лечением.</Alert>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
      {link && !linkMode && <Button size="small" startIcon={<LinkRounded />} sx={{ mb: 2 }} onClick={() => { setLinkMode(true); setError('') }}>Вернуться к файлу по ссылке</Button>}
      <Box className="panel">
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2, alignItems: { md: 'center' } }}>
          {view === 'mine' && <Breadcrumbs sx={{ flex: 1 }} aria-label="Путь">
            <Link component="button" underline="hover" onClick={() => setFolder(null)}>Мой диск</Link>
            {(listing?.path ?? []).map((crumb) => <Link key={crumb.id} component="button" underline="hover" onClick={() => setFolder(crumb.id)}>{crumb.name}</Link>)}
            {current && <Typography>{current.name}</Typography>}
          </Breadcrumbs>}
          {view === 'link' && <Breadcrumbs sx={{ flex: 1 }}>
            <Link component="button" underline="hover" onClick={() => setLinkFolder(null)}>Ссылка</Link>
            {current && linkFolder && <Typography>{current.name}</Typography>}
          </Breadcrumbs>}
          {(view === 'shared' || view === 'trash') && <Box sx={{ flex: 1 }} />}
          {view === 'shared' && <Stack direction="row" spacing={1}>
            <TextField size="small" label="Открыть ссылку" value={linkInput} onChange={(event) => setLinkInput(event.target.value)} sx={{ minWidth: 260 }} />
            <Button startIcon={<LinkRounded />} onClick={openLink}>Открыть</Button>
          </Stack>}
          {canEditHere && <>
            <Button startIcon={<CreateNewFolderRounded />} onClick={() => setNaming({ mode: 'folder' })}>Новая папка</Button>
            <Button variant="contained" startIcon={<CloudUploadRounded />} onClick={() => upload.current?.click()}>Загрузить файл</Button>
            <input ref={upload} type="file" hidden aria-label="Файл для загрузки" onChange={(event) => { onFiles(event.target.files); event.target.value = '' }} />
          </>}
        </Stack>
        <input ref={versionUpload} type="file" hidden aria-label="Новая версия файла" onChange={(event) => { onVersion(event.target.files); event.target.value = '' }} />
        {nodes.length === 0
          ? <Typography sx={{ color: 'text.secondary' }}>{view === 'trash' ? 'Корзина пуста. Удалённое хранится 30 дней.' : view === 'shared' ? 'Вам пока ничего не выдали.' : 'Здесь пусто.'}</Typography>
          : <table className="audit-table">
            <thead><tr><th>Название</th><th>Размер</th><th>Версия</th><th>Изменён</th><th>Владелец</th><th>Доступ</th><th /></tr></thead>
            <tbody>
              {nodes.map((node) => (
                <tr key={node.id}>
                  <td>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      {node.kind === 'FOLDER' ? <FolderRounded color="primary" fontSize="small" /> : <InsertDriveFileRounded fontSize="small" />}
                      {node.kind === 'FOLDER' && view !== 'trash'
                        ? <Link component="button" underline="hover" onClick={() => open(node)}>{node.name}</Link>
                        : <Typography>{node.name}</Typography>}
                      {node.shared && <Chip size="small" variant="outlined" label="общий" />}
                    </Stack>
                  </td>
                  <td className="nowrap">{node.kind === 'FILE' ? formatBytes(node.sizeBytes) : '—'}</td>
                  <td>{node.kind === 'FILE' ? node.currentVersion : '—'}</td>
                  <td className="nowrap">{formatClinicMoment(node.deletedAt ?? node.updatedAt)}</td>
                  <td>{node.ownerId === self.id ? 'я' : node.ownerName ?? '—'}</td>
                  <td>{node.permission ? permissionLabels[node.permission] : '—'}</td>
                  <td className="nowrap">
                    {view === 'trash'
                      ? <Tooltip title="Восстановить"><IconButton aria-label={`Восстановить ${node.name}`} onClick={() => void run(() => restoreNode(node.id), 'Восстановлено')}><RestoreRounded /></IconButton></Tooltip>
                      : <>
                        {node.kind === 'FILE' && supportedFilePreview(node.name) && <Tooltip title="Просмотр"><IconButton aria-label={`Просмотр ${node.name}`} onClick={() => setPreview(filePreviewSource(node, view, link))}><VisibilityRounded /></IconButton></Tooltip>}
                        {node.kind === 'FILE' && <Tooltip title="Скачать"><IconButton aria-label={`Скачать ${node.name}`} onClick={() => download(node)}><DownloadRounded /></IconButton></Tooltip>}
                        {view !== 'link' && node.kind === 'FILE' && <Tooltip title="Версии"><IconButton aria-label={`Версии ${node.name}`} onClick={() => setVersions(node)}><HistoryRounded /></IconButton></Tooltip>}
                        {view !== 'link' && node.kind === 'FILE' && allows(node, 'EDIT') && <Tooltip title="Загрузить новую версию"><IconButton aria-label={`Новая версия ${node.name}`} onClick={() => { setVersionTarget(node); versionUpload.current?.click() }}><CloudUploadRounded /></IconButton></Tooltip>}
                        {view !== 'link' && allows(node, 'EDIT') && <Tooltip title="Переименовать"><IconButton aria-label={`Переименовать ${node.name}`} onClick={() => setNaming({ mode: 'rename', node })}><EditRounded /></IconButton></Tooltip>}
                        {view !== 'link' && allows(node, 'MANAGE') && <Tooltip title="Поделиться"><IconButton aria-label={`Поделиться ${node.name}`} onClick={() => setSharing(node)}><ShareRounded /></IconButton></Tooltip>}
                        {view !== 'link' && allows(node, 'MANAGE') && <Tooltip title="В корзину"><IconButton aria-label={`Удалить ${node.name}`} onClick={() => void run(() => trashNode(node.id), 'Перемещено в корзину')}><DeleteOutlineRounded /></IconButton></Tooltip>}
                      </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
      </Box>
      {naming && <NameDialog title={naming.mode === 'folder' ? 'Новая папка' : 'Переименовать'} initial={naming.mode === 'rename' ? naming.node.name : ''} onClose={() => setNaming(null)} onSave={async (name) => {
        try {
          if (naming.mode === 'folder') await createFolder(folder, name)
          else await renameNode(naming.node.id, name)
          setNaming(null)
          notify(naming.mode === 'folder' ? 'Папка создана' : 'Переименовано')
          refresh()
          return null
        } catch (reason) {
          return (reason as Error).message
        }
      }} />}
      {sharing && <ShareDialog node={sharing} onClose={() => { setSharing(null); refresh() }} notify={notify} />}
      {versions && <VersionsDialog node={versions} onClose={() => setVersions(null)} />}
      <FilePreviewDialog source={preview} onClose={() => setPreview(null)} />
    </>
  )
}

function filePreviewSource(node: DriveNode, view: View, link: string): FilePreviewSource | null {
  const kind = supportedFilePreview(node.name)
  if (!kind) return null
  return {
    title: node.name,
    kind: kind === 'image' ? 'image' : 'pdf',
    load: () => view === 'link' ? previewDriveLink(link, node) : previewDriveFile(node),
  }
}

/** Ссылка из адреса страницы (…?drive-link=…), если её открыли из письма или чата. */
function linkFromAddress() {
  if (typeof window === 'undefined') return null
  return new URLSearchParams(window.location.search).get('drive-link')
}
