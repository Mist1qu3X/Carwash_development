import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { useMeta } from '../meta.jsx';

const LINKS = [
  { to: '/bookings', label: 'Записи', role: 'VIEWER' },
  { to: '/clients', label: 'Клиенты', role: 'VIEWER' },
  { to: '/analytics', label: 'Аналитика', role: 'VIEWER' },
  { to: '/services', label: 'Услуги', role: 'VIEWER' },
  { to: '/users', label: 'Пользователи', role: 'ADMIN' },
  { to: '/audit', label: 'Аудит', role: 'ADMIN' },
  { to: '/profile', label: 'Профиль', role: 'VIEWER' },
];

export default function Layout() {
  const { user, can, logout } = useAuth();
  const { roleTitle } = useMeta();
  const mainRole = ['ADMIN', 'MANAGER', 'VIEWER'].find((r) => user.roles.includes(r));

  return (
    <>
      <header className="topbar">
        <div className="brand"><span className="brand-mark" aria-hidden="true">CW</span> Автомойка</div>
        <nav id="nav" aria-label="Разделы">
          {LINKS.filter((l) => can(l.role)).map((l) => (
            <NavLink key={l.to} to={l.to} className={({ isActive }) => (isActive ? 'active' : undefined)}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-box">
          <span>{user.fullName}</span>
          <span className="badge">{roleTitle(mainRole)}</span>
          <button className="btn ghost small" type="button" onClick={logout}>Выйти</button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </>
  );
}
