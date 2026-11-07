import { useEffect, useRef } from 'react'
import type { StaffChatPreferences } from '../../api/staffChat'
import type { StaffChatNotification } from './useStaffChat'

type AudioWindow = Window & typeof globalThis & { webkitAudioContext?: typeof AudioContext }

/** Короткий синтезированный сигнал не требует аудиофайла и сетевого запроса. */
function playMessageSound() {
  const audioWindow = window as AudioWindow
  const AudioContextType = window.AudioContext ?? audioWindow.webkitAudioContext
  if (!AudioContextType) return
  const context = new AudioContextType()
  const play = () => {
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.setValueAtTime(660, context.currentTime)
    oscillator.frequency.exponentialRampToValueAtTime(880, context.currentTime + 0.09)
    gain.gain.setValueAtTime(0.0001, context.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.12, context.currentTime + 0.015)
    gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.18)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + 0.19)
    oscillator.onended = () => { void context.close().catch(() => undefined) }
  }
  if (context.state === 'suspended') {
    void context.resume().then(play).catch(() => { void context.close().catch(() => undefined) })
  } else {
    play()
  }
}

/** Звук и системное уведомление побочные эффекты, не источник непрочитанных. */
export function useStaffChatAlerts(
  incoming: StaffChatNotification | null,
  preferences: StaffChatPreferences,
  openConversation: (conversationId: string) => Promise<void>,
) {
  const deliveredKey = useRef(0)
  useEffect(() => {
    if (!incoming || !preferences.notificationsEnabled || deliveredKey.current === incoming.key) return
    deliveredKey.current = incoming.key
    if (preferences.soundEnabled) {
      try {
        playMessageSound()
      } catch {
        // Браузер может запретить звук до первого пользовательского действия.
      }
    }
    const hidden = document.visibilityState !== 'visible' || !document.hasFocus()
    if (!preferences.browserEnabled || !hidden || !('Notification' in window) || Notification.permission !== 'granted') return
    const notification = new Notification(incoming.conversationTitle, {
      body: `${incoming.message.senderName}: ${incoming.message.body}`,
      tag: `staff-chat-${incoming.message.id}`,
    })
    notification.onclick = () => {
      window.focus()
      void openConversation(incoming.conversationId)
      notification.close()
    }
  }, [incoming, openConversation, preferences])
}
