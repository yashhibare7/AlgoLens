import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api, setUnauthenticatedHandler, tokenStore } from '../api/client';
import type { MetaResponse, User } from '../types';

interface AuthState {
  user: User | null;
  meta: MetaResponse | null;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => void;
  /** Keeps the credit badge current after a run without a second round trip. */
  setCreditBalance: (balance: number) => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Holds the session and the backend's capability document.
 *
 * <p>Both live here because both are needed before the first render decision: whether to show
 * the dashboard link, and whether the language picker should offer anything but Java.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [meta, setMeta] = useState<MetaResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const signOut = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    // A token the server rejects is worse than no token: it makes every request fail. Drop it.
    setUnauthenticatedHandler(() => {
      tokenStore.clear();
      setUser(null);
    });
  }, []);

  useEffect(() => {
    let cancelled = false;

    const boot = async () => {
      // Meta is public, so it loads even when signed out; a failure here means the backend is
      // down, which the visualizer page reports on its own.
      const metaPromise = api.meta().catch(() => null);
      const userPromise = tokenStore.get() ? api.auth.me().catch(() => null) : Promise.resolve(null);

      const [loadedMeta, loadedUser] = await Promise.all([metaPromise, userPromise]);
      if (cancelled) {
        return;
      }
      setMeta(loadedMeta);
      setUser(loadedUser);
      setLoading(false);
    };

    void boot();
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await api.auth.login(email, password);
    tokenStore.set(response.token);
    setUser(response.user);
  }, []);

  const signUp = useCallback(async (name: string, email: string, password: string) => {
    const response = await api.auth.register(name, email, password);
    tokenStore.set(response.token);
    setUser(response.user);
  }, []);

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) {
      return;
    }
    const fresh = await api.auth.me().catch(() => null);
    setUser(fresh);
  }, []);

  const setCreditBalance = useCallback((balance: number) => {
    setUser((current) => (current ? { ...current, creditBalance: balance } : current));
  }, []);

  const value = useMemo<AuthState>(
    () => ({ user, meta, loading, signIn, signUp, signOut, setCreditBalance, refresh }),
    [user, meta, loading, signIn, signUp, signOut, setCreditBalance, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
