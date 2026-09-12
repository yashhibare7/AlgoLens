import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../store/AuthContext';

export default function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await signIn(email, password);
      navigate('/dashboard');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not sign in.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-sm flex-col justify-center gap-5 px-4 py-16">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Welcome back</h1>
        <p className="mt-1 text-sm text-slate-400">
          Sign in to save your code, keep your run history and use AI explanations.
        </p>
      </div>

      <form className="space-y-3" onSubmit={submit}>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-400">Email</span>
          <input
            className="field"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>

        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-400">Password</span>
          <input
            className="field"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>

        {error && (
          <p className="rounded-lg border border-red-900 bg-red-950/50 px-3 py-2 text-xs text-red-300">
            {error}
          </p>
        )}

        <button type="submit" className="btn-primary w-full" disabled={pending}>
          {pending ? 'Signing in...' : 'Sign in'}
        </button>
      </form>

      <p className="text-sm text-slate-400">
        No account yet?{' '}
        <Link to="/register" className="text-sky-400 hover:underline">
          Create one
        </Link>
      </p>
    </div>
  );
}
