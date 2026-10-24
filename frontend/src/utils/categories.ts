/**
 * Categories offered in the UI. The backend stores `category` as free text (VARCHAR 80) and has no category list
 * endpoint yet, so this list is a frontend convenience, not a source of truth.
 */
export const CATEGORIES = [
  'Web development',
  'Mobile apps',
  'Design',
  'Writing and editing',
  'Data and analytics',
  'Smart contracts',
  'Marketing',
  'Video and audio',
] as const;
