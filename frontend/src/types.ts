/**
 * The API contract, mirrored from the backend.
 *
 * These types are the single reason the visualizer components are language agnostic: they
 * describe an execution trace, not a Java execution trace. When a Python or C++ executor is
 * added, nothing in this file changes.
 *
 * Kept hand-written rather than generated from the OpenAPI document on purpose -- the surface is
 * small, and a generated client would add a build step and a lot of noise for no benefit at this
 * size. If it grows, generate it from `/v3/api-docs`.
 */

export type ExecutionStatus =
  | 'SUCCESS'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'LIMIT_EXCEEDED'
  | 'UNSUPPORTED_LANGUAGE'
  | 'INTERNAL_ERROR';

export type TraceAction =
  | 'START'
  | 'STATEMENT'
  | 'DECLARE'
  | 'ASSIGN'
  | 'ARRAY_READ'
  | 'ARRAY_WRITE'
  | 'FIELD_READ'
  | 'FIELD_WRITE'
  | 'ALLOCATE'
  | 'COMPARE'
  | 'SWAP'
  | 'CONDITION'
  | 'CALL'
  | 'RETURN'
  | 'OUTPUT'
  | 'DONE'
  | 'ERROR';

export type ElementState =
  | 'DEFAULT'
  | 'ACTIVE'
  | 'COMPARING'
  | 'SWAPPING'
  | 'WRITTEN'
  | 'SORTED'
  | 'PIVOT'
  | 'FOUND'
  | 'EXCLUDED';

export type VisualizationType =
  | 'ARRAY'
  | 'MATRIX'
  | 'MAP'
  | 'SET'
  | 'LINKED_LIST'
  | 'STACK'
  | 'QUEUE'
  | 'TREE'
  | 'GRAPH';

export type TouchKind = 'READ' | 'WRITE' | 'COMPARE' | 'SWAP';

export interface Touch {
  target: string;
  index: number;
  kind: TouchKind;
  value?: unknown;
}

export interface ArrayElement {
  index: number;
  value: unknown;
  state: ElementState;
}

/**
 * A variable marking a position in a structure. Exactly one of `index` (an array cell) and
 * `nodeId` (an object) is present.
 */
export interface Pointer {
  name: string;
  index?: number;
  nodeId?: string;
}

/** One object in a reference-linked structure. */
export interface GraphNode {
  id: string;
  className: string;
  /** The first non-reference field's value: the payload a learner reads off the node. */
  value?: unknown;
  fields: VariableValue[];
  state: ElementState;
}

/** A reference between objects. `to` is null when the field is null. */
export interface GraphEdge {
  from: string;
  to?: string | null;
  label: string;
}

/** One key/value pair of a map. `index` is its position in iteration order. */
export interface MapEntryState {
  index: number;
  key: unknown;
  value: unknown;
  state: ElementState;
}

export interface VisualizationState {
  type: VisualizationType;
  name: string;
  elementType?: string;
  elements?: ArrayElement[];
  rows?: ArrayElement[][];
  entries?: MapEntryState[];
  pointers?: Pointer[];
  nodes?: GraphNode[];
  edges?: GraphEdge[];
}

export interface VariableValue {
  name: string;
  type: string;
  value: unknown;
  changed: boolean;
}

export interface StackFrameState {
  method: string;
  line: number;
  variables: VariableValue[];
}

export interface TraceEvent {
  step: number;
  line: number;
  action: TraceAction;
  message: string;
  variables?: VariableValue[];
  visualizations?: VisualizationState[];
  callStack?: StackFrameState[];
  touches?: Touch[];
  output?: string;
  depth: number;
}

export interface TraceMetrics {
  statements: number;
  comparisons: number;
  swaps: number;
  arrayReads: number;
  arrayWrites: number;
  fieldReads: number;
  fieldWrites: number;
  objectsCreated: number;
  calls: number;
  maxCallDepth: number;
}

