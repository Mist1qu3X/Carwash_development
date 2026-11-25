import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { auth, onSessionLost } from './api.js';

const AuthContext = createContext(null);

// ADMIN умеет всё, что MANAGER; MANAGER — всё, что VIEWER (как RoleHierarchy на сервере)
const RANK = { VIEWER: 1, MANAGER: 2, ADMIN: 3 };

export function hasRole(user, role) {
  const best = Math.max(0, ...(user?.roles || []).map((r) => RANK[r] || 0));
  return best >= RANK[role];
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const unsubscribe = onSessionLost(() => setUser(null));
    auth.restore()
      .then((restored) => setUser(restored))
      .finally(() => setReady(true));
    return unsubscribe;
  }, []);

  const login = useCallback(async (email, password) => setUser(await auth.login(email, password)), []);
  const logout = useCallback(async () => {
    await auth.logout();
    setUser(null);
  }, []);
  const changePassword = useCallback(async (current, next) => setUser(await auth.changePassword(current, next)), []);

  const value = useMemo(() => ({
    user,
    ready,
    login,
    logout,
    changePassword,
    can: (role) => hasRole(user, role),
  }), [user, ready, login, logout, changePassword]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
