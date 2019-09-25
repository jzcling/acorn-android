package acorn.com.acorn_app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;

import static acorn.com.acorn_app.utils.IOUtils.getInputStream;

public class HtmlUtils {
    private static final String TAG = "HtmlUtils";

    private static final Whitelist JSOUP_WHITELIST = Whitelist.relaxed().preserveRelativeLinks(true)
//            .addProtocols("img", "src", "http", "https")
            .addTags("iframe", "video", "audio", "source", "track", "img", "span", "figcaption", "script", "blockquote")
            .addAttributes("iframe", "src", "frameborder", "height", "width", "allowfullscreen", "allow")
            .addAttributes("video", "src", "controls", "height", "width", "poster")
            .addAttributes("audio", "src", "controls")
            .addAttributes("source", "src", "type")
            .addAttributes("track", "src", "kind", "srclang", "label")
            .addAttributes("img", "src", "alt", "srcset", "class")
            .addAttributes("span", "style")
            .addAttributes("script", "async", "src", "charset")
            .addAttributes("blockquote", "class", "data-instgrm-permalink", "data-instgrm-version")
            .addAttributes("dt", "class")
            .addAttributes("dd", "class");

    private static final Pattern IMG_PATTERN = Pattern.compile("(<img)[^>]*\\ssrc=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAZY_LOADING_PATTERN = Pattern.compile("(<img)[^>]*\\s(data-lazy-src|original-src|data-src|original[^>\\s]*?src|data[^>\\s]*?src|data-original)=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEEDLY_IMG_DESC_PATTERN = Pattern.compile("data-image-description=['\"][^'\"]*['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CNA_IMAGE_PATTERN = Pattern.compile("<source[^>]*srcset=['\"]([^'\"]+)['\"]>.*?<img\\s[^>]+src=['\"]([^'\"]+)['\"][^>]+/>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IFRAME_PATTERN = Pattern.compile("(<iframe [^>]*(?!(width|height)))>(</iframe>)", Pattern.CASE_INSENSITIVE);
	private static final Pattern NOSCRIPT_PATTERN = Pattern.compile("<noscript>.*?</noscript>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADS_PATTERN = Pattern.compile("<div class=['\"]mf-viral['\"]><table border=['\"]0['\"]>.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_IMAGE_PATTERN = Pattern.compile("<img\\s+(height=['\"]1['\"]\\s+width=['\"]1['\"]|width=['\"]1['\"]\\s+height=['\"]1['\"])\\s+[^>]*src=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_IMAGE_PATTERN = Pattern.compile("\\s+(href|src)=\\s*?[\"']//([^'\">\\s]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_IMAGE_PATTERN_2 = Pattern.compile("\\s+(href|src)=\\s*?[\"'](/[^/][^'\">\\s]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_IMAGE_PATTERN = Pattern.compile("amp-img\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_IMAGE_PATTERN = Pattern.compile("<img\\s+[^>]*src=\\s*['\"]([^'\"]+)\\.img['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_BR_PATTERN = Pattern.compile("^(\\s*<br\\s*[/]*>\\s*)*", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_BR_PATTERN = Pattern.compile("(\\s*<br\\s*[/]*>\\s*)*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPLE_BR_PATTERN = Pattern.compile("(\\s*<br\\s*[/]*>\\s*){3,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_LINK_PATTERN = Pattern.compile("<a\\s+[^>]*></a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_START_PATTERN = Pattern.compile("(<table)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_END_PATTERN = Pattern.compile("(</table>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_EMBED_PATTERN = Pattern.compile("<blockquote [^>]*?class=['\"][^>'\"]*?instagram[^>'\"]*?['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWITTER_EMBED_PATTERN = Pattern.compile("<blockquote [^>]*?class=['\"][^>'\"]*?twitter[^>'\"]*?['\"]", Pattern.CASE_INSENSITIVE);

    private static final String QUOTE_LEFT_COLOR = "#a6a6a6";
    private static final String QUOTE_TEXT_COLOR = "#565656";
    private static final String TEXT_SIZE = "125%";
    private static final String BUTTON_COLOR = "#52A7DF";
    private static final String SUBTITLE_BORDER_COLOR = "solid #ddd";

    private static final String BODY_START = "<body>";
    private static final String BODY_END = "</body>";
    private static final String TITLE_START = "<h1><a href='";
    private static final String TITLE_MIDDLE = "'>";
    private static final String TITLE_END = "</a></h1>";
    private static final String SUBTITLE_START = "<p class='subtitle'>";
    private static final String SUBTITLE_END = "</p>";

    private static final String INSTAGRAM_SCRIPT = "<script async src=\"https://www.instagram.com/embed.js\"></script>";
    private static final String TWITTER_SCRIPT = "<script async src=\"https://platform.twitter.com/widgets.js\" charset=\"utf-8\"></script>";


    public static String improveHtmlContent(String content, String baseUrl, String aid, String link, int width) {
        if (content != null) {

            // remove some ads
            content = ADS_PATTERN.matcher(content).replaceAll("");
            // remove noscript blocks
            content = NOSCRIPT_PATTERN.matcher(content).replaceAll("");

            // remove interfering elements
            content = SEEDLY_IMG_DESC_PATTERN.matcher(content).replaceAll("");
//            content = CNA_IMAGE_PATTERN.matcher(content).replaceAll("<img src=\"$2\" srcset=$1>");
            // remove lazy loading images stuff
            content = LAZY_LOADING_PATTERN.matcher(content).replaceAll("$1 src=\"$3\"");
            // fix relative image paths
            content = RELATIVE_IMAGE_PATTERN.matcher(content).replaceAll(" $1=\"http://$2\"");
            // fix alternative image tags
            content = ALT_IMAGE_PATTERN.matcher(content).replaceAll("img ");

            // clean by JSoup
            content = Jsoup.clean(content, baseUrl, JSOUP_WHITELIST);

            content = RELATIVE_IMAGE_PATTERN_2.matcher(content).replaceAll(" $1=\"" + baseUrl + "$2\"");

            //replace all images with appropriate image from srcset or api derived images
            String encodedLink = null;
            try {
                encodedLink = URLEncoder.encode(link, "UTF-8");
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
            }

            StringBuffer buffer = new StringBuffer();
            Matcher matcher = IMG_PATTERN.matcher(content);
            while(matcher.find()) {
//                Log.d(TAG, matcher.group(0));
                Pattern srcsetPattern = Pattern.compile("<img[^>]*srcset=\\s*['\"]([^>'\"]*)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
                Matcher srcMatcher = srcsetPattern.matcher(matcher.group(0));
                boolean hasSrc = false;
                while (srcMatcher.find()) {
//                    Log.d(TAG, srcMatcher.group(0));
                    String[] srcset = srcMatcher.group(1).split("[xw],");
                    int smallestAboveWidth = 10000;
                    String srcUrl = "";
                    for (String s : srcset) {
                        String[] sSplit = s.trim().split(" ");
                        if (sSplit.length > 1) {
                            int diff = (Integer.parseInt(sSplit[1].split("[xw]")[0])) - width;
                            if (diff > 0 && diff < smallestAboveWidth) {
                                smallestAboveWidth = diff;
                                srcUrl = s.trim().split(" ")[0];
                                srcUrl = srcUrl.replaceAll("amp;", "")
                                        .replaceAll("#038;", "");
                                hasSrc = true;
                            }
                        }
                    }
                    if (hasSrc) {
                        String replacement = "<img src=\"" + srcUrl + "\">";
//                        Log.d(TAG, "replacement: " + replacement);
                        matcher.appendReplacement(buffer, replacement);
                    }
                }
                if (!hasSrc) {
                    String encodedImageUrl = null;
                    try {
                        String img = matcher.group(2).replaceAll("amp;", "")
                                .replaceAll("#038;", "");
                        encodedImageUrl = URLEncoder.encode(img, "UTF-8");
                    } catch (Exception e) {
                        Log.d(TAG, e.getLocalizedMessage());
                    }
                    if (encodedImageUrl != null && encodedLink != null) {
                        String replacement = "<img src=\"https://acorncommunity.sg/api/v1/resizeImage?url=" + encodedImageUrl + "&aid=" + aid + "&link=" + encodedLink + "\">";
//                        Log.d(TAG, "replacement: " + replacement);
                        matcher.appendReplacement(buffer, replacement);
                    }
                }
            }
            matcher.appendTail(buffer);
            content = buffer.toString();
//            content = IMG_PATTERN.matcher(content).replaceAll("<a href=\"https://acorncommunity.sg/api/v1/resizeImage?url=$2&aid=" + aid + "&link=" + link + "\">$1\"https://acorncommunity.sg/api/v1/resizeImage?url=$2&aid=" + aid + "&link=" + link + "\">");

			content = IFRAME_PATTERN.matcher(content).replaceAll("<div class=\"default-iframe-container\">$1 class=\"default-iframe\">$3</div>");

            // remove empty or bad images
            content = EMPTY_IMAGE_PATTERN.matcher(content).replaceAll("");
            content = BAD_IMAGE_PATTERN.matcher(content).replaceAll("");
            // remove empty links
            content = EMPTY_LINK_PATTERN.matcher(content).replaceAll("");
            // remove trailing BR & too much BR
            content = START_BR_PATTERN.matcher(content).replaceAll("");
            content = END_BR_PATTERN.matcher(content).replaceAll("");
            content = MULTIPLE_BR_PATTERN.matcher(content).replaceAll("<br><br>");
            // add container to tables
            content = TABLE_START_PATTERN.matcher(content).replaceAll("<div class=\"container\">$1");
            content = TABLE_END_PATTERN.matcher(content).replaceAll("$1</div>");
        }

        // remove empty tags
        if (content != null) {
            Document doc = Jsoup.parse(content);
            List<String> tags = new ArrayList<>();
            tags.add("p");
            tags.add("a");
            tags.add("ul");
            tags.add("ol");
            tags.add("li");
            tags.add("span");
            tags.add("table");
            tags.add("caption");
            tags.add("div");
            boolean emptyElementsFullyRemoved = false;
            while (!emptyElementsFullyRemoved) {
                emptyElementsFullyRemoved = true;
                for (Element element : doc.select("*")) {
                    if (tags.contains(element.tagName()) && !element.hasText()
                            && element.html().trim().length() == 0) {
                        element.remove();
                        emptyElementsFullyRemoved = false;
                    }
                }
            }
            content = doc.toString();

//            Log.d(TAG, "pre-generate content: " + content);
            return content;
        }

        return null;
    }

    public static String generateHtmlContent(Context context, String title, String link, String contentText,
                                             String author, String source, String date) {
        String BACKGROUND_COLOR = String.format("#%06X", (0xFFFFFF & context.getColor(R.color.webview_background)));
        String TEXT_COLOR = String.format("#%06X", (0xFFFFFF & context.getColor(R.color.webview_text_color)));
        String SUBTITLE_COLOR = String.format("#%06X", (0xFFFFFF & context.getColor(R.color.webview_subtitle_color)));
        String QUOTE_BACKGROUND_COLOR = String.format("#%06X", (0xFFFFFF & context.getColor(R.color.webview_quote_background_color)));

        String CSS = "<head><style type='text/css'> "
                + "body {max-width: 100%; margin: 0.3cm; font-family: sans-serif-light; font-size: " + TEXT_SIZE + "; text-align: left; color: " + TEXT_COLOR + "; background-color:" + BACKGROUND_COLOR + "; line-height: 150%} "
                + "* {max-width: 100%} "
                + "h1, h2 {line-height: 130%} "
                + "h1 {font-size: 110%; font-weight: 700; margin-bottom: 0.1em} "
                + "h2 {font-size: 110%; font-weight: 500} "
                + "h3 {font-size: 100%; } "
                + "a {color: #0099CC} "
                + "h1 a {font-weight: bold; color: inherit; text-decoration: none} "
                + "img {height: auto} "
                + "img.avatar {vertical-align: middle; width: 16px; height: 16px; border-radius: 50%;} "
                + "figcaption {font-size: 80%} "
                //+ "pre {white-space: pre-wrap;} "
                + "blockquote {border-left: thick solid " + QUOTE_LEFT_COLOR + "; background-color:" + QUOTE_BACKGROUND_COLOR + "; margin: 0.5em 0 0.5em 0em; padding: 0.5em} "
                + "blockquote p {color: " + QUOTE_TEXT_COLOR + "} "
                + "p {margin: 0.8em 0 0.8em 0} "
                + "p.subtitle {font-size: 80%; color: " + SUBTITLE_COLOR + "; border-top:1px " + SUBTITLE_BORDER_COLOR + "; border-bottom:1px " + SUBTITLE_BORDER_COLOR + "; padding-top:2px; padding-bottom:2px; font-weight:800 } "
                + "ul, ol {margin: 0 0 0.8em 0.6em; padding: 0 0 0 1em} "
                + "ul li, ol li {margin: 0 0 0.8em 0; padding: 0} "
                + "div.button-section {padding: 0.4cm 0; margin: 0; text-align: center} "
                + "div.container {width: 100%; overflow: auto; white-space: nowrap;} "
                + "table, th, td {border-collapse: collapse; border: 1px solid darkgray; font-size: 90%} "
                + "th, td {padding: .2em 0.5em;} "
                + "iframe {width: 100%; border: 0} "
				+ ".default-iframe-container {position: relative; width: 100%; height: 0; padding-top: 56.25%} "
				+ ".default-iframe {position: absolute; top: 0; left: 0; bottom: 0; right: 0; width: 100%; height: 100%;} "
                + ".button-section p {margin: 0.1cm 0 0.2cm 0} "
                + ".button-section p.marginfix {margin: 0.5cm 0 0.5cm 0} "
                + ".button-section input, .button-section a {font-family: roboto; font-size: 100%; color: #FFFFFF; background-color: " + BUTTON_COLOR + "; text-decoration: none; border: none; border-radius:0.2cm; padding: 0.3cm} "
                // for business insider images
                + ".image-caption-label, .image-source-label {display:none;} "
                + ".image-caption-value, .image-source-value {display:inline; margin-left: 0;} "
                + ".image-caption-value {font-weight: 700; font-size: 80%; color: #111516;} "
                + ".image-source-value {color: #848f91; font-size: 80%;} "
                + "</style><meta name='viewport' content='width=device-width'/></head>";

        StringBuilder content = new StringBuilder(CSS).append(BODY_START);

        if (link == null) {
            link = "";
        }
        content.append(TITLE_START).append(link).append(TITLE_MIDDLE).append(title).append(TITLE_END);
        boolean hasSubtitle = false;
        if (date != null && !date.isEmpty()) {
            content.append(SUBTITLE_START).append(date);
            hasSubtitle = true;
        }
        if (author != null && !author.isEmpty()) {
            if (!hasSubtitle) {
                content.append(author);
            } else {
                content.append(" · ").append(author);
            }
            hasSubtitle = true;
        }
        if (source != null && !source.isEmpty() && !source.equals(author)) {
            if (!hasSubtitle) {
                content.append(source);
            } else {
                content.append(" · ").append(source);
            }
            hasSubtitle = true;
        }
        if (hasSubtitle) {
            content.append(SUBTITLE_END);
        }
        content.append(contentText);
        Log.d(TAG, "content: " + contentText);
        if (INSTAGRAM_EMBED_PATTERN.matcher(contentText).find()) {
            Log.d(TAG, "instagram embed");
            content.append(INSTAGRAM_SCRIPT);
        }
        if (TWITTER_EMBED_PATTERN.matcher(contentText).find()) {
            Log.d(TAG, "twitter embed");
            content.append(TWITTER_SCRIPT);
        }
        content.append(BODY_END);

        return content.toString();
    }

    @Nullable
    public static String regenArticleHtml(Context context, String url, String title, String author,
                                          String source, String date, @Nullable String htmlContent,
                                          @Nullable String selector, String aid, int width) {
        Log.d(TAG, "regenArticleHtml");
        Pattern baseUrlPattern = Pattern.compile("(https?://[^/]+?/).*", Pattern.CASE_INSENSITIVE);
        String baseUrl = baseUrlPattern.matcher(url).replaceAll("$1");
        String cleanHtml;
        String improvedHtml;

        if (htmlContent != null) {
            cleanHtml = cleanHtmlContent(htmlContent, url, selector, aid, width);
        } else {
            cleanHtml = getCleanedHtml(context, url, selector, aid, width);
        }

        if (cleanHtml != null && !cleanHtml.equals("")) {
            improvedHtml = improveHtmlContent(cleanHtml, baseUrl, aid, url, width);
            return generateHtmlContent(context, title, url, improvedHtml, author, source, date);
        }
        return null;
    }

    private static String getCleanedHtml(Context context, String url, @Nullable String selector, String aid, int width) {
        if (url != null) {
            Pattern baseUrlPattern = Pattern.compile("(https?://.*?/).*", Pattern.CASE_INSENSITIVE);
            String baseUrl = baseUrlPattern.matcher(url).replaceAll("$1");
            String parsedHtml;
            try {
                String extractedHtml = ArticleTextExtractor.extractContent(getInputStream(context, url), selector, baseUrl);
                if (extractedHtml != null && !extractedHtml.equals("")) {
                    parsedHtml = improveHtmlContent(extractedHtml, baseUrl, aid, url, width);
                    return parsedHtml;
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static String cleanHtmlContent(String html, String url, @Nullable String selector, String aid, int width) {
        if (url != null) {
            Pattern baseUrlPattern = Pattern.compile("(https?://.*?/).*", Pattern.CASE_INSENSITIVE);
            String baseUrl = baseUrlPattern.matcher(url).replaceAll("$1");
            String parsedHtml;
            try {
                String extractedHtml = ArticleTextExtractor.extractContent(html, selector, baseUrl);
                if (!extractedHtml.equals("")) {
                    parsedHtml = improveHtmlContent(extractedHtml, baseUrl, aid, url, width);
                    return parsedHtml;
                } else {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
