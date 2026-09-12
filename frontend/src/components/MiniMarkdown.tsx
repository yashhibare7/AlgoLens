import { Fragment, type ReactNode } from 'react';

/**
 * A deliberately tiny markdown renderer for AI explanations.
 *
 * <p>Supports paragraphs, `- ` bullets, `**bold**`, `` `code` `` and fenced code blocks. That
 * covers what the prompt asks the model to produce, and a full markdown library plus a
 * sanitiser would be a large dependency and a real XSS surface for model-authored text. Here
 * nothing is ever inserted as HTML -- every branch builds React elements, so the output cannot
 * become markup.
 */
export default function MiniMarkdown({ text }: { text: string }) {
  const blocks = splitBlocks(text);

  return (
    <div className="space-y-2.5 text-sm leading-relaxed text-slate-300">
      {blocks.map((block, index) => {
        if (block.type === 'code') {
          return (
            <pre
              key={index}
              className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950 p-3
                font-mono text-xs text-slate-200"
            >
              {block.lines.join('\n')}
            </pre>
          );
        }
        if (block.type === 'list') {
          return (
            <ul key={index} className="list-disc space-y-1 pl-5">
              {block.lines.map((line, item) => (
                <li key={item}>{renderInline(line)}</li>
              ))}
            </ul>
          );
        }
        if (block.type === 'rule') {
          return <hr key={index} className="border-slate-800" />;
        }
        return <p key={index}>{renderInline(block.lines.join(' '))}</p>;
      })}
    </div>
  );
}

type Block = { type: 'paragraph' | 'list' | 'code' | 'rule'; lines: string[] };

function splitBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  let current: Block | null = null;
  let inFence = false;

  const flush = () => {
    if (current) {
      blocks.push(current);
      current = null;
    }
  };

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trimEnd();

    if (line.trimStart().startsWith('```')) {
      if (inFence) {
        flush();
        inFence = false;
      } else {
        flush();
        current = { type: 'code', lines: [] };
        inFence = true;
      }
      continue;
    }

    if (inFence) {
      current?.lines.push(rawLine);
      continue;
    }

    if (line.trim() === '') {
      flush();
      continue;
    }

    if (line.trim() === '---' || line.trim() === '***') {
      flush();
      blocks.push({ type: 'rule', lines: [] });
      continue;
    }

    const bullet = line.match(/^\s*[-*]\s+(.*)$/);
    if (bullet) {
      if (current?.type !== 'list') {
        flush();
        current = { type: 'list', lines: [] };
      }
      current.lines.push(bullet[1]);
      continue;
    }

    if (current?.type !== 'paragraph') {
      flush();
      current = { type: 'paragraph', lines: [] };
    }
    current.lines.push(line.trim());
  }

  flush();
  return blocks;
}

/** Handles `**bold**`, `*italic*` and `` `code` `` in one pass. */
function renderInline(text: string): ReactNode {
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g;
  const pieces = text.split(pattern).filter((piece) => piece !== '');

  return (
    <>
      {pieces.map((piece, index) => {
        if (piece.startsWith('**') && piece.endsWith('**') && piece.length > 4) {
          return (
            <strong key={index} className="font-semibold text-slate-100">
              {piece.slice(2, -2)}
            </strong>
          );
        }
        if (piece.startsWith('`') && piece.endsWith('`') && piece.length > 2) {
          return (
            <code
              key={index}
              className="rounded bg-slate-800 px-1 py-0.5 font-mono text-[12px] text-sky-200"
            >
              {piece.slice(1, -1)}
            </code>
          );
        }
        if (piece.startsWith('*') && piece.endsWith('*') && piece.length > 2) {
          return (
            <em key={index} className="text-slate-400">
              {piece.slice(1, -1)}
            </em>
          );
        }
        return <Fragment key={index}>{piece}</Fragment>;
      })}
    </>
  );
}
