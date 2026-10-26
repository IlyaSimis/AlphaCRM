/**
 * Фон рабочего места. Небо — ровная заливка за всей колонкой (стили .landscape-view); земля,
 * светила и деревья — одна SVG-полоса высотой 180 px у нижнего края окна, поэтому верх и середина
 * экрана — только небо, а просветы между карточками выглядят как обычный фон страницы.
 *
 * Полоса: дальний низкий холм, ближние горы (три пика и седловина), солнце и луна за дальней
 * грядой, роща из двух деревьев в седловине и одно дерево поменьше на дальнем холме. Земля
 * растягивается по ширине (preserveAspectRatio="none" у вложенного svg) и не меняет высоту;
 * круглые фигуры ставятся процентами по ширине и пикселями по высоте, поэтому не сплющиваются и
 * остаются на тех же склонах. Позиции — координатами, без атрибута transform: его заменил бы CSS.
 *
 * Выплывает один раз при входе (Workspace монтируется заново на каждого сотрудника), дальше стоит.
 * Светлая тема — солнце, тёмная — луна: при смене одно уходит за землю, другое поднимается.
 */

/** Высота полосы земли, px. Совпадает с высотой viewBox земли: по вертикали масштаб 1. */
export const GROUND_HEIGHT = 180

/** Ближние горы: изломы, а не гладкая волна. Пики x 760, 980, 1190; седловина — x 1085. */
export const MOUNTAINS = 'M0 180 L0 170 L600 168 L760 104 L860 140 L980 40 L1085 118 L1190 70 L1330 150 L1440 160 L1440 180 Z'

/** Дальний холм: низкий, гладкий. */
export const FAR_HILL = 'M0 150 C240 128 480 132 720 142 S1200 126 1440 138 L1440 180 L0 180 Z'

export function Landscape() {
  return (
    <div className="landscape" aria-hidden="true" data-testid="landscape">
      <div className="landscape-view ls-sky">
        <svg className="ls-scene" width="100%" height={GROUND_HEIGHT} focusable="false">
          {/* Светила — за дальней грядой, левее гор. Внешняя группа — выезд при входе, внутренняя — смена темы. */}
          <g className="ls-light">
            <circle className="ls-body ls-sun" cx="40%" cy="118" r="26" />
            <g className="ls-body ls-moon">
              <svg x="41%" overflow="visible">
                <circle className="ls-moon-disc" cx="0" cy="112" r="20" />
                <circle className="ls-moon-shadow" cx="9" cy="106" r="17" />
              </svg>
            </g>
          </g>
          <g className="ls-far">
            <svg width="100%" height={GROUND_HEIGHT} viewBox={`0 0 1440 ${GROUND_HEIGHT}`} preserveAspectRatio="none">
              <path className="ls-far-hill" d={FAR_HILL} />
            </svg>
          </g>
          {/* Одно небольшое дерево на дальнем холме, левее рощи: читается дальним. */}
          <g className="ls-tree-far">
            <svg x="22%" overflow="visible">
              <rect className="ls-trunk" x="-3" y="122" width="6" height="16" rx="2" />
              <circle className="ls-crown" cx="0" cy="116" r="12" />
            </svg>
          </g>
          <g className="ls-near">
            <svg width="100%" height={GROUND_HEIGHT} viewBox={`0 0 1440 ${GROUND_HEIGHT}`} preserveAspectRatio="none">
              <path className="ls-mountains" d={MOUNTAINS} />
            </svg>
          </g>
          {/* Роща в седловине: стволы уходят в склоны по обе стороны от седла. */}
          <g className="ls-trees">
            <svg x="73.5%" overflow="visible">
              <rect className="ls-trunk" x="-4" y="80" width="8" height="30" rx="2" />
              <circle className="ls-crown" cx="0" cy="72" r="20" />
            </svg>
            <svg x="77%" overflow="visible">
              <rect className="ls-trunk" x="-4" y="88" width="8" height="30" rx="2" />
              <circle className="ls-crown" cx="0" cy="80" r="22" />
            </svg>
          </g>
        </svg>
      </div>
    </div>
  )
}
