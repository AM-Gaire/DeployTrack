export function Logo({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <span aria-hidden="true" className="grid size-6 place-items-center rounded-[5px] bg-accent">
        <span className="size-2 rounded-[2px] bg-paper" />
      </span>
      <span className="font-mono text-[13px] tracking-wide">DEPLOYTRACK</span>
    </div>
  )
}
