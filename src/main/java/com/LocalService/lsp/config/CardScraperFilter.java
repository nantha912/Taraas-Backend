package com.LocalService.lsp.config;

import com.LocalService.lsp.model.Provider;
import com.LocalService.lsp.repository.ProviderRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

@Component
@Order(1)
public class CardScraperFilter extends OncePerRequestFilter {

    private final ProviderRepository providerRepository;

    public CardScraperFilter(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // 🟢 DEFENSE: Restrict strictly to base profile views. Ignore API traffic, assets, and management routes
        if (path.contains("/api/") || path.contains(".") || path.contains("/offers") || path.contains("Onboard")) {
            return true;
        }
        return !path.contains("/provider/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String userAgent = request.getHeader("User-Agent");
        String path = request.getRequestURI();

        if (userAgent != null && isSocialScraper(userAgent)) {
            String[] pathParts = path.split("/provider/");
            if (pathParts.length > 1) {
                String segment = pathParts[1];
                // Handle trailing slashes or sub-paths safely
                String providerId = segment.contains("/") ? segment.split("/")[0] : segment;
                providerId = providerId.trim();

                // Stop empty parsing boundaries from executing queries
                if (providerId.isBlank()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Mock execution bypass rule for testing environments
                if ("test-id-123".equals(providerId)) {
                    serveMockScraperResponse(request, response, "Test Provider");
                    return;
                }

                Optional<Provider> providerOpt = providerRepository.findById(providerId);
                if (providerOpt.isPresent()) {
                    serveScraperHtml(request, response, providerOpt.get());
                    return; 
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void serveScraperHtml(HttpServletRequest request, HttpServletResponse response, Provider provider) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        
        String cardUrl = provider.getGeneratedCardImageUrl();
        // 🟢 DEFENSE: Auto-prepend host domain if the database record holds a relative path string chunk
        if (cardUrl == null || cardUrl.isBlank()) {
            cardUrl = "https://taraas.com/logo_icon/android-chrome-512x512.png";
        } else if (!cardUrl.startsWith("http")) {
            cardUrl = "https://taraas.com" + cardUrl;
        }
        
        generateHtmlPayload(out, provider.getName(), cardUrl, request.getRequestURL().toString());
    }

    private void serveMockScraperResponse(HttpServletRequest request, HttpServletResponse response, String mockName) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        generateHtmlPayload(out, mockName, "https://taraas.com/logo_icon/android-chrome-512x512.png", request.getRequestURL().toString());
    }

    private void generateHtmlPayload(PrintWriter out, String name, String cardUrl, String requestUrl) {
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head>");
        out.println("  <meta charset='UTF-8'>");
        out.println("  <title>" + name + " | Local Pro on Taraas</title>");
        out.println("  <meta property='og:title' content='🌟 Verified Expert: " + name + "' />");
        out.println("  <meta property='og:description' content='Bringing top-quality work straight to your doorstep. Check out my official portfolio gallery and verified reviews on Taraas!' />");
        out.println("  <meta property='og:image' content='" + cardUrl + "' />");
        out.println("  <meta property='og:image:type' content='image/png' />");
        out.println("  <meta property='og:image:width' content='1200' />");
        out.println("  <meta property='og:image:height' content='630' />");
        out.println("  <meta property='og:type' content='profile' />");
        out.println("  <meta property='og:url' content='" + requestUrl + "' />");
        out.println("  <meta name='twitter:card' content='summary_large_image' />");
        out.println("</head>");
        out.println("<body>");
        out.println("  <h1>" + name + " - Professional Service Profile</h1>");
        out.println("</body>");
        out.println("</html>");
        out.flush();
    }

    private boolean isSocialScraper(String userAgent) {
        String ua = userAgent.toLowerCase();
        return ua.contains("whatsapp") || 
               ua.contains("facebookexternalhit") || 
               ua.contains("twitterbot") || 
               ua.contains("linkedinbot") || 
               ua.contains("telegrambot") || 
               ua.contains("instagram") ||
               ua.contains("slackbot");
    }
}