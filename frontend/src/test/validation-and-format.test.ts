import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatInr,
  humanizeEnum,
  initials,
  shortAddress,
  timeAgo,
  weiToEth,
} from '../utils/format';
import { isHttpUrl, parseSkills, splitList } from '../utils/validation';

describe('Format and Validation Utilities', () => {
  describe('weiToEth', () => {
    it('converts exact 1 ETH in wei', () => {
      expect(weiToEth('1000000000000000000')).toBe('1');
    });

    it('converts 0.22 ETH in wei', () => {
      expect(weiToEth('220000000000000000')).toBe('0.22');
    });

    it('converts fractional wei without precision loss', () => {
      expect(weiToEth('1500000000000000000')).toBe('1.5');
    });

    it('returns dash on invalid or missing wei', () => {
      expect(weiToEth(null)).toBe('—');
      expect(weiToEth(undefined)).toBe('—');
      expect(weiToEth('')).toBe('—');
      expect(weiToEth('abc')).toBe('—');
      expect(weiToEth('-1')).toBe('—');
    });
  });

  describe('formatInr', () => {
    it('formats whole amounts with rupee symbol', () => {
      const res = formatInr(50000);
      expect(res).toContain('50,000');
    });

    it('returns dash on null or undefined', () => {
      expect(formatInr(null)).toBe('—');
      expect(formatInr(undefined)).toBe('—');
    });
  });

  describe('formatDate and timeAgo', () => {
    it('formats ISO dates', () => {
      expect(formatDate('2026-10-25T12:00:00Z')).toBeTruthy();
      expect(formatDate(null)).toBe('—');
    });

    it('formats relative time ago', () => {
      const now = Date.now();
      expect(timeAgo(new Date(now - 60000).toISOString(), now)).toBe('1 minute ago');
      expect(timeAgo(null)).toBe('—');
    });
  });

  describe('shortAddress', () => {
    it('shortens 0x addresses', () => {
      expect(shortAddress('0x1234567890abcdef1234567890abcdef12345678')).toBe('0x1234…5678');
    });

    it('returns dash on empty', () => {
      expect(shortAddress(null)).toBe('—');
    });
  });

  describe('humanizeEnum', () => {
    it('converts screaming snake case to capitalized words', () => {
      expect(humanizeEnum('IN_PROGRESS')).toBe('In progress');
      expect(humanizeEnum('DISPUTE_RESOLVED')).toBe('Dispute resolved');
    });
  });

  describe('initials', () => {
    it('extracts initials from full names', () => {
      expect(initials('John Doe')).toBe('JD');
      expect(initials('Alice')).toBe('A');
      expect(initials('')).toBe('?');
    });
  });

  describe('isHttpUrl', () => {
    it('validates http and https urls only', () => {
      expect(isHttpUrl('https://github.com/profile')).toBe(true);
      expect(isHttpUrl('http://example.com')).toBe(true);
      expect(isHttpUrl('javascript:alert(1)')).toBe(false);
      expect(isHttpUrl('ftp://server.local')).toBe(false);
      expect(isHttpUrl('not a url')).toBe(false);
    });
  });

  describe('parseSkills and splitList', () => {
    it('splits comma and newline separated lists', () => {
      expect(parseSkills('React, TypeScript, Solidity')).toEqual(['React', 'TypeScript', 'Solidity']);
      expect(splitList('https://link1.com\nhttps://link2.com')).toEqual(['https://link1.com', 'https://link2.com']);
    });
  });
});
