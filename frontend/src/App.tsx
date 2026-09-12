import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import CreditsPage from './pages/CreditsPage';
import DashboardPage from './pages/DashboardPage';
import HistoryPage from './pages/HistoryPage';
import LoginPage from './pages/LoginPage';
import ProblemsPage from './pages/ProblemsPage';
import RegisterPage from './pages/RegisterPage';
import SavedCodePage from './pages/SavedCodePage';
import VisualizerPage from './pages/VisualizerPage';
import { useAuth } from './store/AuthContext';
import type { ReactElement } from 'react';

/**
 * Sends signed-out visitors to the sign-in page.
 *
 * <p>This is convenience only, never a security boundary -- the backend authorises every
 * request on its own, and it scopes each query by user id so a guessed identifier returns 404
 * rather than someone else's data.
 */
function RequireAuth({ children }: { children: ReactElement }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <p className="p-6 text-sm text-slate-500">Loading...</p>;
  }
  return user ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* Usable without an account, so a first-time visitor can run code immediately. */}
        <Route path="/" element={<VisualizerPage />} />
        <Route path="/problems" element={<ProblemsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="/history"
          element={
            <RequireAuth>
              <HistoryPage />
            </RequireAuth>
          }
        />
        <Route
          path="/saved"
          element={
            <RequireAuth>
              <SavedCodePage />
            </RequireAuth>
          }
        />
        <Route
          path="/credits"
          element={
            <RequireAuth>
              <CreditsPage />
            </RequireAuth>
          }
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
