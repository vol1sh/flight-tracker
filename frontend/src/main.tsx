import React from 'react';
import ReactDOM from 'react-dom/client';
import AnalyticsEntry from './pages/AnalyticsEntry';
import EmailSubscriptionConfirm from './components/EmailSubscriptionConfirm';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    {window.location.pathname.replace(/\/$/, '') === '/subscriptions/confirm'
      ? <EmailSubscriptionConfirm />
      : <AnalyticsEntry />}
  </React.StrictMode>,
);
