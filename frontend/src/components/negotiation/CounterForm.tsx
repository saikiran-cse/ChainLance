import { useState, type FormEvent } from 'react';
import type { ProposedTerms } from '../../types/marketplace';
import { toAppError } from '../../utils/errors';
import { hasErrors, toMoney, validateCounter, type FieldErrors } from '../../utils/validation';
import { Button } from '../ui/Button';
import { TextArea, TextField } from '../ui/Field';

interface Props {
  /** Resolve when the backend accepted the message; reject with the error otherwise. */
  onSend(body: { message: string; terms?: ProposedTerms }): Promise<void>;
  submitLabel?: string;
  defaults?: { priceInr?: number; durationDays?: number };
}

/** Message plus an optional new price and duration. Sending terms is what makes a message a counteroffer. */
export function CounterForm({ onSend, submitLabel = 'Send counteroffer', defaults }: Props) {
  const [message, setMessage] = useState('');
  const [price, setPrice] = useState('');
  const [days, setDays] = useState('');
  const [errors, setErrors] = useState<FieldErrors<'message' | 'price' | 'durationDays'>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateCounter({ message, price, durationDays: days });
    setErrors(found);
    setFormError(null);
    if (hasErrors(found)) return;
    const terms: ProposedTerms = {};
    if (price.trim()) terms.priceInr = toMoney(price);
    if (days.trim()) terms.durationDays = Number(days);
    setBusy(true);
    try {
      await onSend({ message, terms: Object.keys(terms).length > 0 ? terms : undefined });
      setMessage('');
      setPrice('');
      setDays('');
    } catch (e) {
      const err = toAppError(e);
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <TextArea
        label="Message"
        rows={3}
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        error={errors.message}
        placeholder="Explain what you would change and why."
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="New price in INR"
          optional
          inputMode="decimal"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          error={errors.price}
          placeholder={defaults?.priceInr ? String(defaults.priceInr) : undefined}
        />
        <TextField
          label="New duration in days"
          optional
          inputMode="numeric"
          value={days}
          onChange={(e) => setDays(e.target.value)}
          error={errors.durationDays}
          placeholder={defaults?.durationDays ? String(defaults.durationDays) : undefined}
        />
      </div>
      {formError && (
        <p role="alert" className="rounded-md border border-brick px-4 py-3 text-sm font-medium text-brick">
          {formError}
        </p>
      )}
      <div>
        <Button type="submit" variant="primary" loading={busy}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
