import type { DayPeriod } from '../shared/greeting'

/**
 * Фон рабочего места: небо, два холма, светило и три дерева. Выплывает один раз при входе
 * (Workspace монтируется заново на каждого сотрудника) и дальше стоит неподвижно. Только для
 * красоты: скрыт от экранного диктора и не перехватывает нажатия. Цвета — в index.css, по части
 * дня и теме. Позиции фигур — координатами, без атрибута transform: его заменил бы CSS-выезд.
 */
export function Landscape({ period }: { period: DayPeriod }) {
  const evening = period === 'evening'
  // Утром солнце у горизонта, днём выше, вечером вместо него луна.
  const light = period === 'morning' ? { cx: 300, cy: 520, r: 56 } : period === 'day' ? { cx: 1060, cy: 270, r: 52 } : { cx: 1140, cy: 250, r: 40 }
  return (
    <div className={`landscape landscape--${period}`} aria-hidden="true" data-testid="landscape">
      <svg viewBox="0 0 1440 900" preserveAspectRatio="xMidYMax slice" focusable="false">
        <defs>
          <linearGradient id="landscape-sky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="900">
            <stop offset="0" className="ls-sky-top" />
            <stop offset="1" className="ls-sky-bottom" />
          </linearGradient>
        </defs>
        <rect className="ls-sky" x="0" y="0" width="1440" height="900" fill="url(#landscape-sky)" />
        <circle className={`ls-light ${evening ? 'ls-moon' : 'ls-sun'}`} cx={light.cx} cy={light.cy} r={light.r} />
        {evening && <circle className="ls-sky ls-moon-shadow" cx={light.cx + 16} cy={light.cy - 10} r={light.r - 4} fill="url(#landscape-sky)" />}
        <path className="ls-far" d="M0 640 C 220 560 420 560 640 610 S 1060 690 1440 580 L 1440 900 L 0 900 Z" />
        <g className="ls-trees">
          <rect className="ls-trunk" x="1036" y="612" width="12" height="54" rx="3" />
          <circle className="ls-crown" cx="1042" cy="596" r="34" />
          <rect className="ls-trunk" x="1126" y="626" width="10" height="44" rx="3" />
          <circle className="ls-crown" cx="1131" cy="614" r="26" />
          <rect className="ls-trunk" x="236" y="664" width="12" height="52" rx="3" />
          <circle className="ls-crown" cx="242" cy="648" r="30" />
        </g>
        <path className="ls-near" d="M0 760 C 300 680 560 690 820 740 S 1240 800 1440 720 L 1440 900 L 0 900 Z" />
      </svg>
    </div>
  )
}
