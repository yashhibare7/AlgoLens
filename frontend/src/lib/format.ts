import type { ElementState, ExecutionStatus, TraceAction } from '../types';

/** Renders a trace value the way the interpreter would print it. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return 'null';
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false';
  }
  if (typeof value === 'number' || typeof value === 'string') {
    // Note: the backend distinguishes int from double, JSON does not -- a whole double arrives
    // as 4, not 4.0. Rather than guess, the variables panel prints the declared type beside the
    // value, so `double x  4` is unambiguous without inventing a decimal point here.
    return String(value);
  }
  return JSON.stringify(value);
}

export const CELL_CLASSES: Record<ElementState, string> = {
  DEFAULT: 'bg-slate-800 border-slate-700 text-slate-200',
  ACTIVE: 'bg-sky-500/25 border-sky-400 text-sky-100',
  COMPARING: 'bg-amber-500/25 border-amber-400 text-amber-100',
  WRITTEN: 'bg-violet-500/25 border-violet-400 text-violet-100',
  SWAPPING: 'bg-rose-500/30 border-rose-400 text-rose-100',
  SORTED: 'bg-emerald-500/20 border-emerald-500 text-emerald-100',
  FOUND: 'bg-emerald-500/30 border-emerald-400 text-emerald-100',
  PIVOT: 'bg-fuchsia-500/25 border-fuchsia-400 text-fuchsia-100',
  EXCLUDED: 'bg-slate-900 border-slate-800 text-slate-600',
};

/**
 * The same palette as {@link CELL_CLASSES}, as literal colours.
 *
 * <p>Object graphs are drawn in SVG, where Tailwind class names cannot be applied to `fill` and
 * `stroke`. Keeping both in this file is what stops an array cell and a list node from drifting
 * to different shades of "being compared".
 */
export const NODE_COLORS: Record<ElementState, { fill: string; stroke: string; text: string }> = {
  DEFAULT: { fill: '#1e293b', stroke: '#334155', text: '#e2e8f0' },
  ACTIVE: { fill: '#0c4a6e', stroke: '#38bdf8', text: '#e0f2fe' },
  COMPARING: { fill: '#78350f', stroke: '#fbbf24', text: '#fef3c7' },
  WRITTEN: { fill: '#4c1d95', stroke: '#a78bfa', text: '#ede9fe' },
  SWAPPING: { fill: '#881337', stroke: '#fb7185', text: '#ffe4e6' },
  SORTED: { fill: '#064e3b', stroke: '#34d399', text: '#d1fae5' },
  FOUND: { fill: '#065f46', stroke: '#6ee7b7', text: '#d1fae5' },
  PIVOT: { fill: '#701a75', stroke: '#e879f9', text: '#fae8ff' },
  EXCLUDED: { fill: '#0f172a', stroke: '#1e293b', text: '#64748b' },
};

export const ACTION_LABELS: Record<TraceAction, string> = {
  START: 'Start',
  STATEMENT: 'Statement',
  DECLARE: 'Declare',
  ASSIGN: 'Assign',
  ARRAY_READ: 'Read',
  ARRAY_WRITE: 'Write',
  FIELD_READ: 'Read field',
  FIELD_WRITE: 'Set field',
  ALLOCATE: 'New object',
  COMPARE: 'Compare',
  SWAP: 'Swap',
  CONDITION: 'Condition',
  CALL: 'Call',
  RETURN: 'Return',
  OUTPUT: 'Print',
  DONE: 'Done',
  ERROR: 'Error',
};

export const ACTION_CLASSES: Record<TraceAction, string> = {
  START: 'bg-slate-700 text-slate-200',
  STATEMENT: 'bg-slate-700 text-slate-200',
  DECLARE: 'bg-teal-600/30 text-teal-200',
  ASSIGN: 'bg-sky-600/30 text-sky-200',
  ARRAY_READ: 'bg-sky-600/30 text-sky-200',
  ARRAY_WRITE: 'bg-violet-600/30 text-violet-200',
  FIELD_READ: 'bg-sky-600/30 text-sky-200',
  FIELD_WRITE: 'bg-violet-600/30 text-violet-200',
  ALLOCATE: 'bg-cyan-600/30 text-cyan-200',
  COMPARE: 'bg-amber-600/30 text-amber-200',
  SWAP: 'bg-rose-600/30 text-rose-200',
  CONDITION: 'bg-slate-600/40 text-slate-200',
  CALL: 'bg-indigo-600/30 text-indigo-200',
  RETURN: 'bg-indigo-600/30 text-indigo-200',
  OUTPUT: 'bg-lime-600/30 text-lime-200',
  DONE: 'bg-emerald-600/30 text-emerald-200',
  ERROR: 'bg-red-600/40 text-red-200',
};

/** Timeline tick colour, so the shape of the run is readable at a glance. */
export const ACTION_TICK_CLASSES: Record<TraceAction, string> = {
  START: 'bg-slate-600',
  STATEMENT: 'bg-slate-600',
  DECLARE: 'bg-teal-500',
  ASSIGN: 'bg-sky-500',
  ARRAY_READ: 'bg-sky-500',
  ARRAY_WRITE: 'bg-violet-500',
  FIELD_READ: 'bg-sky-500',
  FIELD_WRITE: 'bg-violet-500',
  ALLOCATE: 'bg-cyan-500',
  COMPARE: 'bg-amber-500',
  SWAP: 'bg-rose-500',
  CONDITION: 'bg-slate-500',
  CALL: 'bg-indigo-500',
  RETURN: 'bg-indigo-500',
  OUTPUT: 'bg-lime-500',
  DONE: 'bg-emerald-500',
  ERROR: 'bg-red-500',
};

export const STATUS_LABELS: Record<ExecutionStatus, string> = {
  SUCCESS: 'Ran successfully',
  COMPILE_ERROR: 'Syntax error',
  RUNTIME_ERROR: 'Runtime error',
  TIMEOUT: 'Timed out',
  LIMIT_EXCEEDED: 'Limit exceeded',
  UNSUPPORTED_LANGUAGE: 'Not supported yet',
  INTERNAL_ERROR: 'Internal error',
};

export const STATUS_CLASSES: Record<ExecutionStatus, string> = {
  SUCCESS: 'text-emerald-300 border-emerald-800 bg-emerald-950/50',
  COMPILE_ERROR: 'text-amber-300 border-amber-800 bg-amber-950/50',
  RUNTIME_ERROR: 'text-red-300 border-red-900 bg-red-950/50',
  TIMEOUT: 'text-orange-300 border-orange-900 bg-orange-950/50',
  LIMIT_EXCEEDED: 'text-orange-300 border-orange-900 bg-orange-950/50',
  UNSUPPORTED_LANGUAGE: 'text-slate-300 border-slate-700 bg-slate-900',
  INTERNAL_ERROR: 'text-red-300 border-red-900 bg-red-950/50',
};

export function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms} ms`;
  }
  return `${(ms / 1000).toFixed(2)} s`;
}

export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) {
    return iso;
  }
  const seconds = Math.round((Date.now() - then) / 1000);
  if (seconds < 60) {
    return 'just now';
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  }
  const days = Math.round(hours / 24);
  if (days < 30) {
    return `${days} day${days === 1 ? '' : 's'} ago`;
  }
  return new Date(then).toLocaleDateString();
}

export const DIFFICULTY_CLASSES: Record<string, string> = {
  EASY: 'text-emerald-300 border-emerald-800',
  MEDIUM: 'text-amber-300 border-amber-800',
  HARD: 'text-rose-300 border-rose-800',
};
