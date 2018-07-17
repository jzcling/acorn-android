package acorn.com.acorn_app.utils;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class is thread safe.
 *
 * @author Alex P (ifesdjeen from jreadability)
 * @author Peter Karich
 */
public class ArticleTextExtractor {
    private static final String TAG = "TextExtractor";

    // Interesting nodes
    private static final Pattern NODES = Pattern.compile("p|div|td|h1|h2|article|section");

    // Unlikely candidates
    private static final Pattern UNLIKELY = Pattern.compile("com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|"
            + "header|menu|re(mark|ply)|rss|sh(are|outbox)|social|twitter|facebook|sponsor"
            + "a(d|ll|gegate|rchive|ttachment)|(pag(er|ination))|popup|print|"
            + "login|si(debar|gn|ngle)|hinweis|expla(in|nation)?|metablock");

    // Most likely positive candidates
    private static final Pattern POSITIVE = Pattern.compile("(^(body|content|h?entry|main|page|post|text|blog|story|haupt))"
            + "|arti(cle|kel)|instapaper_body");

    // Very most likely positive candidates, used by Joomla CMS
    private static final Pattern ITSJOOMLA = Pattern.compile("articleBody");

    // Most likely negative candidates
    private static final Pattern NEGATIVE = Pattern.compile("nav($|igation)|user|com(ment|bx)|(^com-)|contact|"
            + "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|"
            + "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard|footer");

    private static final Pattern NEGATIVE_STYLE =
            Pattern.compile("hidden|display: ?none|font-size: ?small");

    /**
     * @param input            extracts article text from given html string. wasn't tested
     *                         with improper HTML, although jSoup should be able to handle minor stuff.
     * @return extracted article, all HTML tags stripped
     */
    public static String extractContent(String input) {
        return extractContent(Jsoup.parse(input));
    }

    public static String extractContent(Document doc) {
        if (doc == null)
            throw new NullPointerException("missing document");

        // now remove the clutter
        prepareDocument(doc);
//        Log.d(TAG, doc.select("body").toString());

        // init elements
        Collection<Element> nodes = getNodes(doc);
        int maxWeight = 0;
        Element bestMatchElement = null;

        for (Element entry : nodes) {
            int currentWeight = getWeight(entry);
            if (currentWeight > 0)
                Log.d(TAG, "entry: " + entry.toString().substring(0, Math.min(entry.toString().length(), 50)) + ", weight: " + currentWeight);
            if (currentWeight > maxWeight) {
                maxWeight = currentWeight;
                bestMatchElement = entry;

                if (maxWeight > 300) {
                    break;
                }
            }
        }
//        Log.d(TAG, bestMatchElement.toString());

        Collection<Element> metas = getMetas(doc);
        String ogImage = null;
        for (Element entry : metas) {
            if (entry.hasAttr("property") && "og:image".equals(entry.attr("property"))) {
                ogImage = entry.attr("content");
                break;
            }
        }

        if (bestMatchElement != null) {
            String ret = bestMatchElement.toString();
            if (ogImage != null && !ret.contains(ogImage)) {
                ret = "<img src=\""+ogImage+"\"><br>\n"+ret;
            }
            return ret;
        }

        return null;
    }

    /**
     * Weights current element. By matching it with positive candidates and
     * weighting child nodes. Since it's impossible to predict which exactly
     * names, ids or class names will be used in HTML, major role is played by
     * child nodes
     *
     * @param e                Element to weight, along with child nodes
     */
    private static int getWeight(Element e) {
        int weight = calcWeight(e);
        weight += (int) Math.round(e.ownText().length() / 100.0 * 10);
        weight += weightChildNodes(e);
        return weight;
    }

    /**
     * Weights a child nodes of given Element. During tests some difficulties
     * were met. For instance, not every single document has nested paragraph
     * tags inside of the major article tag. Sometimes people are adding one
     * more nesting level. So, we're adding 4 points for every 100 symbols
     * contained in tag nested inside of the current weighted element, but only
     * 3 points for every element that's nested 2 levels deep. This way we give
     * more chances to extract the element that has less nested levels,
     * increasing probability of the correct extraction.
     *
     * @param rootEl           Element, who's child nodes will be weighted
     */
    private static int weightChildNodes(Element rootEl) {
        int weight = 0;
        Element caption = null;
        List<Element> pEls = new ArrayList<>(5);
        for (Element child : rootEl.children()) {
            String text = child.text();
            int textLength = text.length();
            if (textLength < 20) {
                continue;
            }

            String ownText = child.ownText();
            int ownTextLength = ownText.length();
            if (ownTextLength > 200) {
                weight += Math.max(50, ownTextLength / 10);
            }

            if (child.tagName().equals("h1") || child.tagName().equals("h2")) {
                weight += 30;
            } else if (child.tagName().equals("div") || child.tagName().equals("p")) {
                weight += calcWeightForChild(ownText);
                if (child.tagName().equals("p") && textLength > 50)
                    pEls.add(child);

                if (child.className().toLowerCase().equals("caption"))
                    caption = child;
            }
        }

        // use caption and image
        if (caption != null)
            weight += 30;

        if (pEls.size() >= 2) {
            for (Element subEl : rootEl.children()) {
                if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
                    weight += 20;
                    // headerEls.add(subEl);
                }
            }
        }
        return weight;
    }

    private static int calcWeightForChild(String text) {
        return text.length() / 25;
//		return Math.min(100, text.length() / ((child.getAllElements().size()+1)*5));
    }

    private static int calcWeight(Element e) {
        int weight = 0;
        if (POSITIVE.matcher(e.className()).find())
            weight += 35;

        if (POSITIVE.matcher(e.id()).find())
            weight += 40;

        if (ITSJOOMLA.matcher(e.attributes().toString()).find())
            weight += 200;

        if (UNLIKELY.matcher(e.className()).find())
            weight -= 20;

        if (UNLIKELY.matcher(e.id()).find())
            weight -= 20;

        if (NEGATIVE.matcher(e.className()).find())
            weight -= 50;

        if (NEGATIVE.matcher(e.id()).find())
            weight -= 50;

        String style = e.attr("style");
        if (style != null && !style.isEmpty() && NEGATIVE_STYLE.matcher(style).find())
            weight -= 50;
        return weight;
    }

    /**
     * Prepares document. Currently only stipping unlikely candidates, since
     * from time to time they're getting more score than good ones especially in
     * cases when major text is short.
     *
     * @param doc document to prepare. Passed as reference, and changed inside
     *            of function
     */
    private static void prepareDocument(Document doc) {
        // stripUnlikelyCandidates(doc);
        removeNav(doc);
        removeSelectsAndOptions(doc);
        removeScriptsAndStyles(doc);
        removeShares(doc);
        removeAds(doc);
        removeAuthor(doc);
        removeTitle(doc);
        removeMisc(doc);
    }

    /**
     * Removes unlikely candidates from HTML. Currently takes id and class name
     * and matches them against list of patterns
     *
     * @param doc document to strip unlikely candidates from
     */
