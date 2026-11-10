import { useEffect, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, LoaderCircle, Plane } from 'lucide-react';

type ConfirmState = { status: 'loading' | 'success' | 'error'; message: string };

export default function EmailSubscriptionConfirm() {
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token'));
  const [state, setState] = useState<ConfirmState>({
    status: 'loading',
    message: 'Проверяем ссылку подтверждения…',
  });
  const requestStarted = useRef(false);

  useEffect(() => {
    if (requestStarted.current) return;
    requestStarted.current = true;

    if (!token) {
      setState({ status: 'error', message: 'В ссылке отсутствует токен подтверждения.' });
      return;
    }

    const confirm = async () => {
      try {
        const response = await fetch('/api/v1/subscriptions/email/confirm', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token }),
        });

        if (!response.ok) {
          throw new Error(
            response.status === 400 || response.status === 404 || response.status === 410
              ? 'Ссылка недействительна или срок её действия истёк. Оформите подписку заново.'
              : `Не удалось подтвердить подписку (HTTP ${response.status}).`,
          );
        }

        setState({
          status: 'success',
          message: 'Электронная почта подтверждена! Подписка на рейс активирована.',
        });
      } catch (error) {
        setState({
          status: 'error',
          message: error instanceof Error ? error.message : 'Ошибка соединения с сервером.',
        });
      }
    };

    void confirm();
  }, [token]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f7f9fc] px-4 py-10 text-slate-900">
      <section className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-xl shadow-blue-900/5">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-50 text-blue-600">
          {state.status === 'loading' ? <LoaderCircle className="h-7 w-7 animate-spin" aria-hidden="true" /> : state.status === 'success' ? <CheckCircle2 className="h-7 w-7 text-emerald-600" aria-hidden="true" /> : <AlertCircle className="h-7 w-7 text-red-600" aria-hidden="true" />}
        </div>
        <h1 className="text-2xl font-extrabold tracking-tight">
          {state.status === 'loading' ? 'Подтверждение подписки' : state.status === 'success' ? 'Подписка подтверждена' : 'Не удалось подтвердить'}
        </h1>
        <p role="status" aria-live="polite" className="mt-3 text-sm leading-relaxed text-slate-500">{state.message}</p>
        <a href="/" className="mt-7 inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-6 py-3 text-sm font-bold text-white transition hover:bg-blue-700">
          <Plane className="h-4 w-4" aria-hidden="true" />
          Вернуться к рейсам
        </a>
      </section>
    </main>
  );
}
