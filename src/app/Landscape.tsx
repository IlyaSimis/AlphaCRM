/**
 * Фон рабочего места: небо, два холма, солнце, луна и три дерева. Выплывает один раз при входе
 * (Workspace монтируется заново на каждого сотрудника) и дальше стоит неподвижно. Светило зависит
 * от темы: в светлой — солнце, в тёмной — луна; при смене темы одно заходит за холм, другое
 * восходит (переходы — в index.css). Только для красоты: скрыт от экранного диктора и не
 * перехватывает нажатия. Позиции фигур — координатами, без атрибута transform: его заменил бы CSS.
 */
export function Landscape() {
  return (
    <div className="landscape" aria-hidden="true" data-testid="landscape">
      <svg viewBox="0 0 1440 900" preserveAspectRatio="xMidYMax slice" focusable="false">
        <defs>
          <linearGradient id="landscape-sky" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="900">
            <stop offset="0" className="ls-sky-top" />
            <stop offset="1" className="ls-sky-bottom" />
          </linearGradient>
        </defs>
        <rect className="ls-sky" x="0" y="0" width="1440" height="900" fill="url(#landscape-sky)" />
        {/* Внешняя группа — выезд при входе, внутренняя — восход и заход при смене темы. */}
        <g className="ls-light">
          <circle className="ls-body ls-sun" cx="1060" cy="260" r="52" />
          <g className="ls-body ls-moon">
            <circle className="ls-moon-disc" cx="1060" cy="250" r="42" />
            <circle className="ls-moon-shadow" cx="1077" cy="239" r="38" fill="url(#landscape-sky)" />
          </g>
        </g>
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
