package mediaservice.media;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** /files/** 必须带未过期的 exp + sig，否则 403。 */
public class SignedFileFilter extends OncePerRequestFilter {

    private final MediaUrlSigner signer;

    public SignedFileFilter(MediaUrlSigner signer) {
        this.signer = signer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String expStr = request.getParameter("exp");
        String sig = request.getParameter("sig");
        if (expStr == null || sig == null) {
            forbid(response, "missing signature");
            return;
        }
        long exp;
        try {
            exp = Long.parseLong(expStr);
        } catch (NumberFormatException e) {
            forbid(response, "bad exp");
            return;
        }
        if (!signer.verify(path, exp, sig)) {
            forbid(response, "invalid or expired signature");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void forbid(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":40301,\"message\":\"" + msg + "\"}");
    }
}
