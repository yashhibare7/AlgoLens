/**
 * Program output, accumulated only up to the current step.
 *
 * <p>Showing the whole run's output at step 3 would spoil what the trace is about to reveal, so
 * this stays in sync with the playhead -- stepping backwards un-prints.
 */
export default function OutputPanel({ output }: { output: string }) {
  if (!output) {
    return (
      <p className="px-4 py-3 text-xs text-slate-500">
        Nothing printed yet. Use <code className="font-mono">System.out.println(...)</code> to
        print from your code.
      </p>
    );
  }

  return (
    <pre className="max-h-40 overflow-auto whitespace-pre-wrap px-4 py-3 font-mono text-xs
      leading-relaxed text-lime-200">
      {output}
    </pre>
  );
}
