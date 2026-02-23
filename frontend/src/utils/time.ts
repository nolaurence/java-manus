/**
 * Time related utility functions
 */

/**
 * Convert timestamp to relative time (e.g., minutes ago, hours ago, days ago)
 * @param timestamp Timestamp (seconds)
 * @returns Formatted relative time string
 */
export const formatRelativeTime = (timestamp: number): string => {
  // 这里实现相对时间的逻辑
  const now = new Date();
  const date = new Date(timestamp);
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return 'Just now';
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  return `${Math.floor(diffInSeconds / 86400)}d ago`;
};
