import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ExecutionTrace, TraceEvent } from '../types';

export const SPEEDS = [0.25, 0.5, 1, 2, 4] as const;
export type Speed = (typeof SPEEDS)[number];

/** Milliseconds per step at 1x. Slow enough to follow, fast enough not to feel like waiting. */
const BASE_INTERVAL_MS = 420;

export interface Player {
  index: number;
  step: TraceEvent | null;
  totalSteps: number;
  playing: boolean;
  speed: Speed;
  atStart: boolean;
  atEnd: boolean;
  /** Everything printed up to and including the current step. */
  outputSoFar: string;
  /** True when playback paused because it reached a breakpoint line. */
  pausedAtBreakpoint: boolean;
  play: () => void;
  pause: () => void;
  toggle: () => void;
  next: () => void;
  previous: () => void;
  reset: () => void;
  jumpToEnd: () => void;
  seek: (index: number) => void;
  setSpeed: (speed: Speed) => void;
  /** Jumps to the next step matching a predicate, or does nothing if there is none. */
  jumpForwardTo: (predicate: (step: TraceEvent) => boolean) => void;
  jumpBackwardTo: (predicate: (step: TraceEvent) => boolean) => void;
}

interface Options {
  /** Source lines to pause on when they are reached during playback. */
  breakpoints?: ReadonlySet<number>;
}

/**
 * Playback over a finished trace.
 *
 * <p>The whole trace already exists client-side, so playback is pure index movement -- no
 * streaming, no server round trips, and stepping backwards is exactly as cheap as stepping
 * forwards. That is the payoff of having the backend return the complete trace in one response
 * instead of driving execution interactively, and it is what makes breakpoints and
 * jump-to-next-event trivial rather than a protocol.
 */
export function usePlayer(trace: ExecutionTrace | null, options: Options = {}): Player {
  const steps = useMemo(() => trace?.steps ?? [], [trace]);
  const totalSteps = steps.length;
  const breakpoints = options.breakpoints;

  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<Speed>(1);
  const [pausedAtBreakpoint, setPausedAtBreakpoint] = useState(false);
  const timer = useRef<number | null>(null);

  // A new trace always starts from the beginning, paused.
  useEffect(() => {
    setIndex(0);
    setPlaying(false);
    setPausedAtBreakpoint(false);
  }, [trace]);

  const clearTimer = useCallback(() => {
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  useEffect(() => {
    if (!playing || totalSteps === 0) {
      return undefined;
    }
    if (index >= totalSteps - 1) {
      setPlaying(false);
      return undefined;
    }
    timer.current = window.setTimeout(() => {
      const target = Math.min(index + 1, totalSteps - 1);
      setIndex(target);
      // Stop on *arrival* at a breakpoint line, so pressing play again resumes past it -- the
      // way every debugger behaves.
      if (breakpoints?.size && breakpoints.has(steps[target].line)) {
        setPlaying(false);
        setPausedAtBreakpoint(true);
      }
    }, BASE_INTERVAL_MS / speed);

    return clearTimer;
  }, [playing, index, speed, totalSteps, clearTimer, breakpoints, steps]);

  useEffect(() => clearTimer, [clearTimer]);

  const seek = useCallback(
    (target: number) => {
      setPlaying(false);
      setPausedAtBreakpoint(false);
      setIndex(Math.max(0, Math.min(target, Math.max(0, totalSteps - 1))));
    },
    [totalSteps],
  );

  const play = useCallback(() => {
    if (totalSteps === 0) {
      return;
    }
    setPausedAtBreakpoint(false);
    // Pressing play at the end restarts rather than doing nothing, which is what people expect.
    setIndex((current) => (current >= totalSteps - 1 ? 0 : current));
    setPlaying(true);
  }, [totalSteps]);

  const pause = useCallback(() => setPlaying(false), []);

  const toggle = useCallback(() => {
    if (playing) {
      pause();
    } else {
      play();
    }
  }, [playing, pause, play]);

  const next = useCallback(() => seek(index + 1), [index, seek]);
  const previous = useCallback(() => seek(index - 1), [index, seek]);
  const reset = useCallback(() => seek(0), [seek]);
  const jumpToEnd = useCallback(() => seek(totalSteps - 1), [seek, totalSteps]);

  const jumpForwardTo = useCallback(
    (predicate: (step: TraceEvent) => boolean) => {
      for (let i = index + 1; i < steps.length; i++) {
        if (predicate(steps[i])) {
          seek(i);
          return;
        }
      }
    },
    [index, steps, seek],
  );

  const jumpBackwardTo = useCallback(
    (predicate: (step: TraceEvent) => boolean) => {
      for (let i = index - 1; i >= 0; i--) {
        if (predicate(steps[i])) {
          seek(i);
          return;
        }
      }
    },
    [index, steps, seek],
  );

  /**
   * Output is accumulated from per-step deltas rather than each step carrying the whole buffer,
   * which would make the trace payload grow quadratically with the number of prints.
   */
  const outputSoFar = useMemo(() => {
    let text = '';
    for (let i = 0; i <= index && i < steps.length; i++) {
      const chunk = steps[i]?.output;
      if (chunk) {
        text += chunk;
      }
    }
    return text;
  }, [index, steps]);

  return {
    index,
    step: steps[index] ?? null,
    totalSteps,
    playing,
    speed,
    atStart: index === 0,
    atEnd: totalSteps === 0 || index >= totalSteps - 1,
    outputSoFar,
    pausedAtBreakpoint,
    play,
    pause,
    toggle,
    next,
    previous,
    reset,
    jumpToEnd,
    seek,
    setSpeed,
    jumpForwardTo,
    jumpBackwardTo,
  };
}
