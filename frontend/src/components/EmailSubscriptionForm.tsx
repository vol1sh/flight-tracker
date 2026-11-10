import { useState } from 'react';
import type { FormEvent } from 'react';
import { AlertCircle, BellRing, CalendarDays, CheckCircle2, LoaderCircle, Mail, Plane, Send } from 'lucide-react';

type Feedback = { type: 'success' | 'error'; message: string } | null;

export default function EmailSubscriptionForm() {
  const [flightIata, setFlightIata] = useState('');
  const [flightDate, setFlightDate] = useState('');
  const [email, setEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSubmitting) return;

    setFeedback(null);
    setIsSubmitting(true);

    try {
      const response = await fetch('/api/v1/subscriptions/email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          flightIata: flightIata.trim().toUpperCase().replace(/\s+/g, ''),
          flightDate,
          email: email.trim(),
        }),
      });

      if (!response.ok) {
        const errors: Record<number, string> = {
          400: 'Проверьте номер рейса, дату и адрес почты.',
          404: 'Рейс на выбранную дату не найден.',
          409: 'Подписка для этого адреса и рейса уже существует.',
          429: 'Слишком много запросов. Попробуйте позже.',
          503: 'Сервис подписок временно недоступен.',
        };
        throw new Error(errors[response.status] ?? `Не удалось оформить подписку (HTTP ${response.status}).`);
      }

      setFeedback({
        type: 'success',
        message: 'Заявка принята! Проверьте почту и перейдите по ссылке, чтобы подтвердить подписку.',
      });
      setFlightIata('');
      setFlightDate('');
    } catch (error) {
      setFeedback({
        type: 'error',
        message: error instanceof TypeError ? 'Не удалось подключиться к серверу.' : error instanceof Error ? error.message : 'Не удалось связаться с сервером.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const inputClass = 'w-full rounded-xl border border-slate-200 bg-white px-4 py-3 pl-11 text-sm font-medium text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100 disabled:opacity-60';

  return (
    <section id="email-subscription" aria-labelledby="email-subscription-title" className="px-4 pb-12 pt-5 sm:px-6">
      <div className="mx-auto max-w-5xl overflow-hidden rounded-3xl border border-blue-100 bg-white shadow-[0_12px_40px_rgba(37,99,235,0.07)]">
        <div className="h-1 bg-gradient-to-r from-sky-400 via-blue-600 to-indigo-500" />
        <div className="p-6 sm:p-8">
          <div className="mb-6 flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-blue-600">
              <BellRing className="h-6 w-6" aria-hidden="true" />
            </div>
            <div>
              <h2 id="email-subscription-title" className="text-xl font-extrabold tracking-tight text-slate-900 sm:text-2xl">
                Следите за рейсом без постоянных проверок
              </h2>
              <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-slate-500">
                Оставьте номер рейса, дату и почту. После подтверждения адреса вы сможете получать уведомления об изменениях рейса.
              </p>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="grid grid-cols-1 items-end gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1.4fr_auto]">
            <label className="block min-w-0">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wide text-slate-600">Рейс для подписки</span>
              <span className="relative block">
                <Plane className="pointer-events-none absolute left-3.5 top-3.5 h-4 w-4 text-blue-500" aria-hidden="true" />
                <input
                  className={inputClass}
                  type="text"
                  name="flightIata"
                  value={flightIata}
                  onChange={(event) => setFlightIata(event.target.value)}
                  placeholder="SU100"
                  autoComplete="off"
                  maxLength={12}
                  required
                />
              </span>
            </label>

            <label className="block min-w-0">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wide text-slate-600">Дата вылета</span>
              <span className="relative block">
                <CalendarDays className="pointer-events-none absolute left-3.5 top-3.5 h-4 w-4 text-blue-500" aria-hidden="true" />
                <input
                  className={inputClass}
                  type="date"
                  name="flightDate"
                  value={flightDate}
                  onChange={(event) => setFlightDate(event.target.value)}
                  required
                />
              </span>
            </label>

            <label className="block min-w-0 sm:col-span-2 lg:col-span-1">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wide text-slate-600">Электронная почта</span>
              <span className="relative block">
                <Mail className="pointer-events-none absolute left-3.5 top-3.5 h-4 w-4 text-blue-500" aria-hidden="true" />
                <input
                  className={inputClass}
                  type="email"
                  name="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@example.com"
                  autoComplete="email"
                  required
                />
              </span>
            </label>

            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex h-[46px] w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 text-sm font-bold text-white shadow-lg shadow-blue-600/15 transition hover:bg-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-wait disabled:opacity-60 sm:col-span-2 lg:col-span-1 lg:w-auto"
            >
              {isSubmitting ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Send className="h-4 w-4" aria-hidden="true" />}
              {isSubmitting ? 'Отправляем…' : 'Подписаться'}
            </button>
          </form>

          <div aria-live="polite" aria-atomic="true" className="mt-4">
            {feedback && (
              <p role={feedback.type === 'error' ? 'alert' : 'status'} className={`flex items-start gap-2 rounded-xl border px-4 py-3 text-sm ${feedback.type === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-red-200 bg-red-50 text-red-700'}`}>
                {feedback.type === 'success' ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /> : <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
                {feedback.message}
              </p>
            )}
          </div>
          <p className="mt-3 text-xs text-slate-400">Письма отправляет сервер Flight Tracker. Никаких паролей от почты вводить не нужно.</p>
        </div>
      </div>
    </section>
  );
}
