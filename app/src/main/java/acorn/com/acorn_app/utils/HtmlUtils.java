package acorn.com.acorn_app.utils;

import android.content.Context;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.util.regex.Pattern;

import acorn.com.acorn_app.R;

import static acorn.com.acorn_app.utils.IOUtils.getInputStream;

public class HtmlUtils {
    private static final String TAG = "HtmlUtils";

    private static final Whitelist JSOUP_WHITELIST = Whitelist.relaxed().preserveRelativeLinks(true)
            .addProtocols("img", "src", "http", "https")
            .addTags("iframe", "video", "audio", "source", "track", "img")
            .addAttributes("iframe", "src", "frameborder", "height", "width")
            .addAttributes("video", "src", "controls", "height", "width", "poster")
            .addAttributes("audio", "src", "controls")
            .addAttributes("source", "src", "type")
            .addAttributes("track", "src", "kind", "srclang", "label");

    private static final Pattern IMG_PATTERN = Pattern.compile("<img\\s+[^>]*src=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOSCRIPT_PATTERN = Pattern.compile("<noscript>.*?</noscript>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADS_PATTERN = Pattern.compile("<div class=['\"]mf-viral['\"]><table border=['\"]0['\"]>.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAZY_LOADING_PATTERN = Pattern.compile("\\s+src=[^>]+\\s+(original|data)[-]*(src=.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAZY_LOADING_PATTERN_2 = Pattern.compile("\\s+(original|data)[^>]*?(src=.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAZY_LOADING_PATTERN_3 = Pattern.compile("\\s+(src=['\"].*?['\"])([^>]+?)data-lazy-src=(['\"].*?['\"])", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAZY_LOADING_PATTERN_4 = Pattern.compile("\\s+data-original=", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_IMAGE_PATTERN = Pattern.compile("<img\\s+(height=['\"]1['\"]\\s+width=['\"]1['\"]|width=['\"]1['\"]\\s+height=['\"]1['\"])\\s+[^>]*src=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_IMAGE_PATTERN = Pattern.compile("\\s+(href|src)=([\"'])//", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_IMAGE_PATTERN_2 = Pattern.compile("\\s+(href|src)=([\"'])/", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALT_IMAGE_PATTERN = Pattern.compile("amp-img\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_IMAGE_PATTERN = Pattern.compile("<img\\s+[^>]*src=\\s*['\"]([^'\"]+)\\.img['\"][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_BR_PATTERN = Pattern.compile("^(\\s*<br\\s*[/]*>\\s*)*", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_BR_PATTERN = Pattern.compile("(\\s*<br\\s*[/]*>\\s*)*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTIPLE_BR_PATTERN = Pattern.compile("(\\s*<br\\s*[/]*>\\s*){3,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMPTY_LINK_PATTERN = Pattern.compile("<a\\s+[^>]*></a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_START_PATTERN = Pattern.compile("(<table)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_END_PATTERN = Pattern.compile("(</table>)", Pattern.CASE_INSENSITIVE);

    private static final String QUOTE_BACKGROUND_COLOR = "#e6e6e6";
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

    public static String improveHtmlContent(String content, String baseUrl) {
        if (content != null) {
            // remove some ads
            content = ADS_PATTERN.matcher(content).replaceAll("");
            // remove noscript blocks
            content = NOSCRIPT_PATTERN.matcher(content).replaceAll("");
            // remove lazy loading images stuff
            content = LAZY_LOADING_PATTERN.matcher(content).replaceAll(" $2");
            content = LAZY_LOADING_PATTERN_2.matcher(content).replaceAll(" $2");
            content = LAZY_LOADING_PATTERN_3.matcher(content).replaceAll(" $2src=$3");
            content = LAZY_LOADING_PATTERN_4.matcher(content).replaceAll("src=");
            // fix relative image paths
            content = RELATIVE_IMAGE_PATTERN.matcher(content).replaceAll(" $1=$2http://");
            // fix alternative image tags
            content = ALT_IMAGE_PATTERN.matcher(content).replaceAll("img ");

            // clean by JSoup
            content = Jsoup.clean(content, baseUrl, JSOUP_WHITELIST);

            content = RELATIVE_IMAGE_PATTERN_2.matcher(content).replaceAll(" $1=$2" + baseUrl);

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


        return content;
    }

    private static String generateHtmlContent(Context context, String title, String link,
                                              String contentText, String author, String source, String date) {
        String BACKGROUND_COLOR = String.format("#%06X", (0xFFFFFF & context.getResources().getColor(R.color.webview_background)));
        String TEXT_COLOR = String.format("#%06X", (0xFFFFFF & context.getResources().getColor(R.color.webview_text_color)));
        String SUBTITLE_COLOR = String.format("#%06X", (0xFFFFFF & context.getResources().getColor(R.color.webview_subtitle_color)));

        String CSS = "<head><style type='text/css'> "
                + "body {max-width: 100%; margin: 0.3cm; font-family: sans-serif-light; font-size: " + TEXT_SIZE + "; color: " + TEXT_COLOR + "; background-color:" + BACKGROUND_COLOR + "; line-height: 150%} "
                + "* {max-width: 100%} "
                + "h1, h2 {font-weight: normal; line-height: 130%} "
                + "h1 {font-size: 110%; margin-bottom: 0.1em} "
                + "h2 {font-size: 90%} "
                + "a {color: #0099CC} "
                + "h1 a {font-weight: 1000; color: inherit; text-decoration: none} "
                + "img {height: auto} "
                + "img.avatar {vertical-align: middle; width: 16px; height: 16px; border-radius: 50%;} "
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
                + ".button-section p {margin: 0.1cm 0 0.2cm 0} "
                + ".button-section p.marginfix {margin: 0.5cm 0 0.5cm 0} "
                + ".button-section input, .button-section a {font-family: roboto; font-size: 100%; color: #FFFFFF; background-color: " + BUTTON_COLOR + "; text-decoration: none; border: none; border-radius:0.2cm; padding: 0.3cm} "
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
        content.append(contentText).append(BODY_END);

        return content.toString();
    }

    public static String regenArticleHtml(Context context, String url, String title,
                                          String author, String source, String date) {
        Pattern baseUrlPattern = Pattern.compile("(https?://.*?/).*", Pattern.CASE_INSENSITIVE);
        String baseUrl = baseUrlPattern.matcher(url).replaceAll("$1");
        String parsedHtml;
        try {
            String extractedHtml = ArticleTextExtractor.extractContent(getInputStream(context, url));
            if (extractedHtml != null) {
                parsedHtml = improveHtmlContent(extractedHtml, baseUrl);
                return generateHtmlContent(context,title,url,parsedHtml,author,source,date);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
