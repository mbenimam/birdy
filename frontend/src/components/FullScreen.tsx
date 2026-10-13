import { X } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cx } from '../lib/cx';

interface FullScreenProps {
  /** Accessible name of the dialog. */
  label: string;
  onClose: () => void;
  className?: string;
  children: ReactNode;
}

const FOCUSABLE = 'button:not(:disabled), [href], input:not(:disabled), textarea:not(:disabled), [tabindex="0"]';

/** Full-screen modal overlay: Escape closes it, Tab stays inside it, and focus returns to the opener. */
export function FullScreen({ label, onClose, className, children }: FullScreenProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Read while rendering, before an autofocused child can take the focus away from the opener.
  const [opener] = useState(() => document.activeElement);

  // Focus moves into the dialog on opening (unless a child already took it) and back to the opener on closing.
  useEffect(() => {
    const container = containerRef.current;
    if (container !== null && !container.contains(document.activeElement)) {
      container.querySelector<HTMLElement>(FOCUSABLE)?.focus();
    }
    return () => {
      if (opener instanceof HTMLElement) opener.focus();
    };
  }, [opener]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const container = containerRef.current;
      if (container === null) return;
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = [...container.querySelectorAll<HTMLElement>(FOCUSABLE)];
      const first = focusable[0];
      const last = focusable.at(-1);
      if (first === undefined || last === undefined) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  return (
    <div className="fullsc-container" role="dialog" aria-modal="true" aria-label={label} ref={containerRef}>
      <button type="button" className="btn-circle btn-circle--large fullsc-close" onClick={onClose}>
        <X aria-hidden="true" />
        <span className="sr-only">Close</span>
      </button>
      <div className={cx('fullsc', className)}>{children}</div>
    </div>
  );
}
