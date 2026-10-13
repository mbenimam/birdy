import { useId, type InputHTMLAttributes } from 'react';
import { cx } from '../lib/cx';

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className' | 'placeholder'> {
  label: string;
  name: string;
  /** Server-side message for this field, shown under the input. */
  error?: string | undefined;
  /** Short hint about the expected format, shown under the input. */
  hint?: string;
  compact?: boolean;
}

/** Floating-label text input of the original design, with a real label and an error slot. */
export function Field({ label, error, hint, compact = false, type = 'text', ...inputProps }: FieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = cx(error !== undefined && errorId, hint !== undefined && hintId);

  return (
    <div className="field-group">
      <div
        className={cx(
          'field',
          compact && 'field--compact',
          error !== undefined && 'field--invalid',
          // A date input never shows its placeholder, so its label stays raised.
          type === 'date' && 'field--always-active',
        )}
      >
        <input
          {...inputProps}
          id={id}
          type={type}
          className="field__input"
          placeholder={label}
          aria-invalid={error !== undefined}
          aria-describedby={describedBy === '' ? undefined : describedBy}
        />
        <label htmlFor={id} className="field__label">
          {label}
        </label>
      </div>
      {hint !== undefined && (
        <p id={hintId} className="sr-only">
          {hint}
        </p>
      )}
      {error !== undefined && (
        <p id={errorId} className="field__error">
          {error}
        </p>
      )}
    </div>
  );
}
