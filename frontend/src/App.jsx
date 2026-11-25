import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth.jsx';
import { MetaProvider } from './meta.jsx';
import { ToastProvider } from './toast.jsx';
import Layout from './components/Layout.jsx';
import LoginPage from './pages/LoginPage.jsx';
import ChangePasswordPage from './pages/ChangePasswordPage.jsx';
import BookingsPage from './pages/BookingsPage.jsx';
import ClientsPage from './pages/ClientsPage.jsx';
import AnalyticsPage from './pages/AnalyticsPage.jsx';
import ServiceTypesPage from './pages/ServiceTypesPage.jsx';
import UsersPage from './pages/UsersPage.jsx';
import AuditPage from './pages/AuditPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Screens />
      </AuthProvider>
    </ToastProvider>
  );
}

// что показать: вход, обязательную смену пароля или само приложение
function Screens() {
  const { user, ready } = useAuth();
  if (!ready) return <div className="loading">Загрузка…</div>;
  if (!user) return <LoginPage />;
  if (user.mustChangePassword) return <ChangePasswordPage />;
  return (
    <MetaProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/bookings" replace />} />
          <Route path="bookings" element={<BookingsPage />} />
          <Route path="clients" element={<ClientsPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="services" element={<ServiceTypesPage />} />
          <Route path="users" element={<AdminOnly><UsersPage /></AdminOnly>} />
          <Route path="audit" element={<AdminOnly><AuditPage /></AdminOnly>} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="*" element={<Navigate to="/bookings" replace />} />
        </Route>
      </Routes>
    </MetaProvider>
  );
}

function AdminOnly({ children }) {
  const { can } = useAuth();
  return can('ADMIN') ? children : <Navigate to="/bookings" replace />;
}
