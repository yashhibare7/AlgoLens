import clsx from 'clsx';
import { useMemo } from 'react';
import type { ExecutionTrace } from '../../types';
import { ACTION_TICK_CLASSES } from '../../lib/format';

interface Props {
  trace: ExecutionTrace;
  index: number;
  onSeek: (index: number) => void;
}

/** Above this, one tick per step stops being a shape and becomes noise. */
const MAX_TICKS = 400;

/**
 * A scrubber that also shows the shape of the run.
 *
 * <p>Each tick is coloured by what that step did, so a bubble sort reads as bands of compares
 * punctuated by swaps -- you can see the algorithm's rhythm before stepping into it. Long runs
 * are sampled down so the strip stays legible.
 */
export default function Timeline({ trace, index, onSeek }: Props) {
  const ticks = useMemo(() => {
    const steps = trace.steps;
    if (steps.length <= MAX_TICKS) {
      return steps.map((step, position) => ({ action: step.action, position }));
    }
    const stride = steps.length / MAX_TICKS;
    return Array.from({ length: MAX_TICKS }, (_, slot) => {
      const position = Math.min(steps.length - 1, Math.floor(slot * stride));
      return { action: steps[position].action, position };
    });
  }, [trace.steps]);

  const progress = trace.steps.length <= 1 ? 0 : (index / (trace.steps.length - 1)) * 100;

  return (
    <div className="space-y-1.5">
      <div className="flex h-6 items-end gap-px overflow-hidden rounded" role="presentation">
        {ticks.map((tick) => (
          <button
            key={tick.position}
            type="button"
            onClick={() => onSeek(tick.position)}
            title={`Step ${tick.position + 1}: ${tick.action}`}
            className={clsx(
              'h-full flex-1 min-w-px transition-opacity hover:opacity-100',
              ACTION_TICK_CLASSES[tick.action],
              tick.position <= index ? 'opacity-100' : 'opacity-30',
            )}
          />
        ))}
      </div>

      <input
        type="range"
        min={0}
        max={Math.max(0, trace.steps.length - 1)}
        value={index}
        onChange={(event) => onSeek(Number(event.target.value))}
        className="h-1.5 w-full cursor-pointer appearance-none rounded-full bg-slate-800
          accent-sky-500"
        style={{
          background: `linear-gradient(to right, rgb(2 132 199) ${progress}%, rgb(30 41 59) ${progress}%)`,
        }}
        aria-label="Scrub through the execution"
      />
    </div>
  );
}
