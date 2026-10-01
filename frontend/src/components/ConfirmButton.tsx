"use client";

import type { ButtonHTMLAttributes } from "react";

/** Submit button that asks for confirmation before the surrounding form is submitted. */
export default function ConfirmButton({
  message,
  ...props
}: { message: string } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="submit"
      {...props}
      onClick={(e) => {
        if (!window.confirm(message)) e.preventDefault();
      }}
    />
  );
}