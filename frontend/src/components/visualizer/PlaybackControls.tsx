import clsx from 'clsx';
import { SPEEDS, type Player, type Speed } from '../../hooks/usePlayer';

/** Transport controls. Keyboard shortcuts for the same actions live in the visualizer page. */
export default function PlaybackControls({ player }: { player: Player }) {
  const disabled = player.totalSteps === 0;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <button
        type="button"
        className="btn-icon"
        onClick={player.reset}
        disabled={disabled || player.atStart}
        title="Restart (Home)"
        aria-label="Restart"
      >
        &#8635;
      </button>

      <button
        type="button"
        className="btn-icon"
        onClick={player.previous}
        disabled={disabled || player.atStart}
        title="Previous step (Left arrow)"
        aria-label="Previous step"
      >
        &#9664;
      </button>

      <button
        type="button"
        className="btn-primary w-24"
        onClick={player.toggle}
        disabled={disabled}
        title="Play / pause (Space)"
      >
        {player.playing ? '⏸ Pause' : '▶ Play'}
      </button>

      <button
        type="button"
        className="btn-icon"
        onClick={player.next}
        disabled={disabled || player.atEnd}
        title="Next step (Right arrow)"
        aria-label="Next step"
      >
        &#9654;
      </button>

      <button
        type="button"
        className="btn-icon"
        onClick={player.jumpToEnd}
        disabled={disabled || player.atEnd}
        title="Jump to the end (End)"
        aria-label="Jump to end"
      >
        &#9197;
      </button>

      <div className="ml-1 flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-900 p-0.5">
        {SPEEDS.map((speed: Speed) => (
          <button
            key={speed}
            type="button"
            onClick={() => player.setSpeed(speed)}
            className={clsx(
              'rounded px-2 py-1 text-[11px] font-semibold transition-colors',
              player.speed === speed
                ? 'bg-sky-600 text-white'
                : 'text-slate-400 hover:text-slate-200',
            )}
            title={`${speed}x playback speed`}
          >
            {speed}x
          </button>
        ))}
      </div>

      <span className="ml-auto font-mono text-xs text-slate-400">
        step {disabled ? 0 : player.index + 1} / {player.totalSteps}
      </span>
    </div>
  );
}
