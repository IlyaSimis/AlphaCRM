import { useEffect, useState } from 'react'
import { DownloadRounded } from '@mui/icons-material'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Typography } from '@mui/material'
import { downloadFile, loadVersions } from '../../api/drive'
import type { DriveNode, DriveVersion } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { formatBytes } from '../today/dashboard'

/** Версии файла: каждая загрузка поверх — новая версия; хранятся последние 20. */
export function VersionsDialog({ node, onClose }: { node: DriveNode; onClose: () => void }) {
  const [versions, setVersions] = useState<DriveVersion[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    loadVersions(node.id).then(setVersions).catch((reason: Error) => setError(reason.message))
  }, [node.id])

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Версии: {node.name}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        {versions && versions.length === 0 && <Typography>Версий нет.</Typography>}
        {versions && versions.length > 0 && <table className="audit-table">
          <thead><tr><th>№</th><th>Загружена</th><th>Кем</th><th>Размер</th><th /></tr></thead>
          <tbody>
            {versions.map((version) => (
              <tr key={version.version}>
                <td>{version.version}</td>
                <td className="nowrap">{formatClinicMoment(version.uploadedAt)}</td>
                <td>{version.uploadedByName ?? '—'}</td>
                <td className="nowrap">{formatBytes(version.sizeBytes)}</td>
                <td><IconButton aria-label={`Скачать версию ${version.version}`} onClick={() => void downloadFile(node, version.version).catch((reason: Error) => setError(reason.message))}><DownloadRounded /></IconButton></td>
              </tr>
            ))}
          </tbody>
        </table>}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Закрыть</Button></DialogActions>
    </Dialog>
  )
}
