"use client";

import { useActionState } from "react";
import type { FormState } from "@/app/actions";

export type AdminField = {
  name: string;
  label: string;
  type?: "text" | "number" | "url";
  multiline?: boolean;
  optional?: boolean;
  placeholder?: string;
  hint?: string;
  maxLength?: number;
  min?: number;
  pattern?: string;
};

const inputClass =
  "mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900";

export default function AdminForm({
  action,
  fields,
  hidden = {},
  submitLabel,
}: {
  action: (state: FormState, data: FormData) => Promise<FormState>;
  fields: AdminField[];
  hidden?: Record<string, string>;
  submitLabel: string;
}) {
  // Keep what was typed after an error; clear the form after a success.
  const [state, formAction, pending] = useActionState(
    async (prev: FormState, data: FormData): Promise<FormState> => {
      const res = await action(prev, data);
      const values: Record<string, string> = {};
      if (res.error) {
        for (const f of fields) values[f.name] = String(data.get(f.name) ?? "");
      }
      return { ...res, values };
    },
    {},
  );

  return (
    <form action={formAction} className="space-y-3">
      {Object.entries(hidden).map(([k, v]) => (
        <input key={k} type="hidden" name={k} value={v} />
      ))}
      {fields.map((f) => {
        const common = {
          name: f.name,
          required: !f.optional,
          maxLength: f.maxLength,
          placeholder: f.placeholder,
          defaultValue: state.values?.[f.name] ?? "",
          className: inputClass,
        };
        return (
          <label key={f.name} className="block text-sm font-medium">
            {f.label}
            {f.optional && <span className="font-normal text-slate-500"> (optional)</span>}
            {f.multiline ? (
              <textarea {...common} rows={8} className={`${inputClass} font-mono`} />
            ) : (
              <input {...common} type={f.type ?? "text"} min={f.min} pattern={f.pattern} />
            )}
            {f.hint && <span className="mt-1 block text-xs font-normal text-slate-500">{f.hint}</span>}
          </label>
        );
      })}
      {state.error && (
        <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {state.error}
        </p>
      )}
      {state.success && (
        <p role="status" className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">
          {state.success}
        </p>
      )}
      <button
        type="submit"
        disabled={pending}
        className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
      >
        {pending ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}