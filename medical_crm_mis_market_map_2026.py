import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

plt.rcParams["font.family"] = "DejaVu Sans"
fig, ax = plt.subplots(figsize=(16, 9), dpi=180)
fig.patch.set_facecolor("#F7F9FC")
ax.set_facecolor("#F7F9FC")
ax.set_xlim(0, 16)
ax.set_ylim(0, 9)
ax.axis("off")

ax.text(0.7, 8.35, "Медицинские CRM и МИС: единый контур клиники", fontsize=24, fontweight="bold", color="#17233B")
ax.text(0.7, 7.92, "Продукты различаются тем, какую часть пути пациента и операционного цикла они закрывают", fontsize=12.5, color="#526079")

columns = [
    (0.7, "1. Привлечение", "CRM", ["Лиды и источники", "Маркетинговые кампании", "Маркетплейс и репутация"], "#4C78A8"),
    (4.55, "2. Доступ и удержание", "CRM + МИС", ["Онлайн-запись и расписание", "Contact center и напоминания", "Лояльность и реактивация"], "#4C9A92"),
    (8.4, "3. Лечебный процесс", "МИС / EHR", ["ЭМК, протоколы и назначения", "ЛИС / PACS / стационар", "CDSS и клинические процессы"], "#7A6FAC"),
    (12.25, "4. Выручка и операции", "МИС / RCM", ["Счета, платежи и страховые", "Склад, зарплата, отчётность", "Claims и управление отказами"], "#D0834C"),
]

for x, title, subtitle, items, color in columns:
    box = FancyBboxPatch((x, 4.45), 3.15, 2.85, boxstyle="round,pad=0.02,rounding_size=0.12", linewidth=1.3, edgecolor=color, facecolor="white")
    ax.add_patch(box)
    ax.add_patch(FancyBboxPatch((x, 6.82), 3.15, 0.48, boxstyle="round,pad=0.02,rounding_size=0.12", linewidth=0, facecolor=color))
    ax.text(x + 0.18, 7.02, title, fontsize=13.2, fontweight="bold", color="white", va="center")
    ax.text(x + 0.18, 6.48, subtitle, fontsize=12, fontweight="bold", color=color)
    for i, item in enumerate(items):
        ax.text(x + 0.18, 5.96 - i * 0.47, "• " + item, fontsize=10.4, color="#27354D", va="center")

for x in [3.93, 7.78, 11.63]:
    ax.add_patch(FancyArrowPatch((x, 5.88), (x + 0.48, 5.88), arrowstyle="-|>", mutation_scale=18, linewidth=1.8, color="#97A3B6"))

ax.text(0.7, 3.77, "Сквозной слой: единый пациент, согласия, роли, аудит, API, HL7 FHIR, ЕГИСЗ / местные регуляторные интеграции", fontsize=12.2, fontweight="bold", color="#34435E")
ax.add_patch(FancyBboxPatch((0.7, 3.17), 14.7, 0.43, boxstyle="round,pad=0.02,rounding_size=0.1", linewidth=0, facecolor="#E5EAF2"))

ax.text(0.7, 2.55, "Как это монетизируется", fontsize=15, fontweight="bold", color="#17233B")
models = [
    (0.7, "Подписка", "за врача / пользователя\n/ филиал"),
    (3.65, "Модули", "CRM, телемедицина,\nЛИС, AI"),
    (6.6, "Транзакции", "приёмы, сообщения,\nclaims, AI-заметки"),
    (9.55, "Результат", "% собранных платежей\n/ RCM"),
    (12.5, "Проект", "лицензии, внедрение,\nподдержка"),
]
for x, head, body in models:
    ax.text(x, 2.03, head, fontsize=11.5, fontweight="bold", color="#35516F")
    ax.text(x, 1.69, body, fontsize=9.2, color="#526079", linespacing=1.35)

ax.text(0.7, 0.65, "Вывод: рынок движется к конвергенции CRM и МИС, но клиническое ядро и engagement-слой сохраняют разные задачи и экономику.", fontsize=12.4, fontweight="bold", color="#17233B")

plt.tight_layout(pad=0.6)
plt.savefig(r"C:\Users\simis\IdeaProjects\AlphaCRM\medical_crm_mis_market_map_2026.png", bbox_inches="tight", facecolor=fig.get_facecolor())
plt.close(fig)
