import { createContext, useCallback, useContext, useState } from 'react';

const ToastContext = createContext(() => {});

// всплывающие уведомления в правом нижнем углу
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const notify = useCallback((message, type = 'info') => {
    const id = Math.random().toString(36).slice(2);
    setToasts((list) => [...list, { id, message, type }]);
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), type === 'error' ? 7000 : 4000);
  }, []);

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div id="toasts" aria-live="polite">
        {toasts.map((t) => <div key={t.id} className={`toast ${t.type}`}>{t.message}</div>)}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
