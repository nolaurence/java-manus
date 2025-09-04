import { useState, useEffect, useMemo } from 'react';
import { formatRelativeTime } from '@/utils/time';

export function useRelativeTime(timestamp: number) {
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setTick(prev => prev + 1);
    }, 60000);

    return () => clearInterval(interval);
  }, []);

  return useMemo(() => formatRelativeTime(timestamp), [timestamp, tick]);
}
