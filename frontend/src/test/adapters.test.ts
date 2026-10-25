import { describe, expect, it } from 'vitest';
import {
  adaptAgreement,
  adaptOffer,
  adaptPage,
  adaptProject,
  adaptProposal,
  unwrapList,
} from '../services/adapters';
import { weiToEth } from '../utils/format';

describe('Frontend Adapters', () => {
  describe('wei conversion and optWei validation', () => {
    it('accepts valid decimal wei string', () => {
      const agreement = adaptAgreement({
        id: 1,
        proposalId: 10,
        totalWei: '220000000000000000',
      });
      expect(agreement.totalWei).toBe('220000000000000000');
      expect(weiToEth(agreement.totalWei)).toBe('0.22');
    });

    it('strictly refuses non-decimal wei strings or numbers and returns null', () => {
      const withNumber = adaptAgreement({ id: 1, proposalId: 10, totalWei: 220000000000000000 });
      expect(withNumber.totalWei).toBeNull();
      expect(weiToEth(withNumber.totalWei)).toBe('—');

      const withAlpha = adaptAgreement({ id: 2, proposalId: 10, totalWei: '22eth' });
      expect(withAlpha.totalWei).toBeNull();
      expect(weiToEth(withAlpha.totalWei)).toBe('—');

      const withNegative = adaptAgreement({ id: 3, proposalId: 10, totalWei: '-1000' });
      expect(withNegative.totalWei).toBeNull();
      expect(weiToEth(withNegative.totalWei)).toBe('—');
    });
  });

  describe('snapshot JSON parsing in adaptAgreement', () => {
    it('accepts snapshot when provided as an object', () => {
      const raw = {
        id: 5,
        proposalId: 2,
        snapshot: { priceInr: 50000, durationDays: 14, clientName: 'Alice' },
        snapshotHash: '0x1234abcd',
        snapshotIntegrityOk: true,
      };
      const agreement = adaptAgreement(raw);
      expect(agreement.snapshot).toEqual({ priceInr: 50000, durationDays: 14, clientName: 'Alice' });
      expect(agreement.priceInr).toBe(50000);
      expect(agreement.snapshotHash).toBe('0x1234abcd');
      expect(agreement.snapshotIntegrityOk).toBe(true);
    });

    it('accepts and parses snapshot when provided as a JSON string', () => {
      const raw = {
        id: 6,
        proposalId: 3,
        snapshotJson: JSON.stringify({ priceInr: 75000, durationDays: 21, clientName: 'Bob' }),
        snapshotHash: '0x5678efgh',
        snapshotIntegrityOk: false,
      };
      const agreement = adaptAgreement(raw);
      expect(agreement.snapshot).toEqual({ priceInr: 75000, durationDays: 21, clientName: 'Bob' });
      expect(agreement.priceInr).toBe(75000);
      expect(agreement.snapshotIntegrityOk).toBe(false);
    });

    it('handles malformed JSON string without crashing', () => {
      const raw = {
        id: 7,
        proposalId: 4,
        snapshotJson: 'not valid json',
      };
      const agreement = adaptAgreement(raw);
      expect(agreement.snapshot).toBeNull();
    });
  });

  describe('list and page unwrapping', () => {
    it('unwraps bare arrays', () => {
      expect(unwrapList([1, 2, 3])).toEqual([1, 2, 3]);
    });

    it('unwraps Spring Page response envelope ({ content: [...] })', () => {
      const page = {
        content: [{ id: 1, title: 'Project 1' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      };
      expect(unwrapList(page)).toEqual([{ id: 1, title: 'Project 1' }]);
    });

    it('adapts page response properly', () => {
      const rawPage = {
        content: [{ id: 1, title: 'Project A' }, { id: 2, title: 'Project B' }],
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
      };
      const paged = adaptPage(rawPage, (item) => adaptProject(item));
      expect(paged.page).toBe(0);
      expect(paged.size).toBe(10);
      expect(paged.totalElements).toBe(2);
      expect(paged.totalPages).toBe(1);
      expect(paged.content).toHaveLength(2);
      expect(paged.content[0].title).toBe('Project A');
    });
  });

  describe('adaptOffer and adaptProposal', () => {
    it('adapts offer correctly', () => {
      const offer = adaptOffer({
        id: 11,
        proposalId: 2,
        offeredBy: 5,
        priceInr: 25000,
        durationDays: 10,
        note: 'Initial bid',
        status: 'PENDING',
        createdAt: '2026-10-25T12:00:00Z',
      });
      expect(offer.id).toBe(11);
      expect(offer.priceInr).toBe(25000);
      expect(offer.status).toBe('PENDING');
      expect(offer.note).toBe('Initial bid');
    });

    it('adapts proposal with embedded offers', () => {
      const proposal = adaptProposal({
        id: 2,
        projectId: 1,
        freelancer: { id: 5, displayName: 'Bob' },
        message: 'I can build this',
        priceInr: 25000,
        durationDays: 10,
        portfolioLinks: 'https://example.com/a\nhttps://example.com/b',
        status: 'SUBMITTED',
        createdAt: '2026-10-25T12:00:00Z',
        offers: [
          { id: 11, proposalId: 2, offeredBy: 5, priceInr: 25000, durationDays: 10, status: 'PENDING' },
        ],
      });
      expect(proposal.id).toBe(2);
      expect(proposal.portfolioLinks).toHaveLength(2);
      expect(proposal.offers).toHaveLength(1);
      expect(proposal.offers![0].id).toBe(11);
    });
  });
});
