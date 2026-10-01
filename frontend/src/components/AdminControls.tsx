import { deleteAdminItemAction, reorderAction } from "@/app/actions";
import ConfirmButton from "@/components/ConfirmButton";

export function MoveButtons({
  kind,
  parent,
  ids,
  id,
}: {
  kind: "modules" | "lessons" | "exercises";
  parent: string;
  ids: string[];
  id: string;
}) {
  const index = ids.indexOf(id);
  const btn =
    "rounded border border-slate-300 px-2 py-0.5 text-xs text-slate-700 hover:bg-slate-100 disabled:opacity-30";
  return (
    <form action={reorderAction} className="inline-flex gap-1">
      <input type="hidden" name="kind" value={kind} />
      <input type="hidden" name="parent" value={parent} />
      <input type="hidden" name="ids" value={ids.join(",")} />
      <input type="hidden" name="id" value={id} />
      <button name="dir" value="up" disabled={index <= 0} className={btn} aria-label="Move up">
        ↑
      </button>
      <button name="dir" value="down" disabled={index >= ids.length - 1} className={btn} aria-label="Move down">
        ↓
      </button>
    </form>
  );
}

export function DeleteButton({
  kind,
  id,
  message,
  label = "Delete",
}: {
  kind: "tool" | "module" | "lesson" | "exercise";
  id: string;
  message: string;
  label?: string;
}) {
  return (
    <form action={deleteAdminItemAction} className="inline">
      <input type="hidden" name="kind" value={kind} />
      <input type="hidden" name="id" value={id} />
      <ConfirmButton
        message={message}
        className="rounded border border-red-300 px-2 py-0.5 text-xs text-red-700 hover:bg-red-50"
      >
        {label}
      </ConfirmButton>
    </form>
  );
}