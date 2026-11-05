import type { APIRoute } from 'astro';

/**
 * AI crawlers and dataset harvesters.
 *
 * Search engines stay allowed: Googlebot, Bingbot and friends index the site
 * for people. These agents exist to train models or resell corpora, so they are
 * refused explicitly. A crawler that ignores robots.txt is not stopped by this
 * file; it is a clear statement of intent, not a firewall.
 */
const AI_CRAWLERS = [
  'GPTBot',
  'ChatGPT-User',
  'OAI-SearchBot',
  'ClaudeBot',
  'Claude-Web',
  'anthropic-ai',
  'Claude-SearchBot',
  'CCBot',
  'Google-Extended',
  'Applebot-Extended',
  'PerplexityBot',
  'Perplexity-User',
  'Bytespider',
  'Amazonbot',
  'meta-externalagent',
  'meta-externalfetcher',
  'FacebookBot',
  'cohere-ai',
  'cohere-embed',
  'YouBot',
  'Diffbot',
  'ImagesiftBot',
  'Omgilibot',
  'Timpibot',
  'VelenPublicWebCrawler',
  'PanguBot',
  'Kangaroo Bot',
  'Scrapy',
  'magpie-crawler',
  'AI2Bot',
  'Ai2Bot-Dolma',
  'DuckAssistBot',
  'MistralAI-User',
  'GrokBot',
  'DeepSeekBot',
  'ICC-Crawler',
  'Sogou',
  'SemrushBot-OCOB',
  'DataForSeoBot',
  'Brightbot',
  'iaskspider',
];

export const GET: APIRoute = ({ site }) => {
  const sitemap = new URL('sitemap.xml', site).toString();
  const groups = AI_CRAWLERS.map(
    (agent) => `User-agent: ${agent}\nDisallow: /\n`,
  ).join('\n');

  const body = `# Solipsism Browser robots.txt
# Search engines are welcome. AI training and dataset crawlers are not.
# https://github.com/Kenneth-Cho-InfoSec/Solipsism

User-agent: *
Allow: /

${groups}
Sitemap: ${sitemap}
`;

  return new Response(body, {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
