import { useRef, useState, type ChangeEvent, type ClipboardEvent, type InputEvent as ReactInputEvent, type KeyboardEvent } from 'react';
import PhoneInput, { getCountries, getCountryCallingCode, parsePhoneNumber, type Country } from 'react-phone-number-input/input';
import labels from 'react-phone-number-input/locale/zh';

const priority: Country[] = ['CA', 'US', 'CN'];
const countries = [...priority, ...getCountries().filter(country => !priority.includes(country))
  .sort((left, right) => labels[left].localeCompare(labels[right], 'zh'))];

function countryFor(value: string): Country | undefined {
  if (!value) return 'CA';
  // A phone number alone cannot distinguish every country sharing +1; use the local default.
  if (value.startsWith('+1')) return 'CA';
  return parsePhoneNumber(value)?.country ?? countries.find(country =>
    value.startsWith(`+${getCountryCallingCode(country)}`));
}

type Props = {
  value: string; onChange: (value: string) => void; disabled: boolean;
  invalid: boolean; describedBy: string;
};

export default function ContactPhoneInput({ value, onChange, disabled, invalid, describedBy }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const override = useRef<string | null>(null);
  // Keep a manually typed full number in international mode until blur, so its prefix is not added twice.
  const [internationalEditing, setInternationalEditing] = useState(false);
  const [formatVersion, setFormatVersion] = useState(0);
  const [editor, setEditor] = useState(() => ({ country: countryFor(value), value, formattedValue: value }));
  // Sync only an external load/save, so normal rerenders preserve the selected country and draft.
  if (value !== editor.value) {
    setEditor({ country: countryFor(value), value, formattedValue: value });
  }

  function update(next: string, country: Country | undefined, formattedValue = next) {
    setEditor({ country, value: next, formattedValue });
    onChange(next);
  }

  function captureRaw(event: ChangeEvent<HTMLInputElement>) {
    const raw = event.currentTarget.value;
    const compact = raw.replace(/[ ()-]/g, '');
    if (compact.startsWith('+')) setInternationalEditing(true);
    override.current = null;
    if (compact === '+') return;
    const full = compact.startsWith('+');
    if (full || raw.length > 64 || /[^0-9 ()-]/.test(raw)) {
      const next = full ? raw
        : `${editor.country ? `+${getCountryCallingCode(editor.country)}` : ''}${raw}`;
      const cleaned = next.length > 64 ? next : next.replace(/[ ()-]/g, '');
      override.current = cleaned;
      // Preserve malformed input for the existing validator, rather than silently saving filtered digits.
      update(cleaned, full ? countryFor(cleaned) : editor.country,
        next.startsWith('+') ? `+${next.replace(/\D/g, '')}` : editor.formattedValue);
    }
  }

  function changeCountry(event: ChangeEvent<HTMLSelectElement>) {
    const country = (event.target.value || undefined) as Country | undefined;
    const number = (input.current?.value ?? '').replace(/[ ()-]/g, '');
    override.current = null;
    const next = !number ? '' : country
      ? `+${getCountryCallingCode(country)}${number.replace(/^\+/, '')}` : editor.formattedValue;
    update(next, country);
  }

  return <div className="student-phone-input">
    <select aria-label="国家或地区" value={editor.country ?? ''} onChange={changeCountry}
      disabled={disabled} aria-describedby={describedBy}>
      <option value="">国际格式</option>
      {countries.map(country => <option key={country} value={country}>
        {labels[country]} +{getCountryCallingCode(country)}
      </option>)}
    </select>
    <PhoneInput key={formatVersion} ref={input} id="student-contact-phone" country={internationalEditing ? undefined : editor.country}
      international={!internationalEditing && editor.country ? true : undefined}
      smartCaret={false} value={editor.formattedValue || undefined} type="tel" inputMode="tel"
      autoComplete="tel-national" dir="ltr" required maxLength={64}
      placeholder={editor.country === 'CA' || editor.country === 'US' ? '416 555 0123' : editor.country === 'CN' ? '138 0013 8000' : '电话号码'}
      disabled={disabled} aria-invalid={invalid} aria-describedby={describedBy}
      onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => { if (event.key === '+') setInternationalEditing(true); }}
      onBeforeInput={(event: ReactInputEvent<HTMLInputElement>) => { if (event.nativeEvent.data === '+') setInternationalEditing(true); }}
      onChangeCapture={captureRaw}
      onPaste={(event: ClipboardEvent<HTMLInputElement>) => {
        const raw = event.clipboardData.getData('text');
        const compact = raw.replace(/[ ()-]/g, '');
        if (!compact.startsWith('+')) return;
        event.preventDefault();
        setInternationalEditing(false);
        const next = raw.length > 64 ? raw : compact;
        update(next, countryFor(compact), `+${compact.replace(/\D/g, '')}`);
      }}
      onBlur={() => {
        if (internationalEditing) {
          setInternationalEditing(false);
          update(editor.value, countryFor(editor.value));
          setFormatVersion(version => version + 1);
        }
      }}
      onChange={next => {
        const captured = override.current;
        override.current = null;
        if (captured !== null) return;
        update(next ?? '', editor.country);
      }} />
  </div>;
}
