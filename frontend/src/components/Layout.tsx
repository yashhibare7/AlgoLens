import clsx from 'clsx';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';

const NAV_ITEMS = [
  { to: '/', label: 'Visualizer' },
  { to: '/problems', label: 'Problems' },
];

const AUTHED_NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/history', label: 'History' },
  { to: '/saved', label: 'My code' },
];

export default function Layout() {
  const { user, meta, signOut } = useAuth();
  const navigate = useNavigate();

  const items = user ? [...NAV_ITEMS, ...AUTHED_NAV_ITEMS] : NAV_ITEMS;

  return (
    <div className="flex h-full min-h-screen flex-col">
      <header className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-slate-800
        bg-slate-950/80 px-4 py-2.5 backdrop-blur">
        <Link to="/" className="flex items-center gap-2">
          <span className="grid h-7 w-7 place-items-center rounded-lg bg-sky-600 text-sm
            font-black text-white">
            A
          </span>
          <span className="text-sm font-bold tracking-tight text-slate-100">AlgoLens</span>
        </Link>

        <nav className="flex items-center gap-1">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                clsx(
                  'rounded-lg px-2.5 py-1.5 text-sm transition-colors',
                  isActive
                    ? 'bg-slate-800 text-slate-100'
                    : 'text-slate-400 hover:text-slate-200',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          {user ? (
            <>
              <Link to="/credits" className="chip" title="Credit balance">
                {user.creditBalance} credits
                {meta && !meta.creditsEnforced && (
                  <span className="text-slate-500">(not enforced)</span>
                )}
              </Link>
              <span className="hidden text-xs text-slate-400 sm:inline">{user.name}</span>
              <button
                type="button"
                className="btn-ghost !py-1.5 text-xs"
                onClick={() => {
                  signOut();
                  navigate('/');
                }}
              >
                Sign out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn-ghost !py-1.5 text-xs">
                Sign in
              </Link>
              <Link to="/register" className="btn-primary !py-1.5 text-xs">
                Create account
              </Link>
            </>
          )}
        </div>
      </header>

      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  );
}
