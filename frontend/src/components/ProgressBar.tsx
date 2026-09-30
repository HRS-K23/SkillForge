export default function ProgressBar({
  percent,
  label,
}: {
  percent: number;
  label?: string;
}) {
  return (
    <div>
      {label && (
        <div className="mb-1 flex justify-between text-xs text-slate-600">
          <span>{label}</span>
          <span>{percent}%</span>
        </div>
      )}
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
        role="progressbar"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div className="h-full bg-indigo-600" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
