import { useContext } from 'react';
import { ToastContext, type ToastContextValue } from '../context/ToastContext';

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext);
  if (!value) throw new Error('useToast must be used inside <ToastProvider>.');
  return value;
}
