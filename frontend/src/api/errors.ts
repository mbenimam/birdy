export type ApiErrorCode =
  | 'validation_failed'
  | 'unauthorized'
  | 'forbidden'
  | 'not_found'
  | 'conflict'
  | 'internal_error'
  /** The request never produced an HTTP response (offline, DNS, aborted connection). */
  | 'network_error';

const CODE_BY_STATUS: Record<number, ApiErrorCode> = {
  400: 'validation_failed',
  401: 'unauthorized',
  403: 'forbidden',
  404: 'not_found',
  409: 'conflict',
};

const KNOWN_CODES = new Set<string>([...Object.values(CODE_BY_STATUS), 'internal_error']);

/** Typed form of the contract's error body `{ error, message, fields }`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  /** Per-field messages; only filled for `validation_failed`. */
  readonly fields: Readonly<Record<string, string>>;

  constructor(status: number, code: ApiErrorCode, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readFields(value: unknown): Record<string, string> {
  if (!isRecord(value)) return {};
  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  );
}

/** Builds an ApiError from a failed response, tolerating bodies that are not the contract's JSON (e.g. a proxy's HTML 502). */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  const fallbackCode = CODE_BY_STATUS[response.status] ?? 'internal_error';
  if (!isRecord(body)) {
    return new ApiError(response.status, fallbackCode, `The server answered with status ${response.status}.`);
  }

  const code =
    typeof body.error === 'string' && KNOWN_CODES.has(body.error) ? (body.error as ApiErrorCode) : fallbackCode;
  const message =
    typeof body.message === 'string' && body.message !== ''
      ? body.message
      : `The server answered with status ${response.status}.`;
  return new ApiError(response.status, code, message, readFields(body.fields));
}

/** Text to show for any thrown value. */
export function errorMessage(error: unknown): string {
  return error instanceof Error && error.message !== '' ? error.message : 'Something went wrong.';
}
