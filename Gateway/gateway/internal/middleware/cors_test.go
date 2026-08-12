package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestCORSWhitelist(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(CORS([]string{"http://localhost:5173", "https://www.example.com"}, true))
	r.GET("/api/x", func(c *gin.Context) { c.Status(http.StatusOK) })

	t.Run("allowed origin", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/x", nil)
		req.Header.Set("Origin", "http://localhost:5173")
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("status=%d", w.Code)
		}
		if got := w.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:5173" {
			t.Fatalf("Allow-Origin=%q", got)
		}
		if w.Header().Get("Access-Control-Allow-Credentials") != "true" {
			t.Fatal("expected credentials")
		}
	})

	t.Run("evil origin no allow header", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/x", nil)
		req.Header.Set("Origin", "https://evil.com")
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("status=%d", w.Code)
		}
		if got := w.Header().Get("Access-Control-Allow-Origin"); got != "" {
			t.Fatalf("unexpected Allow-Origin=%q", got)
		}
	})

	t.Run("preflight forbidden for unknown", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodOptions, "/api/x", nil)
		req.Header.Set("Origin", "https://evil.com")
		r.ServeHTTP(w, req)
		if w.Code != http.StatusForbidden {
			t.Fatalf("status=%d want 403", w.Code)
		}
	})

	t.Run("preflight ok for allowed", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodOptions, "/api/x", nil)
		req.Header.Set("Origin", "https://www.example.com")
		r.ServeHTTP(w, req)
		if w.Code != http.StatusNoContent {
			t.Fatalf("status=%d want 204", w.Code)
		}
		if got := w.Header().Get("Access-Control-Allow-Origin"); got != "https://www.example.com" {
			t.Fatalf("Allow-Origin=%q", got)
		}
	})
}
