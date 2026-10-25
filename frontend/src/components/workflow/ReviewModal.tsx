import { useState } from 'react';
import { Star } from 'lucide-react';
import { useToast } from '../../hooks/useToast';
import { workflowApi } from '../../services/workflow.service';
import { Button } from '../ui/Button';

interface Props {
  agreementId: number;
  isOpen: boolean;
  onClose: () => void;
  onSubmitted: () => void;
}

export function ReviewModal({ agreementId, isOpen, onClose, onSubmitted }: Props) {
  const [rating, setRating] = useState<number>(5);
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  if (!isOpen) return null;

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await workflowApi.createReview(agreementId, rating, comment.trim() || undefined);
      toast.push({ message: 'Review submitted successfully!', tone: 'success' });
      onSubmitted();
      onClose();
    } catch (err) {
      toast.push({ message: err instanceof Error ? err.message : 'Failed to submit review', tone: 'error' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
      <div className="w-full max-w-md rounded-lg border border-line bg-paper p-6 shadow-xl">
        <h3 className="font-display text-lg font-semibold">Review Partner</h3>
        <p className="mt-1 text-sm text-mute">
          Rate your experience working on this agreement. Your feedback will be publicly visible on their profile.
        </p>

        {/* Star Rating Input */}
        <div className="my-5 flex items-center justify-center gap-2">
          {[1, 2, 3, 4, 5].map((star) => {
            const isFilled = (hoverRating !== null ? hoverRating : rating) >= star;
            return (
              <button
                key={star}
                type="button"
                onMouseEnter={() => setHoverRating(star)}
                onMouseLeave={() => setHoverRating(null)}
                onClick={() => setRating(star)}
                className="p-1 transition-transform hover:scale-110"
              >
                <Star
                  className={`size-8 ${
                    isFilled ? 'fill-amber-400 text-amber-400' : 'text-line hover:text-amber-200'
                  }`}
                />
              </button>
            );
          })}
        </div>

        {/* Feedback Textarea */}
        <div>
          <label className="text-xs font-medium text-mute">Comments (Optional)</label>
          <textarea
            rows={3}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Share feedback on communication, quality, and professionalism..."
            className="mt-1 w-full rounded-md border border-line bg-paper p-2.5 text-sm"
          />
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button variant="outline" size="sm" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button size="sm" onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Submitting...' : 'Submit Review'}
          </Button>
        </div>
      </div>
    </div>
  );
}
