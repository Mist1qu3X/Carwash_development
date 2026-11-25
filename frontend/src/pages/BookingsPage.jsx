import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { request } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useMeta } from '../meta.jsx';
import { useToast } from '../toast.jsx';
import { downloadBlob, fileNameFromResponse, formatLocal, formatMoney } from '../format.js';
import { ErrorBox, Pager, RiskBadge, SortTh, StatusBadge, Tags, nextSort } from '../components/common.jsx';
import Modal from '../components/Modal.jsx';
import BookingForm from '../components/BookingForm.jsx';
import BookingCard from '../components/BookingCard.jsx';
import ImportDialog from '../components/ImportDialog.jsx';
import BulkBar from '../components/BulkBar.jsx';

const EMPTY_FILTER = {
  q: '', serviceTypeId: '', clientId: '', status: '', paid: '', risk: '', tag: '',
  archive: 'ACTIVE', priceOp: 'GTE', price: '',
};

// параметры запроса: пустые значения api.js не отправляет
function toQuery(filter) {
  const query = { ...filter };
  if (query.price === '') delete query.priceOp;
  return query;
}

export default function BookingsPage() {
  const { can } = useAuth();
  const { meta, serviceTypes } = useMeta();
  const notify = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const initial = { ...EMPTY_FILTER, clientId: searchParams.get('clientId') || '' };
  const [draft, setDraft] = useState(initial);   // то, что сейчас в полях формы
  const [filter, setFilter] = useState(initial); // применённый фильтр
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState('scheduledAt,desc');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [tags, setTags] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [dialog, setDialog] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = () => setReloadKey((k) => k + 1);
  const query = useMemo(() => ({ ...toQuery(filter), page, size, sort }), [filter, page, size, sort]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    request('/api/bookings', { query })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [query, reloadKey]);

  useEffect(() => {
    request('/api/bookings/tags').then(setTags).catch(() => setTags([]));
  }, [reloadKey]);

  const set = (name) => (e) => setDraft({ ...draft, [name]: e.target.value });

  function applyFilter(e) {
    e.preventDefault();
    setFilter(draft);
    setPage(0);
    setSelected(new Set());
  }

  function resetFilter() {
    setDraft(EMPTY_FILTER);
    setFilter(EMPTY_FILTER);
    setPage(0);
    setSelected(new Set());
    setSearchParams({});
  }

  function changeSort(field) {
    setSort(nextSort(sort, field));
    setPage(0);
  }

  async function action(promise, message) {
    try {
      await promise;
      notify(message, 'success');
      reload();
    } catch (err) {
      notify(err.message, 'error');
    }
  }

  const archive = (b) => action(request(`/api/bookings/${b.id}`, { method: 'DELETE' }), `Запись ${b.code} перемещена в архив`);
  const restore = (b) => action(request(`/api/bookings/${b.id}/restore`, { method: 'POST' }), `Запись ${b.code} восстановлена`);
  const removeForever = (b) => {
    if (window.confirm(`Удалить запись ${b.code} окончательно? Это действие нельзя отменить.`)) {
      action(request(`/api/bookings/${b.id}/permanent`, { method: 'DELETE' }), `Запись ${b.code} удалена`);
    }
  };

  async function exportCsv() {
    try {
      const res = await request('/api/bookings/export', { query: { ...toQuery(filter), sort }, raw: true });
      downloadBlob(await res.blob(), fileNameFromResponse(res, 'bookings.csv'));
    } catch (err) {
      notify(err.message, 'error');
    }
  }

  function toggle(id) {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelected(next);
  }

  const rows = data?.content || [];
  const allChecked = rows.length > 0 && rows.every((b) => selected.has(b.id));
  const toggleAll = () => {
    const next = new Set(selected);
    rows.forEach((b) => (allChecked ? next.delete(b.id) : next.add(b.id)));
    setSelected(next);
  };
  const canEdit = can('MANAGER');

  return (
    <>
      <div className="page-head">
        <h1>Записи на мойку</h1>
        <span className="spacer" />
        <button className="btn" type="button" onClick={exportCsv}>Экспорт CSV</button>
        {canEdit && <button className="btn" type="button" onClick={() => setDialog({ type: 'import' })}>Импорт CSV</button>}
        {canEdit && <button className="btn primary" type="button" onClick={() => setDialog({ type: 'form' })}>+ Новая запись</button>}
      </div>

      <form className="panel filters" onSubmit={applyFilter}>
        <label className="grow">Поиск
          <input type="search" value={draft.q} onChange={set('q')} placeholder="Код, гос. номер, модель, клиент, телефон" />
        </label>
        <label>Услуга
          <select value={draft.serviceTypeId} onChange={set('serviceTypeId')}>
            <option value="">Все</option>
            {serviceTypes.map((s) => <option key={s.id} value={s.id}>{s.name}{s.active ? '' : ' (откл.)'}</option>)}
          </select>
        </label>
        <label>Статус
          <select value={draft.status} onChange={set('status')}>
            <option value="">Все</option>
            {meta.statuses.map((s) => <option key={s.code} value={s.code}>{s.title}</option>)}
          </select>
        </label>
        <label>Оплата
          <select value={draft.paid} onChange={set('paid')}>
            <option value="">Все</option>
            <option value="true">Оплачены</option>
            <option value="false">Не оплачены</option>
          </select>
        </label>
        <label>Риск неоплаты
          <select value={draft.risk} onChange={set('risk')}>
            <option value="">Любой</option>
            {meta.paymentRisks.map((r) => <option key={r.code} value={r.code}>{r.title}</option>)}
          </select>
        </label>
        <label>Тег
          <select value={draft.tag} onChange={set('tag')}>
            <option value="">Любой</option>
            {tags.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label>Записи
          <select value={draft.archive} onChange={set('archive')}>
            <option value="ACTIVE">Активные</option>
            <option value="ARCHIVED">Архив</option>
            <option value="ALL">Все</option>
          </select>
        </label>
        <label>Цена, ₽
          <span className="price-filter">
            <select value={draft.priceOp} onChange={set('priceOp')} aria-label="Сравнение цены">
              <option value="GTE">≥</option>
              <option value="GT">&gt;</option>
              <option value="EQ">=</option>
              <option value="LT">&lt;</option>
              <option value="LTE">≤</option>
            </select>
            <input type="number" min="0" step="0.01" value={draft.price} onChange={set('price')} placeholder="сумма" />
          </span>
        </label>
        <div className="row">
          <button className="btn primary" type="submit">Найти</button>
          <button className="btn" type="button" onClick={resetFilter}>Сбросить</button>
        </div>
        {filter.clientId && (
          <div className="row">
            <span className="chip">
              Только записи клиента {searchParams.get('clientName') || ''}
              <button className="btn ghost small" type="button" onClick={resetFilter} aria-label="Сбросить клиента">✕</button>
            </span>
          </div>
        )}
      </form>

      {canEdit && selected.size > 0 && (
        <BulkBar
          ids={[...selected]}
          onDone={() => {
            setSelected(new Set());
            reload();
          }}
          onClear={() => setSelected(new Set())}
          onShowErrors={(result) => setDialog({ type: 'bulk', result })}
        />
      )}

      <div className="panel">
        <ErrorBox error={error} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                {canEdit && <th><input type="checkbox" checked={allChecked} onChange={toggleAll} aria-label="Выбрать все" /></th>}
                <SortTh field="code" sort={sort} onSort={changeSort}>Код</SortTh>
                <SortTh field="scheduledAt" sort={sort} onSort={changeSort}>Дата и время</SortTh>
                <SortTh field="clientName" sort={sort} onSort={changeSort}>Клиент</SortTh>
                <SortTh field="carNumber" sort={sort} onSort={changeSort}>Автомобиль</SortTh>
                <SortTh field="serviceType" sort={sort} onSort={changeSort}>Услуга</SortTh>
                <SortTh field="price" sort={sort} onSort={changeSort}>Цена</SortTh>
                <th>Оплата</th>
                <SortTh field="status" sort={sort} onSort={changeSort}>Статус</SortTh>
                <th>Риск</th>
                <th>Теги</th>
                <th className="right">Действия</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((b) => (
                <tr key={b.id} className={b.archived ? 'archived' : undefined}>
                  {canEdit && (
                    <td><input type="checkbox" checked={selected.has(b.id)} onChange={() => toggle(b.id)} aria-label={`Выбрать ${b.code}`} /></td>
                  )}
                  <td className="nowrap">
                    <button className="link-btn" type="button" onClick={() => setDialog({ type: 'card', id: b.id })}>{b.code}</button>
                    {b.archived && <div><span className="badge">в архиве</span></div>}
                  </td>
                  <td className="nowrap">{formatLocal(b.scheduledAt)}</td>
                  <td>{b.client.fullName}<div className="muted small-text nowrap">{b.client.phone}</div></td>
                  <td className="nowrap">{b.carNumber}<div className="muted small-text">{b.carModel || ''}</div></td>
                  <td>{b.serviceType.name}</td>
                  <td className="nowrap">{formatMoney(b.price)}</td>
                  <td>{b.paid ? <span className="badge ok">оплачено</span> : <span className="badge">нет</span>}</td>
                  <td><StatusBadge code={b.status} title={b.statusTitle} /></td>
                  <td><RiskBadge code={b.paymentRisk} title={b.paymentRiskTitle} /></td>
                  <td><Tags tags={b.tags} /></td>
                  <td className="actions">
                    <button className="btn small" type="button" onClick={() => setDialog({ type: 'card', id: b.id })}>Открыть</button>
                    {canEdit && !b.archived && (
                      <>
                        <button className="btn small" type="button" onClick={() => setDialog({ type: 'form', booking: b })}>Изменить</button>
                        <button className="btn small" type="button" onClick={() => archive(b)}>В архив</button>
                      </>
                    )}
                    {canEdit && b.archived && <button className="btn small" type="button" onClick={() => restore(b)}>Восстановить</button>}
                    {can('ADMIN') && b.archived && meta.permanentDeleteEnabled && (
                      <button className="btn small danger" type="button" onClick={() => removeForever(b)}>Удалить</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && rows.length === 0 && <div className="empty">Ничего не найдено</div>}
          {loading && !data && <div className="loading">Загрузка…</div>}
        </div>
        <Pager
          data={data}
          onPage={setPage}
          size={size}
          onSize={(n) => {
            setSize(n);
            setPage(0);
          }}
        />
      </div>

      {dialog?.type === 'form' && (
        <BookingForm
          booking={dialog.booking}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            reload();
          }}
        />
      )}
      {dialog?.type === 'card' && (
        <BookingCard
          id={dialog.id}
          onClose={() => setDialog(null)}
          onEdit={(booking) => setDialog({ type: 'form', booking })}
        />
      )}
      {dialog?.type === 'import' && (
        <ImportDialog onClose={() => setDialog(null)} onImported={reload} />
      )}
      {dialog?.type === 'bulk' && (
        <Modal title="Ошибки массовой операции" onClose={() => setDialog(null)}
               footer={<button className="btn primary" type="button" onClick={() => setDialog(null)}>Понятно</button>}>
          <p>Успешно: {dialog.result.succeededCount}, с ошибкой: {dialog.result.failedCount}</p>
          <table>
            <thead><tr><th>Запись</th><th>Причина</th></tr></thead>
            <tbody>
              {dialog.result.failed.map((f) => (
                <tr key={f.id}>
                  <td>{rows.find((b) => b.id === f.id)?.code || <span className="mono">{f.id}</span>}</td>
                  <td>{f.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Modal>
      )}
    </>
  );
}
