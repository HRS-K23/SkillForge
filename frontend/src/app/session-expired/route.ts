import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";
import { TOKEN_COOKIE } from "@/lib/api";

// Cookies can only be modified in route handlers / server actions, so the clean-up lives here.
export async function GET(request: NextRequest) {
  (await cookies()).delete(TOKEN_COOKIE);
  const url = request.nextUrl.clone();
  url.pathname = "/login";
  url.search = request.nextUrl.searchParams.get("reason") === "expired" ? "?expired=1" : "?ended=1";
  return NextResponse.redirect(url);
}