export interface ExecutionTrace {
  language: string;
  status: ExecutionStatus;
  steps: TraceEvent[];
  totalSteps: number;
  truncated: boolean;
  stdout: string;
  errorMessage?: string;
  errorLine?: number;
  durationMs: number;
  metrics: TraceMetrics;
}

export interface ExecutionResponse {
  executionId?: number;
  trace: ExecutionTrace;
  creditsSpent: number;
  creditBalance?: number;
}

// ---------------------------------------------------------------- accounts

export interface User {
  id: number;
  name: string;
  email: string;
  role: string;
  creditBalance: number;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

// ---------------------------------------------------------------- library & history

export interface TestCaseSample {
  arguments: string[];
  expectedOutput: string;
  explanation?: string;
}

export interface Problem {
  id: number;
  slug: string;
  title: string;
  description?: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  category: string;
  language: string;
  starterCode?: string;
  solutionCode?: string;
  judgeEnabled: boolean;
  functionSignature?: string;
  sampleTestCases: TestCaseSample[];
}

// ---------------------------------------------------------------- judge

export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'RUNTIME_ERROR'
  | 'COMPILE_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'INTERNAL_ERROR';

/** `actualOutput`/`expectedOutput` are only present for sample test cases. */
export interface TestCaseOutcome {
  index: number;
  sample: boolean;
  passed: boolean;
  actualOutput?: string;
  expectedOutput?: string;
  errorMessage?: string;
}

export interface JudgeResult {
  verdict: Verdict;
  passedCount: number;
  totalCount: number;
  outcomes: TestCaseOutcome[];
  errorMessage?: string;
  durationMs: number;
}

export interface SubmissionResponse {
  submissionId?: number;
  result: JudgeResult;
  creditsSpent: number;
  creditBalance?: number;
}

export interface SubmissionSummary {
  id: number;
  verdict: Verdict;
  passedCount: number;
  totalCount: number;
  durationMs: number;
  createdAt: string;
}

export interface ExecutionSummary {
  id: number;
  language: string;
  status: ExecutionStatus;
  title: string;
  totalSteps: number;
  durationMs: number;
  creditsSpent: number;
  replayable: boolean;
  createdAt: string;
}

export interface ExecutionDetail {
  id: number;
  language: string;
  status: ExecutionStatus;
  code: string;
  errorMessage?: string;
  totalSteps: number;
  durationMs: number;
  creditsSpent: number;
  trace?: ExecutionTrace;
  createdAt: string;
}

export interface SavedCode {
  id: number;
  title: string;
  language: string;
  code?: string;
  problemId?: number;
  problemTitle?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface DashboardResponse {
  user: User;
  totalExecutions: number;
  successfulExecutions: number;
  problemsSolved: number;
  savedSnippets: number;
  creditBalance: number;
  recentExecutions: ExecutionSummary[];
}

// ---------------------------------------------------------------- credits

export interface CreditBalance {
  balance: number;
  enforced: boolean;
  costs: Record<string, number>;
}

export interface CreditTransaction {
  id: number;
  amount: number;
  type: string;
  description: string;
  balanceAfter: number;
  createdAt: string;
}

// ---------------------------------------------------------------- AI

export type ExplainMode = 'EXPLAIN_STEP' | 'EXPLAIN_CODE' | 'FIND_BUG' | 'COMPLEXITY';

export interface ExplainResponse {
  mode: ExplainMode;
  explanation: string;
  provider: string;
  model?: string;
  creditsSpent: number;
  creditBalance?: number;
}

// ---------------------------------------------------------------- meta

export interface LanguageInfo {
  id: string;
  displayName: string;
  supported: boolean;
}

export interface MetaResponse {
  version: string;
  languages: LanguageInfo[];
  limits: Record<string, number>;
  creditCosts: Record<string, number>;
  creditsEnforced: boolean;
  aiEnabled: boolean;
  aiProvider: string;
  anonymousExecutionAllowed: boolean;
}

// ---------------------------------------------------------------- errors

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
  details?: Record<string, unknown>;
}
