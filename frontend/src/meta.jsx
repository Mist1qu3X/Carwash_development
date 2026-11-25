import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { request } from './api.js';

const MetaContext = createContext(null);

// справочные данные, которые нужны многим страницам: статусы, роли, виды услуг
export function MetaProvider({ children }) {
  const [meta, setMeta] = useState(null);
  const [serviceTypes, setServiceTypes] = useState([]);

  const reloadServiceTypes = useCallback(
    () => request('/api/service-types', { query: { includeInactive: true } }).then(setServiceTypes),
    [],
  );

  useEffect(() => {
    request('/api/meta').then(setMeta);
    reloadServiceTypes();
  }, [reloadServiceTypes]);

  if (!meta) return <div className="loading">Загрузка справочников…</div>;
  return (
    <MetaContext.Provider value={{ meta, serviceTypes, reloadServiceTypes }}>
      {children}
    </MetaContext.Provider>
  );
}

export function useMeta() {
  const value = useContext(MetaContext);
  const statusTitle = (code) => value.meta.statuses.find((s) => s.code === code)?.title || code;
  const roleTitle = (code) => value.meta.roles.find((r) => r.code === code)?.title || code;
  return { ...value, statusTitle, roleTitle };
}
