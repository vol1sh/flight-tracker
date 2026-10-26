// @vitest-environment jsdom
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import '@testing-library/jest-dom/vitest';
import App from './App';

describe('Flight Tracker Frontend', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('SearchForm: отправляет номер рейса и дату в query-параметре', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/system/ping')) {
        return { ok: true, status: 200, json: async () => ({ status: 'UP' }) } as Response;
      }
      if (url.includes('/flights/active')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      if (url.includes('/history')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          flightIata: 'SU100',
          flightDate: '2026-10-14',
          status: 'SCHEDULED',
          delayMinutes: 0,
          departureAirport: 'SVO',
          arrivalAirport: 'JFK',
          scheduledDeparture: '2026-10-14T10:00:00Z',
          actualDeparture: null,
          scheduledArrival: '2026-10-14T19:00:00Z',
          actualArrival: null,
          lastUpdated: '2026-10-14T09:50:00Z',
          dataFresh: true,
          isDegraded: false,
        }),
      } as Response;
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText(/номер рейса/i), 'su100');
    await userEvent.type(screen.getByLabelText(/дата рейса/i), '2026-10-14');
    await userEvent.click(screen.getByRole('button', { name: /найти рейс/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/flights/SU100?date=2026-10-14');
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/flights/SU100/history?date=2026-10-14');
    });
  });

  it('FlightDetails: корректно отображает данные найденного рейса', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/system/ping')) {
        return { ok: true, status: 200, json: async () => ({ status: 'UP' }) } as Response;
      }
      if (url.includes('/flights/active') || url.includes('/history')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          flightIata: 'SU100',
          flightDate: '2026-10-14',
          status: 'DELAYED',
          delayMinutes: 35,
          departureAirport: 'SVO',
          arrivalAirport: 'JFK',
          scheduledDeparture: '2026-10-14T10:00:00Z',
          actualDeparture: '2026-10-14T10:35:00Z',
          scheduledArrival: '2026-10-14T19:00:00Z',
          actualArrival: null,
          lastUpdated: '2026-10-14T09:50:00Z',
          dataFresh: true,
          isDegraded: false,
        }),
      } as Response;
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText(/номер рейса/i), 'SU100');
    await userEvent.type(screen.getByLabelText(/дата рейса/i), '2026-10-14');
    await userEvent.click(screen.getByRole('button', { name: /найти рейс/i }));

    expect(await screen.findByText('SU100')).toBeInTheDocument();
    expect(screen.getByText(/Дата рейса: 2026-10-14/i)).toBeInTheDocument();
    expect(screen.getByText(/Данные актуальны/i)).toBeInTheDocument();
  });

  it('NotFound: отображает понятные сообщения для ошибок 404 и 503', async () => {
    let statusToReturn = 404;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/system/ping') || url.includes('/flights/active')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      return { ok: false, status: statusToReturn, json: async () => ({}) } as Response;
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText(/номер рейса/i), 'SU999');
    await userEvent.type(screen.getByLabelText(/дата рейса/i), '2026-10-14');
    await userEvent.click(screen.getByRole('button', { name: /найти рейс/i }));

    expect(await screen.findByText(/рейс на указанную дату не найден/i)).toBeInTheDocument();

    statusToReturn = 503;
    await userEvent.click(screen.getByRole('button', { name: /повторить поиск/i }));
    expect(await screen.findByText(/данные временно недоступны/i)).toBeInTheDocument();
  });

  it('DegradedMode: показывает предупреждение при устаревших данных (dataFresh=false)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/system/ping') || url.includes('/flights/active') || url.includes('/history')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          flightIata: 'SU100',
          flightDate: '2026-10-14',
          status: 'SCHEDULED',
          delayMinutes: 0,
          departureAirport: 'SVO',
          arrivalAirport: 'JFK',
          scheduledDeparture: '2026-10-14T10:00:00Z',
          actualDeparture: null,
          scheduledArrival: '2026-10-14T19:00:00Z',
          actualArrival: null,
          lastUpdated: '2026-10-14T08:00:00Z',
          dataFresh: false,
          isDegraded: true,
        }),
      } as Response;
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText(/номер рейса/i), 'SU100');
    await userEvent.type(screen.getByLabelText(/дата рейса/i), '2026-10-14');
    await userEvent.click(screen.getByRole('button', { name: /найти рейс/i }));

    expect(await screen.findByText(/отображаются ранее сохранённые данные/i)).toBeInTheDocument();
    expect(screen.getByText(/Устаревшие данные/i)).toBeInTheDocument();
  });

  it('FlightHistory: корректно отображает список переходов статуса', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('/system/ping') || url.includes('/flights/active')) {
        return { ok: true, status: 200, json: async () => [] } as Response;
      }
      if (url.includes('/history')) {
        return {
          ok: true,
          status: 200,
          json: async () => [
            {
              recordedAt: '2026-10-14T10:00:00Z',
              previousStatus: null,
              newStatus: 'SCHEDULED',
              delayMinutes: 0,
            },
            {
              recordedAt: '2026-10-14T11:00:00Z',
              previousStatus: 'SCHEDULED',
              newStatus: 'DELAYED',
              delayMinutes: 35,
            },
          ],
        } as Response;
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          flightIata: 'SU100',
          flightDate: '2026-10-14',
          status: 'DELAYED',
          delayMinutes: 35,
          departureAirport: 'SVO',
          arrivalAirport: 'JFK',
          scheduledDeparture: '2026-10-14T10:00:00Z',
          actualDeparture: null,
          scheduledArrival: '2026-10-14T19:00:00Z',
          actualArrival: null,
          lastUpdated: '2026-10-14T11:00:00Z',
          dataFresh: true,
          isDegraded: false,
        }),
      } as Response;
    });

    render(<App />);
    await userEvent.type(screen.getByLabelText(/номер рейса/i), 'SU100');
    await userEvent.type(screen.getByLabelText(/дата рейса/i), '2026-10-14');
    await userEvent.click(screen.getByRole('button', { name: /найти рейс/i }));

    expect(await screen.findByText(/2 записи/i)).toBeInTheDocument();
    expect(screen.getAllByText('Запланирован').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Задержан').length).toBeGreaterThan(0);
  });
});
