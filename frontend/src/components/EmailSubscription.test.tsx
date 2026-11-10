// @vitest-environment jsdom
import { StrictMode } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, expect, it, vi } from 'vitest';
import EmailSubscriptionForm from './EmailSubscriptionForm';
import EmailSubscriptionConfirm from './EmailSubscriptionConfirm';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  window.history.replaceState({}, '', '/');
});

it('отправляет подписку на backend и просит проверить почту', async () => {
  const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, status: 202 } as Response);
  render(<EmailSubscriptionForm />);

  fireEvent.change(screen.getByLabelText('Рейс для подписки'), { target: { value: 'su100' } });
  fireEvent.change(screen.getByLabelText('Дата вылета'), { target: { value: '2026-12-01' } });
  fireEvent.change(screen.getByLabelText('Электронная почта'), { target: { value: 'user@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Подписаться' }));

  await waitFor(() => expect(request).toHaveBeenCalledWith(
    '/api/v1/subscriptions/email',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ flightIata: 'SU100', flightDate: '2026-12-01', email: 'user@example.com' }),
    }),
  ));
  expect(await screen.findByText(/Заявка принята/)).toBeInTheDocument();
});

it('подтверждает подписку по токену только один раз даже в StrictMode', async () => {
  window.history.replaceState({}, '', '/subscriptions/confirm?token=test-token');
  const request = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, status: 204 } as Response);
  render(<StrictMode><EmailSubscriptionConfirm /></StrictMode>);

  expect(await screen.findByText(/Подписка на рейс активирована/)).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith(
    '/api/v1/subscriptions/email/confirm',
    expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'test-token' }) }),
  );
});
