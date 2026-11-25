import { useEffect, useState } from 'react';
import { request } from '../api.js';
import { formatMoney } from '../format.js';
import { ErrorBox } from '../components/common.jsx';

function Kpi({ title, value, hint }) {
  return (
    <div className="kpi">
      <div className="muted">{title}</div>
      <div className="value">{value}</div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  );
}

// горизонтальные полосы: ширина пропорциональна количеству
function Bars({ title, items }) {
  const max = Math.max(1, ...items.map((i) => i.count));
  return (
    <div className="panel">
      <h3>{title}</h3>
      {items.map((item) => (
        <div key={item.key} className="bar-row">
          <span>{item.title}</span>
          <span className="bar" role="img" aria-label={`${item.title}: ${item.count}`}>
            <span style={{ width: `${(item.count / max) * 100}%` }} />
          </span>
          <strong className="right">{item.count}</strong>
        </div>
      ))}
      {items.length === 0 && <p className="muted">Нет данных</p>}
    </div>
  );
}

export default function AnalyticsPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    request('/api/analytics/summary').then(setData).catch(setError);
  }, []);

  if (error) return <ErrorBox error={error} />;
  if (!data) return <div className="loading">Загрузка…</div>;

  return (
    <>
      <div className="page-head"><h1>Аналитика</h1></div>
      <div className="kpis">
        <Kpi title="Всего записей" value={data.total} />
        <Kpi title="Активных" value={data.active} />
        <Kpi title="В архиве" value={data.archived} />
        <Kpi title="Оплачено" value={data.paid} hint={`не оплачено: ${data.unpaid}`} />
        <Kpi title="Средняя цена" value={formatMoney(data.averagePrice)} hint="по активным записям" />
        <Kpi title="Получено оплат" value={formatMoney(data.paidRevenue)} hint="оплаченные, кроме отменённых" />
        <Kpi title="Долг клиентов" value={formatMoney(data.unpaidDebt)} hint="завершены, но не оплачены" />
      </div>
      <div className="charts">
        <Bars title="По видам услуг" items={data.byServiceType} />
        <Bars title="По статусам" items={data.byStatus} />
        <Bars title="По риску неоплаты" items={data.byPaymentRisk} />
      </div>
      <p className="muted small-text">Группировки и средняя цена считаются по активным (не архивным) записям.</p>
    </>
  );
}
