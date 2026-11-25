import { useState } from 'react';
import { request } from '../api.js';
import { useToast } from '../toast.jsx';
import { downloadBlob } from '../format.js';
import Modal from './Modal.jsx';
import { ErrorBox } from './common.jsx';

const ACTION_TITLE = { CREATE: 'создание', UPDATE: 'обновление', ERROR: 'ошибка' };
const ACTION_CLASS = { CREATE: 'ok', UPDATE: 'info', ERROR: 'bad' };

function sampleCsv() {
  const d = new Date();
  d.setDate(d.getDate() + 3);
  const date = `${d.toISOString().slice(0, 10)} 12:00`;
  return '﻿code;clientName;clientPhone;carNumber;carModel;serviceType;status;scheduledAt;price;paid;tags;comment\r\n'
    + `IMP-0001;Сергей Орлов;+7-900-777-11-22;К111КК77;Skoda Octavia;COMPLEX;CREATED;${date};;нет;новый;Позвонить заранее\r\n`
    + `IMP-0002;Иван Петров;+7-900-111-11-11;А123ВС77;Toyota Camry;BODY_WASH;CONFIRMED;${date};550;да;постоянный,vip;\r\n`;
}

// импорт CSV в два шага: проверка (ничего не сохраняет) -> применение
export default function ImportDialog({ onClose, onImported }) {
  const notify = useToast();
  const [file, setFile] = useState(null);
  const [mode, setMode] = useState('CREATE_ONLY');
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function send(step) {
    const form = new FormData();
    form.append('file', file);
    setBusy(true);
    setError(null);
    try {
      const result = await request(`/api/bookings/import/${step}`, { method: 'POST', query: { mode }, form });
      setReport(result);
      if (step === 'apply') {
        notify(`Импорт выполнен: создано ${result.created}, обновлено ${result.updated}, ошибок ${result.invalidRows}`, 'success');
        onImported();
      }
    } catch (err) {
      setError(err);
      setReport(null);
    } finally {
      setBusy(false);
    }
  }

  // после смены файла или режима нужно проверить заново
  const reset = () => setReport(null);
  const checked = report?.dryRun && report.validRows > 0;

  return (
    <Modal
      wide
      title="Импорт записей из CSV"
      onClose={onClose}
      footer={(
        <>
          <button className="btn" type="button" onClick={onClose}>Закрыть</button>
          <button className="btn" type="button" disabled={!file || busy} onClick={() => send('preview')}>1. Проверить файл</button>
          <button className="btn primary" type="button" disabled={!checked || busy} onClick={() => send('apply')}>2. Применить импорт</button>
        </>
      )}
    >
      <p className="muted">
        Разделитель «;» или «,», кодировка UTF-8. Обязательные колонки: clientPhone, carNumber, serviceType, scheduledAt.
        Клиент ищется по телефону, а если его нет — создаётся (нужна колонка clientName).
        Пустая цена — базовая цена услуги.{' '}
        <button className="link-btn" type="button" onClick={() => downloadBlob(new Blob([sampleCsv()], { type: 'text/csv' }), 'bookings-sample.csv')}>
          Скачать пример файла
        </button>
      </p>
      <div className="form-grid">
        <label>Файл CSV
          <input type="file" accept=".csv,text/csv" onChange={(e) => { setFile(e.target.files[0] || null); reset(); }} />
        </label>
        <label>Режим
          <select value={mode} onChange={(e) => { setMode(e.target.value); reset(); }}>
            <option value="CREATE_ONLY">Только новые записи</option>
            <option value="UPSERT">Обновление по внешнему коду (code)</option>
          </select>
        </label>
      </div>
      <ErrorBox error={error} />

      {report && (
        <>
          <h3>{report.dryRun ? 'Результат проверки (база не изменялась)' : 'Результат импорта'}</h3>
          <div className="import-summary">
            <span className="badge">Строк: {report.totalRows}</span>
            <span className="badge ok">Корректных: {report.validRows}</span>
            <span className="badge bad">С ошибками: {report.invalidRows}</span>
            <span className="badge info">{report.dryRun ? 'Будет создано' : 'Создано'}: {report.created}</span>
            <span className="badge info">{report.dryRun ? 'Будет обновлено' : 'Обновлено'}: {report.updated}</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Строка</th><th>Код</th><th>Действие</th><th>Сообщение</th></tr></thead>
              <tbody>
                {report.rows.map((r) => (
                  <tr key={r.line} className={r.action === 'ERROR' ? 'row-error' : undefined}>
                    <td>{r.line}</td>
                    <td>{r.code || '—'}</td>
                    <td><span className={`badge ${ACTION_CLASS[r.action]}`}>{ACTION_TITLE[r.action]}</span></td>
                    <td>{r.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {report.truncated && <p className="muted">Показаны первые 1000 строк.</p>}
        </>
      )}
    </Modal>
  );
}
