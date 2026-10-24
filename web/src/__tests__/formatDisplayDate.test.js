import { formatDisplayDate } from '../shared/utils/formatDisplayDate';

test('formats API calendar dates without shifting the calendar day', () => {
  expect(formatDisplayDate('2026-10-24')).toBe('10/24/26');
  expect(formatDisplayDate('2026-01-05')).toBe('01/05/26');
  expect(formatDisplayDate(null)).toBe('-');
});
