"use client";

import { useActionState } from "react";
import type { FormState } from "@/app/actions";

type Field = {
  name: string;
  label: string;
  type?: string;
  autoComplete?: string;
  defaultValue?: string;
  disabled?: boolean;
  minLength?: number;
  maxLength?: number;
};

export default function AuthForm({
  action,
  fields,
  submitLabel,
}: {
  action: (state: FormState, data: FormData) => Promise<FormState>;
  fields: Field[];
  submitLabel: string;
}) {
  // React resets uncontrolled inputs after an action; echo back non-secret values.
  const [state, formAction, pending] = useActionState(
    async (prev: FormState, data: FormData): Promise<FormState> => {
      const res = await action(prev, data);
      const values: Record<string, string> = {};
      for (const f of fields) {
        if (f.type !== "password" && !f.disabled) values[f.name] = String(data.get(f.name) ?? "");
      }
      return { ...res, values };
    },
    {},
  );
  return (
    <form action={formAction} className="space-y-4">
      {fields.map((f) => (
        <label key={f.name} className="block text-sm font-medium">
          {f.label}
          <input
            name={f.name}
            type={f.type ?? "text"}
            autoComplete={f.autoComplete}
            defaultValue={state.values?.[f.name] ?? f.defaultValue}
            disabled={f.disabled}
            minLength={f.minLength}
            maxLength={f.maxLength}
            required={!f.disabled}
            className="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-slate-900 disabled:bg-slate-100"
          />
        </label>
      ))}
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
        {pending ? "Please wait…" : submitLabel}
      </button>
    </form>
  );
}
