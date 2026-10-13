import { ApiError, errorMessage } from '../api/errors';

/** Result of a form action: what the server rejected, plus the values to put back in the inputs. */
export interface FormState {
  message: string | null;
  fields: Readonly<Record<string, string>>;
  values: Readonly<Record<string, string>>;
}

export const emptyFormState: FormState = { message: null, fields: {}, values: {} };

export function failedFormState(error: unknown, values: Record<string, string>): FormState {
  if (error instanceof ApiError && Object.keys(error.fields).length > 0) {
    // Field messages are shown next to their inputs; the general message would only repeat them.
    return { message: null, fields: error.fields, values };
  }
  return { message: errorMessage(error), fields: {}, values };
}
