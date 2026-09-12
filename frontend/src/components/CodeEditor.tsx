import Editor from '@monaco-editor/react';
import { useEffect, useRef } from 'react';

interface Props {
  value: string;
  onChange: (value: string) => void;
  /** 1-based line to highlight, or null while nothing is executing. */
  highlightLine: number | null;
  language?: string;
  readOnly?: boolean;
  /** Lines with a breakpoint. Playback pauses on arrival at one. */
  breakpoints?: ReadonlySet<number>;
  onToggleBreakpoint?: (line: number) => void;
}

/**
 * Monaco, with the executing line highlighted and breakpoints in the gutter.
 *
 * <p>The highlight is the thing that turns two panels into one explanation: the array moves on
 * the right while the line that moved it lights up on the left. It is driven straight off
 * {@code TraceEvent.line}, which is why every AST node in the backend carries a line number.
 *
 * <p>Breakpoints matter once a trace is long. A 279-step run is too much to step through, but
 * "run until line 42" turns it into three clicks -- and because the whole trace is already in
 * the browser, that is a search over an array rather than anything the backend needs to know
 * about.
 */
export default function CodeEditor({
  value,
  onChange,
  highlightLine,
  language = 'java',
  readOnly = false,
  breakpoints,
  onToggleBreakpoint,
}: Props) {
  // Typed loosely on purpose: Monaco's own types are only available through the editor
  // package, and pulling that in as a build-time dependency to describe a handful of method
  // calls is not worth it. Every access below is guarded.
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<any>(null);
  const lineDecorations = useRef<any>(null);
  const breakpointDecorations = useRef<any>(null);
  // Read inside the Monaco mouse handler, which is registered once and would otherwise
  // capture the first render's callback forever.
  const toggleRef = useRef<Props['onToggleBreakpoint']>(onToggleBreakpoint);
  toggleRef.current = onToggleBreakpoint;

  useEffect(() => {
    const editor = editorRef.current;
    const monaco = monacoRef.current;
    if (!editor || !monaco) {
      return;
    }

    if (!lineDecorations.current) {
      lineDecorations.current = editor.createDecorationsCollection([]);
    }

    if (highlightLine == null || highlightLine < 1) {
      lineDecorations.current.set([]);
      return;
    }

    lineDecorations.current.set([
      {
        range: new monaco.Range(highlightLine, 1, highlightLine, 1),
        options: {
          isWholeLine: true,
          className: 'algolens-current-line',
          linesDecorationsClassName: 'algolens-current-line-gutter',
        },
      },
    ]);
    // Keeps the current line on screen while stepping through a long method.
    editor.revealLineInCenterIfOutsideViewport(highlightLine);
  }, [highlightLine]);

  useEffect(() => {
    const editor = editorRef.current;
    const monaco = monacoRef.current;
    if (!editor || !monaco) {
      return;
    }
    if (!breakpointDecorations.current) {
      breakpointDecorations.current = editor.createDecorationsCollection([]);
    }
    breakpointDecorations.current.set(
      [...(breakpoints ?? [])].map((line) => ({
        range: new monaco.Range(line, 1, line, 1),
        options: {
          isWholeLine: true,
          glyphMarginClassName: 'algolens-breakpoint',
          glyphMarginHoverMessage: { value: 'Breakpoint — playback pauses here' },
        },
      })),
    );
  }, [breakpoints]);

  return (
    <Editor
      height="100%"
      language={language}
      theme="vs-dark"
      value={value}
      onChange={(next) => onChange(next ?? '')}
      onMount={(editor, monaco) => {
        editorRef.current = editor;
        monacoRef.current = monaco;

        editor.onMouseDown((event: any) => {
          // 2 === GUTTER_GLYPH_MARGIN. Compared numerically so the enum does not have to be
          // imported from a package that is only present at runtime.
          if (event?.target?.type !== 2) {
            return;
          }
          const line = event.target.position?.lineNumber;
          if (line && toggleRef.current) {
            toggleRef.current(line);
          }
        });
      }}
      loading={
        <div className="flex h-full items-center justify-center text-sm text-slate-500">
          Loading editor...
        </div>
      }
      options={{
        readOnly,
        fontSize: 13,
        fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        renderLineHighlight: 'none',
        lineNumbersMinChars: 3,
        glyphMargin: true,
        folding: false,
        automaticLayout: true,
        tabSize: 4,
        padding: { top: 12, bottom: 12 },
        smoothScrolling: true,
        scrollbar: { verticalScrollbarSize: 8, horizontalScrollbarSize: 8 },
      }}
    />
  );
}
