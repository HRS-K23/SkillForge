import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

/** Raw HTML in Markdown is not rendered (react-markdown default), so content is safe. */
export default function Markdown({ children }: { children: string }) {
  return (
    <div className="prose prose-slate max-w-none">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  );
}
