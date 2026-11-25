// небольшие переиспользуемые компоненты

const STATUS_CLASS = {
  CREATED: 'info',
  CONFIRMED: 'info',
  IN_PROGRESS: 'warn',
  COMPLETED: 'ok',
  CANCELLED: '',
};

const RISK_CLASS = { LOW: 'ok', MEDIUM: 'warn', HIGH: 'bad' };

export function StatusBadge({ code, title }) {
  return <span className={`badge ${STATUS_CLASS[code] ?? ''}`}>{title || code}</span>;
}

export function RiskBadge({ code, title }) {
  return <span className={`badge ${RISK_CLASS[code] ?? ''}`}>{title || code}</span>;
}

export function Tags({ tags }) {
  if (!tags?.length) return <span className="muted">—</span>;
  return tags.map((t) => <span key={t} className="tag">{t}</span>);
}

export function ErrorBox({ error }) {
  if (!error) return null;
  const message = typeof error === 'string' ? error : error.message;
  return (
    <p className="form-error">
      {message}
      {error.correlationId && <span className="small-text"> (correlationId: {error.correlationId})</span>}
    </p>
  );
}

// поле формы с подписью и сообщением об ошибке от сервера
export function Field({ label, error, className, children }) {
  return (
    <label className={className}>
      {label}
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

// заголовок колонки с сортировкой по клику
export function SortTh({ field, sort, onSort, children }) {
  const [current, direction] = (sort || '').split(',');
  const active = current === field;
  return (
    <th aria-sort={active ? (direction === 'desc' ? 'descending' : 'ascending') : 'none'}>
      <button type="button" className={`sort-btn ${active ? 'active' : ''}`} onClick={() => onSort(field)}>
        {children} {active ? (direction === 'desc' ? '▼' : '▲') : ''}
      </button>
    </th>
  );
}

export function nextSort(sort, field) {
  const [current, direction] = (sort || '').split(',');
  if (current === field) return `${field},${direction === 'asc' ? 'desc' : 'asc'}`;
  return `${field},asc`;
}

export function Pager({ data, onPage, size, onSize }) {
  if (!data) return null;
  const last = Math.max(data.totalPages - 1, 0);
  return (
    <div className="pager">
      {onSize && (
        <label className="check">
          На странице
          <select value={size} onChange={(e) => onSize(Number(e.target.value))}>
            {[10, 20, 50, 100].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
      )}
      <span className="muted">
        Стр. {data.totalPages === 0 ? 0 : data.page + 1} из {data.totalPages} · всего {data.totalElements}
      </span>
      <button className="btn small" type="button" disabled={data.page <= 0} onClick={() => onPage(data.page - 1)}>← Назад</button>
      <button className="btn small" type="button" disabled={data.page >= last} onClick={() => onPage(data.page + 1)}>Вперёд →</button>
    </div>
  );
}
