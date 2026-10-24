// Keep calendar dates as written by the API; parsing YYYY-MM-DD as UTC can
// shift the displayed day in some time zones.
export const formatDisplayDate = (value, { includeTime = false } = {}) => {
  if (!value) return '-';

  const raw = String(value);
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (dateOnly) {
    const [, year, month, day] = dateOnly;
    return `${month}/${day}/${year.slice(-2)}`;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return raw;

  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const year = String(date.getFullYear()).slice(-2);
  const formatted = `${month}/${day}/${year}`;

  return includeTime
    ? `${formatted} ${new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(date)}`
    : formatted;
};
