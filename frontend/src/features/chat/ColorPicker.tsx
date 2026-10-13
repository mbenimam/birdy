import { FullScreen } from '../../components/FullScreen';
import { cx } from '../../lib/cx';

/** Number of bubble gradients defined in styles/abstracts/_variables.scss ($chat-gradients). */
export const CHAT_COLOR_COUNT = 18;
export const DEFAULT_CHAT_COLOR = 7;

interface ColorPickerProps {
  selected: number;
  onSelect: (color: number) => void;
  onClose: () => void;
}

export function ColorPicker({ selected, onSelect, onClose }: ColorPickerProps) {
  const colors = Array.from({ length: CHAT_COLOR_COUNT }, (_, index) => index + 1);

  return (
    <FullScreen label="Choose the colour of your messages" onClose={onClose} className="fullsc--color-picker">
      <div className="card card--color-picker">
        <div className="color-picker">
          {colors.map((color) => (
            <button
              key={color}
              type="button"
              className={cx('color-picker__color', `color-picker__color--${color}`)}
              aria-pressed={color === selected}
              onClick={() => {
                onSelect(color);
              }}
            >
              <span className="sr-only">Colour {color}</span>
            </button>
          ))}
        </div>
      </div>
    </FullScreen>
  );
}
