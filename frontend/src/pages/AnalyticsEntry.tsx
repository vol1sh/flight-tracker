import App from '../App';
import AnalyticsPage from './AnalyticsPage';

export default function AnalyticsEntry() {
  return window.location.pathname.replace(/\/$/, '') === '/analytics' ? <AnalyticsPage /> : <App />;
}
