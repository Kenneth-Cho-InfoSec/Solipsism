import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({ gfm: true, breaks: true });

export async function renderReleaseMarkdown(
  markdown: string | null,
): Promise<string> {
  const html = await marked.parse(
    markdown?.trim() || '_No release notes were provided._',
  );
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p',
      'a',
      'strong',
      'em',
      'code',
      'pre',
      'ul',
      'ol',
      'li',
      'h1',
      'h2',
      'h3',
      'h4',
      'blockquote',
      'hr',
      'br',
    ],
    ALLOWED_ATTR: ['href', 'title'],
    ALLOW_DATA_ATTR: false,
  });
}
