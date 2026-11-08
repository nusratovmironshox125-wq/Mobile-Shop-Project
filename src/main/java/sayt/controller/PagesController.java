package sayt.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sayt.exception.Problem;
import sayt.service.CatalogService;
import sayt.service.CategoryService;

@Controller
class PagesController {
    private final CatalogService catalog;
    private final CategoryService categories;
    private final String origin;

    PagesController(CatalogService catalog, CategoryService categories, @Value("${shop.origin}") String origin) {
        var uri = URI.create(origin);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null || !uri.getPath().matches("/?")
            || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid SHOP_ORIGIN");
        this.origin = origin.replaceAll("/$", "");
        this.catalog = catalog;
        this.categories = categories;
    }

    @GetMapping({"/","/discounts","/favorites","/contacts","/privacy","/admin","/admin/{*path}"})
    String app() { return "forward:/index.html"; }

    @GetMapping("/catalog/{slug}")
    String category(@PathVariable String slug, HttpServletResponse response) {
        if (categories.categories(false).stream().noneMatch(category -> category.slug().equals(slug))) {
            response.setStatus(404);
        }
        return app();
    }

    @GetMapping("/products/{slug}")
    String product(@PathVariable String slug, HttpServletResponse response) {
        try {
            catalog.detail(slug);
        } catch (Problem error) {
            if (error.status.value() != 404) throw error;
            response.setStatus(404);
        }
        return app();
    }

    @GetMapping(value="/robots.txt", produces=MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody String robots() {
        return "User-agent: *\nDisallow: /admin\nDisallow: /favorites\nDisallow: /api\nSitemap: "
            + origin + "/sitemap.xml\n";
    }

    @GetMapping(value="/sitemap.xml", produces=MediaType.APPLICATION_XML_VALUE)
    @ResponseBody String sitemap() {
        var urls = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (String path : new String[]{"/","/contacts","/discounts","/privacy"}) add(urls, path);
        for (var category : categories.categories(false)) add(urls, "/catalog/" + category.slug());
        for (String slug : catalog.publicSlugs()) add(urls, "/products/" + slug);
        return urls.append("</urlset>").toString();
    }

    private void add(StringBuilder urls, String path) {
        urls.append("<url><loc>").append(origin).append(path).append("</loc></url>");
    }
}
