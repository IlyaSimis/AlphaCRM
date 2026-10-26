/**
 * Фон рабочего места: небо, горы (средняя линия), ближний холм (первая линия) с тремя деревьями,
 * солнце и луна. Выплывает один раз при входе
 * (Workspace монтируется заново на каждого сотрудника) и дальше стоит неподвижно. Светило зависит
 * от темы: в светлой — солнце, в тёмной — луна; при смене темы одно заходит за холм, другое
 * восходит (переходы — в index.css). Только для красоты: скрыт от экранного диктора и не
 * перехватывает нажатия. Позиции фигур — координатами, без атрибута transform: его заменил бы CSS.
 */
/** Средняя линия: горы из прямых отрезков. */
export const MOUNTAINS = 'M0 660 L150 610 L300 500 L420 600 L520 560 L640 640 L820 600 L1000 440 L1110 580 L1230 520 L1360 600 L1440 570 L1440 900 L0 900 Z'

/** Первая линия: ближний гладкий холм, на нём деревья. */
export const NEAR_HILL = 'M0 760 C 300 680 560 690 820 740 S 1240 800 1440 720 L 1440 900 L 0 900 Z'

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
        {/* Средняя линия — горы: прямые изломы, пики разной высоты. */}
        <path className="ls-far" d={MOUNTAINS} />
        {/* Первая линия — гладкий ближний холм; деревья стоят на нём, стволы уходят в склон. */}
        <path className="ls-near" d={NEAR_HILL} />
        <g className="ls-trees">
          <rect className="ls-trunk" x="244" y="660" width="12" height="58" rx="3" />
          <circle className="ls-crown" cx="250" cy="644" r="32" />
          <rect className="ls-trunk" x="1054" y="714" width="12" height="66" rx="3" />
          <circle className="ls-crown" cx="1060" cy="698" r="34" />
          <rect className="ls-trunk" x="1145" y="728" width="10" height="56" rx="3" />
          <circle className="ls-crown" cx="1150" cy="714" r="26" />
        </g>
      </svg>
    </div>
  )
}
