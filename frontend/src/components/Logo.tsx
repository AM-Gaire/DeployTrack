export function Logo({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      {/* Decorative: the wordmark beside it already names the product, so
          announcing the icon too would just repeat it to a screen reader. */}
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-5 shrink-0 text-accent"
      >
        <path d="M4 13a8 8 0 0 1 7 7a6 6 0 0 0 3-5a9 9 0 0 0 6-8a3 3 0 0 0-3-3a9 9 0 0 0-8 6a6 6 0 0 0-5 3" />
        <path d="M7 14a6 6 0 0 0-3 6a6 6 0 0 0 6-3" />
        <circle cx="15" cy="9" r="1" />
      </svg>
      <span className="font-mono text-[13px] tracking-wide">DEPLOYTRACK</span>
    </div>
  )
}
