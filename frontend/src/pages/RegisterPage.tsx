import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../store/AuthContext';

export default function RegisterPage() {
  const { signUp, meta } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    setFieldErrors({});
    try {
      await signUp(name, email, password);
      navigate('/dashboard');
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors ?? {});
      } else {
        setError('Could not create that account.');
      }
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-sm flex-col justify-center gap-5 px-4 py-16">
      <div>
        <h1 className="text-xl font-bold text-slate-100">Create your account</h1>
        <p className="mt-1 text-sm text-slate-400">
          Free. Includes a monthly credit allowance
          {meta && !meta.creditsEnforced ? ', which is not enforced yet' : ''}, saved code, run
          history and AI explanations.
        </p>
      </div>

      <form className="space-y-3" onSubmit={submit}>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-400">Name</span>
          <input
            className="field"
            required
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          {fieldErrors.name && <p className="text-xs text-red-400">{fieldErrors.name}</p>}
        </label>

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
          {fieldErrors.email && <p className="text-xs text-red-400">{fieldErrors.email}</p>}
        </label>

        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-400">Password</span>
          <input
            className="field"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <span className="text-[11px] text-slate-500">At least 8 characters.</span>
          {fieldErrors.password && <p className="text-xs text-red-400">{fieldErrors.password}</p>}
        </label>

        {error && (
          <p className="rounded-lg border border-red-900 bg-red-950/50 px-3 py-2 text-xs text-red-300">
            {error}
          </p>
        )}

        <button type="submit" className="btn-primary w-full" disabled={pending}>
          {pending ? 'Creating...' : 'Create account'}
        </button>
      </form>

      <p className="text-sm text-slate-400">
        Already have one?{' '}
        <Link to="/login" className="text-sky-400 hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