//    protected void stripUnlikelyCandidates(Document doc) {
//        for (Element child : doc.select("body").select("*")) {
//            String className = child.className().toLowerCase();
//            String id = child.id().toLowerCase();
//
//            if (NEGATIVE.matcher(className).find()
//                    || NEGATIVE.matcher(id).find()) {
//                child.remove();
//            }
//        }
//    }
    private static void removeNav(Document doc) {
        Elements nav = doc.getElementsByTag("nav");
        for (Element item : nav) {
            item.remove();
        }
    }

    private static void removeScriptsAndStyles(Document doc) {
        Elements scripts = doc.getElementsByTag("script");
        for (Element item : scripts) {
            item.remove();
        }

        Elements noscripts = doc.getElementsByTag("noscript");
        for (Element item : noscripts) {
            item.remove();
        }

        Elements styles = doc.getElementsByTag("style");
        for (Element style : styles) {
            style.remove();
        }

    }

    private static void removeSelectsAndOptions(Document doc) {
        Elements scripts = doc.getElementsByTag("select");
        for (Element item : scripts) {
            item.remove();
        }

        Elements noscripts = doc.getElementsByTag("option");
        for (Element item : noscripts) {
            item.remove();
        }

    }

    private static void removeShares(Document doc) {
        Elements shares = doc.select(
                "div[class~=(shar(e|ing)|facebook|twitter|google.*plus|whatsapp|pinterest|instagram|youtube)]");
        for (Element item : shares) {
            item.remove();
        }

        Elements singPromosFbLikes = doc.select(
                "iframe[src~=facebook.*like]");
        for (Element item : singPromosFbLikes) {
            item.remove();
        }

        Elements singPromosFbShares = doc.select(
                "p[id~=shareOnFacebook]");
        for (Element item : singPromosFbShares) {
            item.remove();
        }
    }

    private static void removeAds(Document doc) {
        Elements ads = doc.select("div[class~=thrv_wrapper]");
        for (Element item : ads) {
            item.remove();
        }

        ads = doc.select("aside");
        for (Element item : ads) {
            item.remove();
        }

        ads = doc.select("div[class~=social-ring-button]");
        for (Element item : ads) {
            item.remove();
        }
    }

    private static void removeAuthor(Document doc) {
        Elements authors = doc.select("div[class~=author]");
        for (Element item : authors) {
            item.remove();
        }
    }

    private static void removeTitle(Document doc) {
        Elements title = doc.select("div[class~=([^a-z]title$|^title$)]");
        for (Element item : title) {
            item.remove();
        }
    }

    private static void removeMisc(Document doc) {
        // Seedly specific
        Elements misc = doc.select("a:contains(back to main blog)");
        for (Element item : misc) {
            item.remove();
        }

        misc = doc.select("div[class~=hatom-extra]");
        for (Element item : misc) {
            item.remove();
        }

        //image
        misc = doc.select("meta[property~=og:image]");
        for (Element item : misc) {
            item.remove();
        }

        //forms
        misc = doc.select("form");
        for (Element item : misc) {
            item.remove();
        }

        //footer
        misc = doc.select("footer");
        for (Element item : misc) {
            item.remove();
        }

        //related
        misc = doc.select("div[class~=.*related.*]");
        for (Element item : misc) {
            item.remove();
        }

        //copyright
        misc = doc.select("p[class~=copyright]");
        for (Element item : misc) {
            item.remove();
        }

        //comments
        misc = doc.select("div[class~=.*comment.*]");
        for (Element item : misc) {
            item.remove();
        }

        //seedly questions
        misc = doc.select("a[href~=.*seedly.sg/questions.*]");
        for (Element item : misc) {
            item.remove();
        }

        //peatix
        misc = doc.select("iframe[src~=peatix]");
        for (Element item : misc) {
            item.remove();
        }
    }

    /**
     * @return a set of all meta nodes
     */
    private static Collection<Element> getMetas(Document doc) {
        Collection<Element> nodes = new HashSet<>(64);
        nodes.addAll(doc.select("head").select("meta"));
        return nodes;
    }

    /**
     * @return a set of all important nodes
     */
    private static Collection<Element> getNodes(Document doc) {
        Collection<Element> nodes = new HashSet<>(64);
        for (Element el : doc.select("body").select("*")) {
            if (NODES.matcher(el.tagName()).matches()) {
                nodes.add(el);
            }
        }
        return nodes;
    }
}
